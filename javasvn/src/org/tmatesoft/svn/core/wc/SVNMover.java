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
public class SVNMover {

    public static void doMove(File src, File dst) throws SVNException {
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
            SVNWCClient client = new SVNWCClient();
            client.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            SVNWCClient client = new SVNWCClient();
            client.doAdd(dst, false, false, false, true);            
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
            
            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // obstruction assertion.
            if (dstEntry != null && dstEntry.getKind() != srcEntry.getKind()) {
                // ops have no sence->target is obstructed, just export src to dst and delete src.
                SVNWCClient client = new SVNWCClient();
                client.doDelete(src, true, false);
                return;                        
            }

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    SVNWCClient client = new SVNWCClient();
                    client.doAdd(dst, false, false, false, true);            
                } catch (SVNException e) {}
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
                } else if (!srcEntry.isCopied()) {
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
                } else if (!srcEntry.isCopied()) {
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
                    SVNWCClient client = new SVNWCClient();
                    client.doAdd(dst, false, false, false, true);            
                }
            }

            // now delete src.
            try {
                SVNWCClient client = new SVNWCClient();
                client.doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }

            srcAccess.close(false);
            dstAccess.close(false);
        }
    }

    // move that considered as move undo.
    public static void undoMove(File src, File dst) throws SVNException {
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
            SVNWCClient client = new SVNWCClient();
            client.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            SVNWCClient client = new SVNWCClient();
            // dst should probably be deleted, in this case - revert it
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                client.doRevert(dst, true);
            } else {
                client.doAdd(dst, false, false, false, true);
            }
        } else {
            // wc:wc.

            SVNWCClient client = new SVNWCClient();
            client.doRevert(dst, true);
            client.doDelete(src, true, false);
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
    /*
    public void doSmartMove(File src, File dst) throws SVNException {
        // 1. check if both files are in wc, if not -> just move
        File srcRoot = SVNWCUtil.getWorkingCopyRoot(src, true);
        File dstRoot = SVNWCUtil.getWorkingCopyRoot(dst, true);
        if (srcRoot == null && dstRoot == null) {
            SVNFileUtil.rename(src, dst);
        } else if (srcRoot == null) {
            // from world to wc.
            SVNNodeKind dstKind = null;
            boolean isDstDeleted = false;
            SVNWCAccess wcAccess = createWCAccess(dst);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry != null) {
                dstKind = entry.getKind();
                isDstDeleted = entry.isScheduledForDeletion();
            }

            SVNWCClient wcClient = new SVNWCClient(getOptions(), null);
            SVNFileType srcType = SVNFileType.getType(src);
            // may dst's parent be unversioned? no, we found wc root for missing file.
            if (dstKind != null && !SVNFileType.equals(srcType, dstKind)) {
                // 1) dst kind differs from src kind, just put files there, do not add. (already done).
                SVNFileUtil.rename(src, dst);
            } else if (isDstDeleted) {
                // UNDO case. for now just revert, later add 'undo' parameter.
                wcClient.doRevert(dst, true);
                SVNFileUtil.deleteAll(src);
                // 2) dst is deleted -> revert non-missing files, add unversioned.
                // it will not happen for dirs, but for files just make unschedule.
            } else if (entry != null) {
                // 3) dst exists (missinng): delete and replace.
                wcClient.doDelete(dst, true, false);
                SVNFileUtil.rename(src, dst);
                wcClient.doAdd(dst, false, false, true, true);
            } else {
                SVNFileUtil.rename(src, dst);
                wcClient.doAdd(dst, false, false, true, true);

            }
        } else if (dstRoot == null || !srcRoot.equals(dstRoot)) {
            // from wc to world or from wc1 to wc2.
            SVNWCClient wcClient = new SVNWCClient(getOptions(), null);

            SVNWCAccess wcAccess = createWCAccess(src);
            // src is not versioned if there is no entry in anchor for it (named or "" if it is anchor).
            if (wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false) == null) {
                SVNFileUtil.rename(src, dst);
            } else {
                SVNUpdateClient upClient = new SVNUpdateClient(getOptions(), null);
                upClient.doExport(src, dst, SVNRevision.UNDEFINED, SVNRevision.WORKING, null, true, true);
                wcClient.doDelete(src, true, false);
            }
            if (dstRoot != null) {
                wcClient.doAdd(dst, false, false, false, true);
            }
        } else {
            // all inside the same wc.
            SVNWCAccess srcAccess = createWCAccess(src);
            SVNWCAccess dstAccess = createWCAccess(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            SVNWCClient wcClient = new SVNWCClient(getOptions(), null);
            if (srcEntry == null) {
                // unversioned to versioned.
                SVNFileUtil.rename(src, dst);
                wcClient.doAdd(dst, false, false, false, true);
            } else if (dstEntry != null) {
                // UNDO case!
                // this and only this may be move undo!
                if (dstEntry.isScheduledForDeletion()) {
                    // do revert of deleted item. only do this when undoing,
                    wcClient.doRevert(dst, true);
                    wcClient.doDelete(src, true, false);
                } else {
                    wcClient.doDelete(dst, true, false);
                    SVNUpdateClient upClient = new SVNUpdateClient(getOptions(), null);
                    upClient.doExport(src, dst, SVNRevision.UNDEFINED, SVNRevision.WORKING, null, true, true);
                    wcClient.doDelete(src, true, false);
                    // add dst only if kind is the same.
                    try {
                        wcClient.doAdd(dst, false, false, false, true);
                    } catch (SVNException e) {
                        // will be thrown in case of obstruction.
                    }
                }
            } else {
                // what if src is replaced?
                // versioned move to unversione location
                SVNCopyClient cpClient = new SVNCopyClient(getOptions(), null);
                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {

                } else if (srcEntry.isScheduledForAddition() || srcEntry.isScheduledForReplacement()) {

                } else {
                    cpClient.doCopy(src, null, SVNRevision.WORKING,
                        dst, null, SVNRevision.WORKING, true, true, null);

                }
                wcClient.doDelete(src, true, false);
            }
        }
    }

    // for dirs:
    // if move, and src is missing, and dst looks like copied from src (url matches) -> copy dst back to src.
    // if move -> schedule src for deletion.
    // schedules 'dst path' for addition with history.

    // for files:
    // if move, and src is missing, and dst looks like copied from src  -> just shcedule src for deletion.
    // schedules 'dst path' for addition with history.
    public void doVirtualCopy(File srcPath, File dstPath, boolean move) throws SVNException {

    }             */
}
