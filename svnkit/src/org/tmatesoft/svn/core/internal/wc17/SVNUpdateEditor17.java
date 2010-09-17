/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictedInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNUpdateEditor17 implements ISVNUpdateEditor {

    private SVNWCContext myWcContext;
    private File myAnchor;
    private String myTarget;
    private boolean myIsUnversionedObstructionsAllowed;
    private SVNURL mySwitchURL;
    private long myTargetRevision;
    private SVNDepth myRequestedDepth;
    private boolean myIsDepthSticky;
    private SVNDeltaProcessor myDeltaProcessor;
    private String[] myExtensionPatterns;
    private ISVNFileFetcher myFileFetcher;
    private File myTargetPath;
    private SVNURL myRootURL;
    private boolean myIsLockOnDemand;
    private SVNExternalsStore myExternalsStore;
    private File mySwitchRelpath;
    private String myTargetBasename;
    private boolean myIsRootOpened;
    private SVNDirectoryInfo myCurrentDirectory;
    private List<File> mySkippedTrees = new LinkedList<File>();
    private boolean myIsTargetDeleted;

    public static ISVNUpdateEditor createUpdateEditor(SVNWCContext wcContext, File anchorAbspath, String target, SVNURL reposRoot, SVNURL switchURL, SVNExternalsStore externalsStore,
            boolean allowUnversionedObstructions, boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, boolean updateLocksOnDemand) throws SVNException {
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }

        WCDbInfo info = wcContext.getDb().readInfo(anchorAbspath, InfoField.reposRootUrl, InfoField.reposUuid);
        assert (info.reposRootUrl != null && info.reposUuid != null);
        if (switchURL != null) {
            if (!SVNPathUtil.isAncestor(info.reposRootUrl.toDecodedString(), switchURL.toDecodedString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''", new Object[] {
                        switchURL.toDecodedString(), info.reposRootUrl.toDecodedString()
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        return new SVNUpdateEditor17(wcContext, anchorAbspath, target, info.reposRootUrl, switchURL, externalsStore, allowUnversionedObstructions, depthIsSticky, depth, preservedExts, fileFetcher,
                updateLocksOnDemand);
    }

    public SVNUpdateEditor17(SVNWCContext wcContext, File anchorAbspath, String target, SVNURL reposRootUrl, SVNURL switchURL, SVNExternalsStore externalsStore, boolean allowUnversionedObstructions,
            boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, boolean lockOnDemand) {

        myWcContext = wcContext;
        myAnchor = anchorAbspath;
        myTarget = target;
        myIsUnversionedObstructionsAllowed = allowUnversionedObstructions;
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myRequestedDepth = depth;
        myIsDepthSticky = depthIsSticky;
        myDeltaProcessor = new SVNDeltaProcessor();
        myExtensionPatterns = preservedExts;
        myFileFetcher = fileFetcher;
        myTargetPath = anchorAbspath;
        myRootURL = reposRootUrl;
        myIsLockOnDemand = lockOnDemand;
        myExternalsStore = externalsStore;

        if (myTarget != null) {
            myTargetPath = SVNFileUtil.createFilePath(myTargetPath, myTarget);
        }
        if ("".equals(myTarget)) {
            myTarget = null;
        }

    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    private void rememberSkippedTree(File localAbspath) {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        mySkippedTrees.add(localAbspath);
        return;
    }

    public void openRoot(long revision) throws SVNException {
        boolean already_conflicted;
        myIsRootOpened = true;
        myCurrentDirectory = createDirectoryInfo(null, null, false);

        SVNWCDbKind kind = myWcContext.getDb().readKind(myCurrentDirectory.getLocalAbspath(), true);
        if (kind == SVNWCDbKind.Dir) {
            try {
                already_conflicted = alreadyInATreeConflict(myCurrentDirectory.getLocalAbspath());
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_MISSING) {
                    already_conflicted = true;
                } else {
                    throw e;
                }
            }
        } else
            already_conflicted = false;

        if (already_conflicted) {
            myCurrentDirectory.setSkipThis(true);
            myCurrentDirectory.setSkipDescendants(true);
            myCurrentDirectory.setAlreadyNotified(true);
            myCurrentDirectory.getBumpInfo().setSkipped(true);
            doNotification(myTargetPath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }

        if (myTargetBasename == null) {

            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(myCurrentDirectory.getLocalAbspath(), BaseInfoField.status, BaseInfoField.depth);
            myCurrentDirectory.setAmbientDepth(baseInfo.depth);
            myCurrentDirectory.setWasIncomplete(baseInfo.status == SVNWCDbStatus.Incomplete);
            /* ### TODO: Skip if inside a conflicted tree. */
            myWcContext.getDb().opStartDirectoryUpdateTemp(myCurrentDirectory.getLocalAbspath(), myCurrentDirectory.getNewRelpath(), myTargetRevision);
        }

    }

    private void doNotification(File localAbspath, SVNNodeKind kind, SVNEventAction action) throws SVNException {
        if (myWcContext.getEventHandler() != null) {
            myWcContext.getEventHandler().handleEvent(new SVNEvent(localAbspath, kind, null, -1, null, null, null, null, action, null, null, null, null), 0);
        }
    }

    private boolean alreadyInATreeConflict(File localAbspath) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        File ancestorAbspath = localAbspath;
        boolean conflicted = false;
        while (true) {
            SVNWCDbStatus status;
            boolean isWcRoot, hasConflict;
            SVNConflictDescription conflict;
            try {
                WCDbInfo readInfo = myWcContext.getDb().readInfo(ancestorAbspath, InfoField.status, InfoField.conflicted);
                status = readInfo.status;
                hasConflict = readInfo.conflicted;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY
                        && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_UPGRADE_REQUIRED) {
                    throw e;
                }
                break;
            }
            if (status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.Absent || status == SVNWCDbStatus.Excluded)
                break;
            if (hasConflict) {
                conflict = myWcContext.getDb().opReadTreeConflict(ancestorAbspath);
                if (conflict != null) {
                    conflicted = true;
                    break;
                }
            }
            if (SVNFileUtil.getParentFile(ancestorAbspath) == null)
                break;
            isWcRoot = myWcContext.getDb().isWCRoot(ancestorAbspath);
            if (isWcRoot)
                break;
            ancestorAbspath = SVNFileUtil.getParentFile(ancestorAbspath);
        }
        return conflicted;
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String base = SVNFileUtil.getBasePath(SVNFileUtil.createFilePath(path));
        File localAbspath = SVNFileUtil.createFilePath(myCurrentDirectory.getLocalAbspath(), base);
        if (myCurrentDirectory.isSkipDescendants()) {
            if (!myCurrentDirectory.isSkipThis())
                rememberSkippedTree(localAbspath);
            return;
        }
        checkIfPathIsUnderRoot(path);
        File theirRelpath = SVNFileUtil.createFilePath(myCurrentDirectory.getNewRelpath(), base);
        doEntryDeletion(localAbspath, theirRelpath, myCurrentDirectory.isInDeletedAndTreeConflictedSubtree());
    }

    private void doEntryDeletion(File localAbspath, File theirRelpath, boolean inDeletedAndTreeConflictedSubtree) throws SVNException {
        ISVNWCDb db = myWcContext.getDb();
        boolean conflicted = db.readInfo(localAbspath, InfoField.conflicted).conflicted;
        if (conflicted)
            conflicted = isNodeAlreadyConflicted(localAbspath);
        if (conflicted) {
            rememberSkippedTree(localAbspath);
            /* ### TODO: Also print victim_path in the skip msg. */
            doNotification(localAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        boolean hidden = db.isNodeHidden(localAbspath);
        if (hidden) {
            db.removeBase(localAbspath);
            if (localAbspath.equals(myTargetPath))
                myIsTargetDeleted = true;
            return;
        }
        SVNTreeConflictDescription tree_conflict = null;
        if (!inDeletedAndTreeConflictedSubtree)
            tree_conflict = checkTreeConflict(localAbspath, SVNConflictAction.DELETE, SVNNodeKind.NONE, theirRelpath);
        if (tree_conflict != null) {
            db.opSetTreeConflict(tree_conflict.getPath(), tree_conflict);
            rememberSkippedTree(localAbspath);
            doNotification(localAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
            if (tree_conflict.getConflictReason() == SVNConflictReason.EDITED) {
                db.opMakeCopyTemp(localAbspath, false);
            } else if (tree_conflict.getConflictReason() == SVNConflictReason.DELETED) {
            } else if (tree_conflict.getConflictReason() == SVNConflictReason.REPLACED) {
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        File dirAbspath = SVNFileUtil.getParentFile(localAbspath);
        if (!localAbspath.equals(myTargetPath)) {
            SVNSkel workItem = myWcContext.wqBuildBaseRemove(localAbspath, false);
            myWcContext.wqAdd(dirAbspath, workItem);
        } else {
            SVNSkel workItem = myWcContext.wqBuildBaseRemove(localAbspath, true);
            myWcContext.wqAdd(dirAbspath, workItem);
            myIsTargetDeleted = true;
        }
        myWcContext.wqRun(dirAbspath);
        if (tree_conflict == null)
            doNotification(localAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.UPDATE_DELETE);
        return;
    }

    private boolean isNodeAlreadyConflicted(File localAbspath) throws SVNException {
        List<SVNConflictDescription> conflicts = myWcContext.getDb().readConflicts(localAbspath);
        for (SVNConflictDescription cd : conflicts) {
            if (cd.isTreeConflict()) {
                return true;
            } else if (cd.isTreeConflict() || cd.isTextConflict()) {
                ConflictedInfo info = myWcContext.getConflicted(localAbspath, true, true, true);
                return (info.textConflicted || info.propConflicted || info.treeConflicted);
            }
        }
        return false;
    }

    private SVNTreeConflictDescription checkTreeConflict(File localAbspath, SVNConflictAction action, SVNNodeKind theirNodeKind, File theirRelpath) throws SVNException {
        WCDbInfo readInfo = myWcContext.getDb().readInfo(localAbspath, InfoField.status, InfoField.kind, InfoField.haveBase);
        SVNWCDbStatus status = readInfo.status;
        SVNWCDbKind db_node_kind = readInfo.kind;
        boolean have_base = readInfo.haveBase;
        SVNConflictReason reason = null;
        boolean locally_replaced = false;
        boolean modified = false;
        boolean all_mods_are_deletes = false;
        switch (status) {
            case Added:
            case MovedHere:
            case Copied:
                if (have_base) {
                    SVNWCDbStatus base_status = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.status).status;
                    if (base_status != SVNWCDbStatus.NotPresent)
                        locally_replaced = true;
                }
                if (!locally_replaced) {
                    assert (action == SVNConflictAction.ADD);
                    reason = SVNConflictReason.ADDED;
                } else {
                    reason = SVNConflictReason.REPLACED;
                }
                break;

            case Deleted:
                reason = SVNConflictReason.DELETED;
                break;

            case Incomplete:
            case Normal:
                if (action == SVNConflictAction.EDIT)
                    return null;
                switch (db_node_kind) {
                    case File:
                    case Symlink:
                        all_mods_are_deletes = false;
                        modified = hasEntryLocalMods(localAbspath, db_node_kind);
                        break;

                    case Dir:
                        TreeLocalModsInfo info = hasTreeLocalMods(localAbspath);
                        modified = info.modified;
                        all_mods_are_deletes = info.allModsAreDeletes;
                        break;

                    default:
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL);
                        SVNErrorManager.error(err, SVNLogType.WC);
                        break;
                }

                if (modified) {
                    if (all_mods_are_deletes)
                        reason = SVNConflictReason.DELETED;
                    else
                        reason = SVNConflictReason.EDITED;
                }
                break;

            case Absent:
            case Excluded:
            case NotPresent:
                return null;

            case BaseDeleted:
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL);
                SVNErrorManager.error(err, SVNLogType.WC);
                break;

        }

        if (reason == null)
            return null;
        if (reason == SVNConflictReason.EDITED || reason == SVNConflictReason.DELETED || reason == SVNConflictReason.REPLACED)
            assert (action == SVNConflictAction.EDIT || action == SVNConflictAction.DELETE || action == SVNConflictAction.REPLACE);
        else if (reason == SVNConflictReason.ADDED)
            assert (action == SVNConflictAction.ADD);
        return new SVNTreeConflictDescription(localAbspath, theirNodeKind, action, reason, SVNOperation.UPDATE, null, null);
    }

    private boolean hasEntryLocalMods(File localAbspath, SVNWCDbKind kind) throws SVNException {
        boolean text_modified;
        if (kind == SVNWCDbKind.File || kind == SVNWCDbKind.Symlink) {
            text_modified = myWcContext.isTextModified(localAbspath, false, true);
        } else {
            text_modified = false;
        }
        boolean props_modified = myWcContext.isPropsModified(localAbspath);
        return (text_modified || props_modified);
    }

    private static class TreeLocalModsInfo {

        public boolean modified;
        public boolean allModsAreDeletes;
    }

    private TreeLocalModsInfo hasTreeLocalMods(File localAbspath) throws SVNException {
        final TreeLocalModsInfo modInfo = new TreeLocalModsInfo();
        ISVNWCNodeHandler nodeHandler = new ISVNWCNodeHandler() {

            public void nodeFound(File localAbspath) throws SVNException {
                WCDbInfo readInfo = myWcContext.getDb().readInfo(localAbspath, InfoField.status, InfoField.kind);
                SVNWCDbStatus status = readInfo.status;
                SVNWCDbKind kind = readInfo.kind;
                boolean modified = false;
                if (status != SVNWCDbStatus.Normal)
                    modified = true;
                else if (!modInfo.modified || modInfo.allModsAreDeletes)
                    modified = hasEntryLocalMods(localAbspath, kind);
                if (modified) {
                    modInfo.modified = true;
                    if (status != SVNWCDbStatus.Deleted)
                        modInfo.allModsAreDeletes = false;
                }
                return;
            }
        };
        myWcContext.nodeWalkChildren(localAbspath, nodeHandler, false, SVNDepth.INFINITY);
        return modInfo;
    }

    private void checkIfPathIsUnderRoot(String path) throws SVNException {
        if (SVNFileUtil.isWindows && path != null) {
            String testPath = path.replace(File.separatorChar, '/');
            int ind = -1;

            while (testPath.length() > 0 && (ind = testPath.indexOf("..")) != -1) {
                if (ind == 0 || testPath.charAt(ind - 1) == '/') {
                    int i;
                    for (i = ind + 2; i < testPath.length(); i++) {
                        if (testPath.charAt(i) == '.') {
                            continue;
                        } else if (testPath.charAt(i) == '/') {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Path ''{0}'' is not in the working copy", path);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        } else {
                            break;
                        }
                    }
                    if (i == testPath.length()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Path ''{0}'' is not in the working copy", path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    testPath = testPath.substring(i);
                } else {
                    testPath = testPath.substring(ind + 2);
                }
            }
        }
    }

    public void absentDir(String path) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void absentFile(String path) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void openDir(String path, long revision) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void closeDir() throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void openFile(String path, long revision) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void abortEdit() throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void textDeltaEnd(String path) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, File path, boolean added) throws SVNException {
        assert (path != null || parent == null);

        SVNDirectoryInfo d = new SVNDirectoryInfo();
        if (path != null) {
            d.setName(SVNFileUtil.getFileName(path));
            d.setLocalAbspath(SVNFileUtil.createFilePath(parent.getLocalAbspath(), d.getName()));
            d.setInDeletedAndTreeConflictedSubtree(parent.isInDeletedAndTreeConflictedSubtree());
        } else {
            d.setLocalAbspath(myAnchor);
        }

        if (mySwitchRelpath != null) {
            if (parent == null) {
                if (myTargetBasename == null || myTargetBasename.equals("")) {
                    d.setNewRelpath(this.mySwitchRelpath);
                } else {
                    d.setNewRelpath(myWcContext.getDb().scanBaseRepository(d.getLocalAbspath(), RepositoryInfoField.relPath).relPath);
                }
            } else {
                if (parent.getParentDir() == null && myTargetBasename.equals(d.getName()))
                    d.setNewRelpath(mySwitchRelpath);
                else
                    d.setNewRelpath(SVNFileUtil.createFilePath(parent.getNewRelpath(), d.getName()));
            }
        } else {
            if (added) {
                assert (parent != null);
                d.setNewRelpath(SVNFileUtil.createFilePath(parent.getNewRelpath(), d.getName()));
            } else {
                d.setNewRelpath(myWcContext.getDb().scanBaseRepository(d.getLocalAbspath(), RepositoryInfoField.relPath).relPath);
            }
        }

        SVNBumpDirInfo bdi = new SVNBumpDirInfo();
        bdi.setParent(parent != null ? parent.getBumpInfo() : null);
        bdi.setRefCount(1);
        bdi.setLocalAbspath(d.getLocalAbspath());
        bdi.setSkipped(false);

        if (parent != null)
            bdi.getParent().setRefCount(bdi.getParent().getRefCount() + 1);

        d.setParentDir(parent);
        d.setPropChanges(new SVNProperties());
        d.setObstructionFound(false);
        d.setAddExisted(false);
        d.setBumpInfo(bdi);
        d.setOldRevision(SVNWCContext.INVALID_REVNUM);
        d.setAddingDir(added);

        d.setAmbientDepth(SVNDepth.UNKNOWN);
        d.setWasIncomplete(false);

        myWcContext.registerCleanupHandler(d);

        return d;
    }

    private class SVNBumpDirInfo {

        private SVNBumpDirInfo parent;
        private int refCount;
        private File localAbspath;
        private boolean skipped;

        public SVNBumpDirInfo getParent() {
            return parent;
        }

        public void setParent(SVNBumpDirInfo parent) {
            this.parent = parent;
        }

        public int getRefCount() {
            return refCount;
        }

        public void setRefCount(int refCount) {
            this.refCount = refCount;
        }

        public File getLocalAbspath() {
            return localAbspath;
        }

        public void setLocalAbspath(File localAbspath) {
            this.localAbspath = localAbspath;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public void setSkipped(boolean skipped) {
            this.skipped = skipped;
        }
    };

    private class SVNEntryInfo {

        private String name;
        private File localAbspath;
        private File newRelpath;
        private long oldRevision;
        private SVNDirectoryInfo parentDir;
        private boolean skipThis;
        private boolean alreadyNotified;
        private boolean obstructionFound;
        private boolean addExisted;
        private SVNProperties propChanges;
        private SVNBumpDirInfo bumpInfo;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public File getLocalAbspath() {
            return localAbspath;
        }

        public void setLocalAbspath(File localAbspath) {
            this.localAbspath = localAbspath;
        }

        public File getNewRelpath() {
            return newRelpath;
        }

        public void setNewRelpath(File newRelpath) {
            this.newRelpath = newRelpath;
        }

        public long getOldRevision() {
            return oldRevision;
        }

        public void setOldRevision(long oldRevision) {
            this.oldRevision = oldRevision;
        }

        public SVNDirectoryInfo getParentDir() {
            return parentDir;
        }

        public void setParentDir(SVNDirectoryInfo parentDir) {
            this.parentDir = parentDir;
        }

        public boolean isSkipThis() {
            return skipThis;
        }

        public void setSkipThis(boolean skipThis) {
            this.skipThis = skipThis;
        }

        public boolean isAlreadyNotified() {
            return alreadyNotified;
        }

        public void setAlreadyNotified(boolean alreadyNotified) {
            this.alreadyNotified = alreadyNotified;
        }

        public boolean isObstructionFound() {
            return obstructionFound;
        }

        public void setObstructionFound(boolean obstructionFound) {
            this.obstructionFound = obstructionFound;
        }

        public boolean isAddExisted() {
            return addExisted;
        }

        public void setAddExisted(boolean addExisted) {
            this.addExisted = addExisted;
        }

        public SVNProperties getPropChanges() {
            return propChanges;
        }

        public void setPropChanges(SVNProperties propChanges) {
            this.propChanges = propChanges;
        }

        public SVNBumpDirInfo getBumpInfo() {
            return bumpInfo;
        }

        public void setBumpInfo(SVNBumpDirInfo bumpInfo) {
            this.bumpInfo = bumpInfo;
        }

    }

    private class SVNDirectoryInfo extends SVNEntryInfo implements SVNWCContext.CleanupHandler {

        private boolean skipDescendants;
        private boolean addingDir;
        private boolean inDeletedAndTreeConflictedSubtree;
        private SVNDepth ambientDepth;
        private boolean wasIncomplete;

        public boolean isSkipDescendants() {
            return skipDescendants;
        }

        public void setSkipDescendants(boolean skipDescendants) {
            this.skipDescendants = skipDescendants;
        }

        public boolean isAddingDir() {
            return addingDir;
        }

        public void setAddingDir(boolean addingDir) {
            this.addingDir = addingDir;
        }

        public boolean isInDeletedAndTreeConflictedSubtree() {
            return inDeletedAndTreeConflictedSubtree;
        }

        public void setInDeletedAndTreeConflictedSubtree(boolean inDeletedAndTreeConflictedSubtree) {
            this.inDeletedAndTreeConflictedSubtree = inDeletedAndTreeConflictedSubtree;
        }

        public SVNDepth getAmbientDepth() {
            return ambientDepth;
        }

        public void setAmbientDepth(SVNDepth ambientDepth) {
            this.ambientDepth = ambientDepth;
        }

        public boolean isWasIncomplete() {
            return wasIncomplete;
        }

        public void setWasIncomplete(boolean wasIncomplete) {
            this.wasIncomplete = wasIncomplete;
        }

        public void cleanup() throws SVNException {
            SVNUpdateEditor17.this.myWcContext.getDb().runWorkQueue(getLocalAbspath());
        }

    }

    private class SVNFileInfo extends SVNEntryInfo {

        private boolean addingFile;
        private boolean addedWithHistory;
        private boolean deleted;
        private SVNChecksum newTextBaseMd5Checksum;
        private SVNChecksum newTextBaseSha1Checksum;
        private SVNChecksum copiedTextBaseMd5Checksum;
        private SVNChecksum copiedTextBaseSha1Checksum;
        private File copiedWorkingText;
        private SVNProperties copiedBaseProps;
        private SVNProperties copied_working_props;
        private boolean receivedTextdelta;
        private SVNDate lastChangedDate;
        private boolean addingBaseUnderLocalAdd;

        public boolean isAddingFile() {
            return addingFile;
        }

        public void setAddingFile(boolean addingFile) {
            this.addingFile = addingFile;
        }

        public boolean isAddedWithHistory() {
            return addedWithHistory;
        }

        public void setAddedWithHistory(boolean addedWithHistory) {
            this.addedWithHistory = addedWithHistory;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }

        public SVNChecksum getNewTextBaseMd5Checksum() {
            return newTextBaseMd5Checksum;
        }

        public void setNewTextBaseMd5Checksum(SVNChecksum newTextBaseMd5Checksum) {
            this.newTextBaseMd5Checksum = newTextBaseMd5Checksum;
        }

        public SVNChecksum getNewTextBaseSha1Checksum() {
            return newTextBaseSha1Checksum;
        }

        public void setNewTextBaseSha1Checksum(SVNChecksum newTextBaseSha1Checksum) {
            this.newTextBaseSha1Checksum = newTextBaseSha1Checksum;
        }

        public SVNChecksum getCopiedTextBaseMd5Checksum() {
            return copiedTextBaseMd5Checksum;
        }

        public void setCopiedTextBaseMd5Checksum(SVNChecksum copiedTextBaseMd5Checksum) {
            this.copiedTextBaseMd5Checksum = copiedTextBaseMd5Checksum;
        }

        public SVNChecksum getCopiedTextBaseSha1Checksum() {
            return copiedTextBaseSha1Checksum;
        }

        public void setCopiedTextBaseSha1Checksum(SVNChecksum copiedTextBaseSha1Checksum) {
            this.copiedTextBaseSha1Checksum = copiedTextBaseSha1Checksum;
        }

        public File getCopiedWorkingText() {
            return copiedWorkingText;
        }

        public void setCopiedWorkingText(File copiedWorkingText) {
            this.copiedWorkingText = copiedWorkingText;
        }

        public SVNProperties getCopiedBaseProps() {
            return copiedBaseProps;
        }

        public void setCopiedBaseProps(SVNProperties copiedBaseProps) {
            this.copiedBaseProps = copiedBaseProps;
        }

        public SVNProperties getCopied_working_props() {
            return copied_working_props;
        }

        public void setCopied_working_props(SVNProperties copied_working_props) {
            this.copied_working_props = copied_working_props;
        }

        public boolean isReceivedTextdelta() {
            return receivedTextdelta;
        }

        public void setReceivedTextdelta(boolean receivedTextdelta) {
            this.receivedTextdelta = receivedTextdelta;
        }

        public SVNDate getLastChangedDate() {
            return lastChangedDate;
        }

        public void setLastChangedDate(SVNDate lastChangedDate) {
            this.lastChangedDate = lastChangedDate;
        }

        public boolean isAddingBaseUnderLocalAdd() {
            return addingBaseUnderLocalAdd;
        }

        public void setAddingBaseUnderLocalAdd(boolean addingBaseUnderLocalAdd) {
            this.addingBaseUnderLocalAdd = addingBaseUnderLocalAdd;
        }

    }
}
