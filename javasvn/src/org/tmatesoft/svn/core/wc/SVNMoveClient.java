/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNMoveClient extends SVNBasicClient {

    private SVNWCClient myWCClient;

    public SVNMoveClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
        myWCClient = new SVNWCClient(authManager, options);
    }

    protected SVNMoveClient(ISVNRepositoryFactory factory, ISVNOptions options) {
        super(factory, options);
        myWCClient = new SVNWCClient(factory, options);
    }

    public void doMove(File src, File dst) throws SVNException {
        if (dst.exists()) {
            throw new SVNException("svn: Cannot move '" + src
                    + "' over existing file '" + dst);
        } else if (!src.exists()) {
            throw new SVNException("svn: Cannot move '" + src
                    + "'; file does not exist");
        }
        // src condidered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            myWCClient.doAdd(dst, false, false, false, true);
        } else {
            // wc:wc.

            // 1. collect information on src and dst entries.
            SVNWCAccess srcAccess = SVNWCAccess.create(src);
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();

            SVNProperties srcProps = srcAccess.getAnchor().getProperties(
                    srcAccess.getTargetName(), false);
            SVNProperties dstProps = dstAccess.getAnchor().getProperties(
                    dstAccess.getTargetName(), false);

            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries()
                    .getEntry("", false);

            File srcWCRoot = SVNWCUtil.getWorkingCopyRoot(src, true);
            File dstWCRoot = SVNWCUtil.getWorkingCopyRoot(dst, true);
            boolean sameWC = srcWCRoot != null && srcWCRoot.equals(dstWCRoot);

            if (sameWC
                    && dstEntry != null
                    && (dstEntry.isScheduledForDeletion() || dstEntry.getKind() != srcEntry
                            .getKind())) {
                // attempt replace.
                SVNFileUtil.copy(src, dst, false, false);
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // will be thrown on obstruction.
                }
                myWCClient.doDelete(src, true, false);
                return;
            }

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();
                // copy props!
                srcProps.copyTo(dstProps);

                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                } else {
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    if (!dstEntry.isScheduledForReplacement()) {
                        dstEntry.setRevision(0);
                    }
                }
                dstAccess.getAnchor().getEntries().save(true);

            } else if (srcEntry.isDirectory()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                srcEntry = srcAccess.getTarget().getEntries().getEntry("",
                        false);

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                dstURL = SVNPathUtil
                        .append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    srcProps.copyTo(dstProps);
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstDir.updateURL(dstURL, true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    // versioned (deleted, replaced, or normal).
                    srcProps.copyTo(dstProps);
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL, CF-URL and CF-REV in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstEntry = dstDir.getEntries().getEntry("", false);
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setURL(dstURL);
                        dstEntry.setCopyFromURL(srcURL);

                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL,
                                null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // unversioned entry (copied or added)
                    dstAccess.getAnchor().getEntries().deleteEntry(
                            dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst, this);
                    SVNFileUtil.copy(src, dst, false, false);
                    myWCClient.doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src (if it is not the same as dst :))
            try {
                myWCClient.doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
        }
    }

    // move that considered as move undo.
    public void undoMove(File src, File dst) throws SVNException {
        // dst could exists, if it is deleted directory.
        if (!src.exists()) {
            throw new SVNException("svn: Cannot undo move of '" + dst
                    + "'; file '" + src + "' does not exist");
        }
        // src condidered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            // dst should probably be deleted, in this case - revert it
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                myWCClient.doRevert(dst, true);
            } else {
                myWCClient.doAdd(dst, false, false, false, true);
            }
        } else {
            // wc:wc.
            SVNWCAccess srcAccess = SVNWCAccess.create(src);
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("", false);

            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                // clear undo.
                myWCClient.doRevert(dst, true);
                myWCClient.doDelete(src, true, false);
                return;
            }

            boolean sameWC = dstParentEntry.getUUID() != null
                    && dstParentEntry.getUUID().equals(srcEntry.getUUID());

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // obstruction assertion.
            if (dstEntry != null && dstEntry.getKind() != srcEntry.getKind()) {
                // ops have no sence->target is obstructed, just export src to
                // dst and delete src.
                myWCClient.doDelete(src, true, false);
                return;
            }

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                } else {
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    if (!dstEntry.isScheduledForReplacement()) {
                        dstEntry.setRevision(0);
                    }
                }
                dstAccess.getAnchor().getEntries().save(true);

            } else if (srcEntry.isDirectory()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                dstURL = SVNPathUtil
                        .append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstDir.updateURL(dstURL, true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL, CF-URL and CF-REV in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstEntry = dstDir.getEntries().getEntry("", false);
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setURL(dstURL);
                        dstEntry.setCopyFromURL(srcURL);

                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL,
                                null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // replay
                    dstAccess.getAnchor().getEntries().deleteEntry(
                            dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst, this);
                    SVNFileUtil.copy(src, dst, false, false);
                    myWCClient.doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src.
            try {
                myWCClient.doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
        }
    }
    
    public void doVirtualCopy(File src, File dst, boolean move) throws SVNException {
        SVNFileType srcType  = SVNFileType.getType(src);
        SVNFileType dstType  = SVNFileType.getType(dst);
        
        String opName = move ? "move" : "copy";
        if (move && srcType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: Cannot perform 'virtual' " + opName + ": '" + src + "' still exists");
        }
        if (dstType == SVNFileType.NONE) {
            SVNErrorManager.error("svn: Cannot perform 'virtual' " + opName + ": '" + dst + "' does not exist");
        }
        if (dstType == SVNFileType.DIRECTORY) {
            SVNErrorManager.error("svn: Cannot perform 'virtual' " + opName + ": '" + dst + "' is a directory");
        }
        if (!move && srcType == SVNFileType.DIRECTORY) {
            SVNErrorManager.error("svn: Cannot perform 'virtual' " + opName + ": '" + src + "' is a directory");
        }
        
        SVNWCAccess srcAccess = createWCAccess(src);
        String cfURL = null;
        boolean added = false;
        long cfRevision = -1;
        try {
            srcAccess.open(true, false);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorManager.error("svn: '" + src + "' is not under version control");
            }
            if (srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                SVNErrorManager.error("svn: '" + src + "' is part of the copied tree");
            }
            cfURL = srcEntry.isCopied() ? srcEntry.getCopyFromURL() : srcEntry.getURL();
            cfRevision = srcEntry.isCopied() ? srcEntry.getCopyFromRevision() : srcEntry.getRevision();
            added = srcEntry.isScheduledForAddition() && !srcEntry.isCopied();
        } finally {
            srcAccess.close(true);
        }
        if (!move) {
            myWCClient.doDelete(src, true, false);
        }
        if (added) {
            myWCClient.doAdd(dst, true, false, false, false);            
            return;
        }

        SVNWCAccess dstAccess = createWCAccess(dst);
        try {
            dstAccess.open(true, false);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null) {
                SVNErrorManager.error("svn: '" + dst + "' is already under version control");                
            }
            dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
            dstEntry.setCopyFromURL(cfURL);
            dstEntry.setCopyFromRevision(cfRevision);
            dstEntry.setCopied(true);
            dstEntry.setKind(SVNNodeKind.FILE);
            dstEntry.scheduleForAddition();
            
            dstAccess.getAnchor().getEntries().save(true);
        } finally {
            dstAccess.close(true);
        }
        
    }

    private static boolean isVersionedFile(File file) {
        SVNWCAccess wcAccess;
        try {
            wcAccess = SVNWCAccess.create(file);
        } catch (SVNException e) {
            return false;
        }
        try {
            return wcAccess != null && wcAccess.getTargetEntry() != null;
        } catch (SVNException e) {
            return false;
        }
     }
}
