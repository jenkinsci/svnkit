package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.util.PathUtil;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 30.06.2005
 * Time: 21:50:54
 * To change this template use File | Settings | File Templates.
 */
public class SVNMover extends SVNWCClient {

    public SVNMover(ISVNOptions options) {
        super(options, null);
    }

    public void doMove(File src, File dst) throws SVNException {
        if (dst.exists()) {
            throw new SVNException("svn: Cannot move '" + src + "' over existing file '" + dst);
        } else if (!src.exists()) {
            throw new SVNException("svn: Cannot move '" + src + "'; file does not exist");
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
            doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            doAdd(dst, false, false, false, true);
        } else {
            // wc:wc.

            // 1. collect information on src and dst entries.
            SVNWCAccess srcAccess = SVNWCAccess.create(src);
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("", false);
            
            boolean sameWC = dstParentEntry.getUUID() != null &&
                dstParentEntry.getUUID().equals(srcEntry.getUUID());

            if (sameWC && dstEntry != null &&
                    (dstEntry.isScheduledForDeletion() || dstEntry.getKind() != srcEntry.getKind())) {
                // attempt replace.
                SVNFileUtil.copy(src, dst, false, false);
                try {
                    doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // will be thrown on obstruction.
                }
                doDelete(src, true, false);
                return;
            }

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()){
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
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
                } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
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
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
                } 
                
                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();
                
                dstURL = PathUtil.append(dstURL, PathUtil.encode(dst.getName()));
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
                } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                    // versioned (deleted, replaced, or normal).
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
                        
                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL, null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // unversioned entry (copied or added)
                    dstAccess.getAnchor().getEntries().deleteEntry(dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst);
                    SVNFileUtil.copy(src, dst, false, false);
                    doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src.
            try {
                doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
        }
    }

    // move that considered as move undo.
    public void undoMove(File src, File dst) throws SVNException {
        // dst could exists, if it is deleted directory.
        if (!src.exists()) {
            throw new SVNException("svn: Cannot undo move of '" + dst + "'; file '"  + src + "' does not exist");
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
            doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            // dst should probably be deleted, in this case - revert it
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                doRevert(dst, true);
            } else {
                doAdd(dst, false, false, false, true);
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
                doRevert(dst, true);
                doDelete(src, true, false);
                return;
            }

            boolean sameWC = dstParentEntry.getUUID() != null &&
                dstParentEntry.getUUID().equals(srcEntry.getUUID());

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // obstruction assertion.
            if (dstEntry != null && dstEntry.getKind() != srcEntry.getKind()) {
                // ops have no sence->target is obstructed, just export src to dst and delete src.
                doDelete(src, true, false);
                return;
            }

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()){
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
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
                } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
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
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                dstURL = PathUtil.append(dstURL, PathUtil.encode(dst.getName()));
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
                } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
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

                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL, null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // replay
                    dstAccess.getAnchor().getEntries().deleteEntry(dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst);
                    SVNFileUtil.copy(src, dst, false, false);
                    doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src.
            try {
                doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
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
