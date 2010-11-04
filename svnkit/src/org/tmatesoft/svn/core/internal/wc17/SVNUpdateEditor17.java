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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumOutputStream;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictedInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.CheckWCRootInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.NodeCopyFromField;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.WritableBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNUpdateEditor17 implements ISVNUpdateEditor {

    private SVNWCContext myWcContext;

    private String myTargetBasename;
    private File myAnchorAbspath;
    private File myTargetAbspath;
    private String[] myExtensionPatterns;
    private long myTargetRevision;
    private SVNDepth myRequestedDepth;
    private boolean myIsDepthSticky;
    private boolean myIsUseCommitTimes;
    private boolean myIsRootOpened;
    private boolean myIsTargetDeleted;
    private boolean myIsUnversionedObstructionsAllowed;
    private boolean myIsLockOnDemand;
    private File mySwitchRelpath;
    private SVNURL myReposRootURL;
    private String myReposUuid;
    private Set<File> mySkippedTrees = new HashSet<File>();
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNFileFetcher myFileFetcher;
    private SVNExternalsStore myExternalsStore;
    private SVNDirectoryInfo myCurrentDirectory;

    private SVNFileInfo myCurrentFile;

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
        return new SVNUpdateEditor17(wcContext, anchorAbspath, target, info.reposRootUrl, info.reposUuid, switchURL, externalsStore, allowUnversionedObstructions, depthIsSticky, depth, preservedExts,
                fileFetcher, updateLocksOnDemand);
    }

    public SVNUpdateEditor17(SVNWCContext wcContext, File anchorAbspath, String targetBasename, SVNURL reposRootUrl, String reposUuid, SVNURL switchURL, SVNExternalsStore externalsStore,
            boolean allowUnversionedObstructions, boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, boolean lockOnDemand) {
        myWcContext = wcContext;
        myAnchorAbspath = anchorAbspath;
        myTargetBasename = targetBasename;
        myIsUnversionedObstructionsAllowed = allowUnversionedObstructions;
        myTargetRevision = -1;
        myRequestedDepth = depth;
        myIsDepthSticky = depthIsSticky;
        myDeltaProcessor = new SVNDeltaProcessor();
        myExtensionPatterns = preservedExts;
        myFileFetcher = fileFetcher;
        myTargetAbspath = anchorAbspath;
        myReposRootURL = reposRootUrl;
        myReposUuid = reposUuid;
        myIsLockOnDemand = lockOnDemand;
        myExternalsStore = externalsStore;
        myIsUseCommitTimes = myWcContext.getOptions().isUseCommitTimes();
        if (myTargetBasename != null) {
            myTargetAbspath = SVNFileUtil.createFilePath(myTargetAbspath, myTargetBasename);
        }
        if ("".equals(myTargetBasename)) {
            myTargetBasename = null;
        }
        if (switchURL != null)
            mySwitchRelpath = SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(reposRootUrl.getPath(), switchURL.getPath()));
        else
            mySwitchRelpath = null;
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
        } else {
            already_conflicted = false;
        }
        if (already_conflicted) {
            myCurrentDirectory.setSkipThis(true);
            myCurrentDirectory.setSkipDescendants(true);
            myCurrentDirectory.setAlreadyNotified(true);
            myCurrentDirectory.getBumpInfo().setSkipped(true);
            doNotification(myTargetAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        if (myTargetBasename == null) {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(myCurrentDirectory.getLocalAbspath(), BaseInfoField.status, BaseInfoField.depth);
            myCurrentDirectory.setAmbientDepth(baseInfo.depth);
            myCurrentDirectory.setWasIncomplete(baseInfo.status == SVNWCDbStatus.Incomplete);
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
        String base = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(path));
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
            doNotification(localAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        boolean hidden = db.isNodeHidden(localAbspath);
        if (hidden) {
            db.removeBase(localAbspath);
            if (localAbspath.equals(myTargetAbspath))
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
        if (!localAbspath.equals(myTargetAbspath)) {
            SVNSkel workItem = myWcContext.wqBuildBaseRemove(localAbspath, false);
            myWcContext.getDb().addWorkQueue(dirAbspath, workItem);
        } else {
            SVNSkel workItem = myWcContext.wqBuildBaseRemove(localAbspath, true);
            myWcContext.getDb().addWorkQueue(dirAbspath, workItem);
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
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNWCDbKind dbKind = kind == SVNNodeKind.DIR ? SVNWCDbKind.Dir : SVNWCDbKind.File;
        File localAbspath = SVNFileUtil.createFilePath(myCurrentDirectory.getLocalAbspath(), name);
        SVNNodeKind existing_kind = myWcContext.readKind(localAbspath, true);
        if (existing_kind != SVNNodeKind.NONE) {
            if (myWcContext.isNodeAdded(localAbspath)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        WCDbRepositoryInfo baseReposInfo = myWcContext.getDb().scanBaseRepository(myCurrentDirectory.getLocalAbspath(), RepositoryInfoField.relPath, RepositoryInfoField.rootUrl,
                RepositoryInfoField.uuid);
        File reposRelpath = baseReposInfo.relPath;
        SVNURL reposRootUrl = baseReposInfo.rootUrl;
        String reposUuid = baseReposInfo.uuid;
        reposRelpath = SVNFileUtil.createFilePath(reposRelpath, name);
        myWcContext.getDb().addBaseAbsentNode(localAbspath, reposRelpath, reposRootUrl, reposUuid, myTargetRevision, dbKind, SVNWCDbStatus.Absent, null, null);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        assert ((copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision)) || (copyFromPath == null && !SVNRevision.isValidRevisionNumber(copyFromRevision)));
        if (copyFromPath != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Failed to add directory ''{0}'': copyfrom arguments not yet supported", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNDirectoryInfo pb = myCurrentDirectory;
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, new File(path), true);
        SVNDirectoryInfo db = myCurrentDirectory;
        SVNTreeConflictDescription treeConflict = null;
        if (pb.isSkipDescendants()) {
            if (!pb.isSkipThis())
                rememberSkippedTree(db.getLocalAbspath());
            db.setSkipThis(true);
            db.setSkipDescendants(true);
            db.setAlreadyNotified(true);
            return;
        }
        checkPathUnderRoot(pb.getLocalAbspath(), db.getName());
        if (myTargetAbspath.equals(db.getLocalAbspath())) {
            db.setAmbientDepth((myRequestedDepth == SVNDepth.UNKNOWN) ? SVNDepth.INFINITY : myRequestedDepth);
        } else if (myRequestedDepth == SVNDepth.IMMEDIATES || (myRequestedDepth == SVNDepth.UNKNOWN && pb.getAmbientDepth() == SVNDepth.IMMEDIATES)) {
            db.setAmbientDepth(SVNDepth.EMPTY);
        } else {
            db.setAmbientDepth(SVNDepth.INFINITY);
        }
        if (SVNFileUtil.getAdminDirectoryName().equals(db.getName())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name as the administrative directory",
                    db.getLocalAbspath());
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(db.getLocalAbspath()));
        SVNWCDbStatus status;
        SVNWCDbKind wc_kind;
        boolean conflicted;
        boolean versionedLocallyAndPresent;
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(db.getLocalAbspath(), InfoField.status, InfoField.kind, InfoField.conflicted);
            status = readInfo.status;
            wc_kind = readInfo.kind;
            conflicted = readInfo.conflicted;
            versionedLocallyAndPresent = isNodePresent(status);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            wc_kind = SVNWCDbKind.Unknown;
            status = SVNWCDbStatus.Normal;
            conflicted = true;
            versionedLocallyAndPresent = false;
        }
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(db.getLocalAbspath());
        }
        if (conflicted && status == SVNWCDbStatus.NotPresent && kind == SVNNodeKind.NONE) {
            SVNTreeConflictDescription previous_tc = myWcContext.getTreeConflict(db.getLocalAbspath());
            if (previous_tc != null && previous_tc.getConflictReason() == SVNConflictReason.UNVERSIONED) {
                myWcContext.getDb().opSetTreeConflict(db.getLocalAbspath(), null);
                conflicted = false;
            }
        }
        if (conflicted) {
            rememberSkippedTree(db.getLocalAbspath());
            db.setSkipThis(true);
            db.setSkipDescendants(true);
            db.setAlreadyNotified(true);
            doNotification(db.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        if (versionedLocallyAndPresent) {
            boolean local_is_dir;
            boolean local_is_non_dir;
            SVNURL local_is_copy = null;
            if (status == SVNWCDbStatus.Added) {
                local_is_copy = myWcContext.getNodeCopyFromInfo(db.getLocalAbspath(), NodeCopyFromField.rootUrl).rootUrl;
            }
            local_is_dir = (wc_kind == SVNWCDbKind.Dir && status != SVNWCDbStatus.Deleted);
            local_is_non_dir = (wc_kind != SVNWCDbKind.Dir && status != SVNWCDbStatus.Deleted);
            if (local_is_dir) {
                boolean wc_root = false;
                boolean switched = false;
                try {
                    CheckWCRootInfo info = myWcContext.checkWCRoot(db.getLocalAbspath(), true);
                    wc_root = info.wcRoot;
                    switched = info.switched;
                } catch (SVNException e) {
                }
                SVNErrorMessage err = null;
                if (wc_root) {
                    err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': a separate working copy " + "with the same name already exists",
                            db.getLocalAbspath());
                }
                if (err == null && switched && mySwitchRelpath == null) {
                    err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Switched directory ''{0}'' does not match expected URL ''{1}''", new Object[] {
                            db.getLocalAbspath(), myReposRootURL.appendPath(db.getNewRelpath().getPath(), false)
                    });
                }
                if (err != null) {
                    db.setAlreadyNotified(true);
                    doNotification(db.getLocalAbspath(), SVNNodeKind.DIR, SVNEventAction.UPDATE_OBSTRUCTION);
                    SVNErrorManager.error(err, SVNLogType.WC);
                    return;
                }
            }
            if (local_is_non_dir) {
                db.setAlreadyNotified(true);
                doNotification(db.getLocalAbspath(), SVNNodeKind.DIR, SVNEventAction.UPDATE_OBSTRUCTION);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': a non-directory object " + "of the same name already exists",
                        db.getLocalAbspath());
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
            if (!pb.isInDeletedAndTreeConflictedSubtree() && (mySwitchRelpath != null || local_is_non_dir || local_is_copy != null)) {
                treeConflict = checkTreeConflict(db.getLocalAbspath(), SVNConflictAction.ADD, SVNNodeKind.DIR, db.getNewRelpath());
            }
            if (treeConflict == null) {
                db.setAddExisted(true);
            }
        } else if (kind != SVNNodeKind.NONE) {
            db.setObstructionFound(true);
            if (!(kind == SVNNodeKind.DIR && myIsUnversionedObstructionsAllowed)) {
                db.setSkipThis(true);
                myWcContext.getDb().addBaseAbsentNode(db.getLocalAbspath(), db.getNewRelpath(), myReposRootURL, myReposUuid, myTargetRevision != 0 ? myTargetRevision : SVNWCContext.INVALID_REVNUM,
                        SVNWCDbKind.Dir, SVNWCDbStatus.NotPresent, null, null);
                rememberSkippedTree(db.getLocalAbspath());
                treeConflict = createTreeConflict(db.getLocalAbspath(), SVNConflictReason.UNVERSIONED, SVNConflictAction.ADD, SVNNodeKind.DIR, db.getNewRelpath());
                assert (treeConflict != null);
            }
        }
        if (treeConflict != null) {
            myWcContext.getDb().opSetTreeConflict(db.getLocalAbspath(), treeConflict);
            rememberSkippedTree(db.getLocalAbspath());
            db.setSkipThis(true);
            db.setSkipDescendants(true);
            db.setAlreadyNotified(true);
            doNotification(db.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
            return;
        }
        myWcContext.getDb().opSetNewDirToIncompleteTemp(db.getLocalAbspath(), db.getNewRelpath(), myReposRootURL, myReposUuid, myTargetRevision, db.getAmbientDepth());
        prepareDirectory(db, myReposRootURL.appendPath(db.getNewRelpath().getPath(), false), myTargetRevision);
        if (pb.isInDeletedAndTreeConflictedSubtree()) {
            myWcContext.getDb().opDeleteTemp(db.getLocalAbspath());
        }
        if (myWcContext.getEventHandler() != null && !db.isAlreadyNotified() && !db.isAddExisted()) {
            SVNEventAction action;
            if (db.isInDeletedAndTreeConflictedSubtree())
                action = SVNEventAction.UPDATE_ADD_DELETED;
            else if (db.isObstructionFound())
                action = SVNEventAction.UPDATE_EXISTS;
            else
                action = SVNEventAction.UPDATE_ADD;
            db.setAlreadyNotified(true);
            doNotification(db.getLocalAbspath(), SVNNodeKind.DIR, action);
        }
        return;
    }

    public void openDir(String path, long revision) throws SVNException {
        SVNDirectoryInfo pb = myCurrentDirectory;
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, new File(path), false);
        SVNDirectoryInfo db = myCurrentDirectory;
        myWcContext.writeCheck(db.getLocalAbspath());
        if (pb.isSkipDescendants()) {
            if (!pb.isSkipThis()) {
                rememberSkippedTree(db.getLocalAbspath());
            }
            db.setSkipThis(true);
            db.setSkipDescendants(true);
            db.setAlreadyNotified(true);
            db.getBumpInfo().setSkipped(true);
            return;
        }
        checkPathUnderRoot(pb.getLocalAbspath(), db.getName());
        WCDbInfo readInfo = myWcContext.getDb().readInfo(db.getLocalAbspath(), InfoField.status, InfoField.revision, InfoField.depth, InfoField.haveWork, InfoField.conflicted);
        SVNWCDbStatus status = readInfo.status;
        db.setOldRevision(readInfo.revision);
        db.setAmbientDepth(readInfo.depth);
        boolean have_work = readInfo.haveWork;
        boolean conflicted = readInfo.conflicted;
        SVNTreeConflictDescription treeConflict = null;
        SVNWCDbStatus baseStatus;
        if (!have_work) {
            baseStatus = status;
        } else {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(db.getLocalAbspath(), BaseInfoField.status, BaseInfoField.revision, BaseInfoField.depth);
            baseStatus = baseInfo.status;
            db.setOldRevision(baseInfo.revision);
            db.setAmbientDepth(baseInfo.depth);
        }
        db.setWasIncomplete(baseStatus == SVNWCDbStatus.Incomplete);
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(db.getLocalAbspath());
        }
        if (conflicted) {
            rememberSkippedTree(db.getLocalAbspath());
            db.setSkipThis(true);
            db.setSkipDescendants(true);
            db.setAlreadyNotified(true);
            doNotification(db.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        if (!db.isInDeletedAndTreeConflictedSubtree()) {
            treeConflict = checkTreeConflict(db.getLocalAbspath(), SVNConflictAction.EDIT, SVNNodeKind.DIR, db.getNewRelpath());
        }
        if (treeConflict != null) {
            myWcContext.getDb().opSetTreeConflict(db.getLocalAbspath(), treeConflict);
            doNotification(db.getLocalAbspath(), SVNNodeKind.DIR, SVNEventAction.TREE_CONFLICT);
            db.setAlreadyNotified(true);
            if (treeConflict.getConflictReason() != SVNConflictReason.DELETED && treeConflict.getConflictReason() != SVNConflictReason.REPLACED) {
                rememberSkippedTree(db.getLocalAbspath());
                db.setSkipDescendants(true);
                db.setSkipThis(true);
                return;
            }
            db.setInDeletedAndTreeConflictedSubtree(true);
        }
        myWcContext.getDb().opStartDirectoryUpdateTemp(db.getLocalAbspath(), db.getNewRelpath(), myTargetRevision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!myCurrentDirectory.isSkipThis()) {
            myCurrentDirectory.propertyChanged(name, value);
        }
    }

    public void closeDir() throws SVNException {
        SVNDirectoryInfo db = myCurrentDirectory;
        if (db.isSkipThis()) {
            db.getBumpInfo().setSkipped(true);
            maybeBumpDirInfo(db.getBumpInfo());
            return;
        }
        SVNProperties entryProps = db.getChangedEntryProperties();
        SVNProperties davProps = db.getChangedWCProperties();
        SVNProperties regularProps = db.getChangedProperties();

        SVNProperties baseProps = myWcContext.getPristineProps(db.getLocalAbspath());
        SVNProperties actualProps = myWcContext.getActualProps(db.getLocalAbspath());

        if (baseProps == null) {
            baseProps = new SVNProperties();
        }
        if (actualProps == null) {
            actualProps = new SVNProperties();
        }
        SVNStatusType propStatus = SVNStatusType.UNKNOWN;
        SVNProperties newBaseProps = null;
        SVNProperties newActualProps = null;
        long newChangedRev = -1;
        SVNDate newChangedDate = null;
        String newChangedAuthor = null;
        if (db.isWasIncomplete()) {
            if (regularProps == null) {
                regularProps = new SVNProperties();
            }
            for (Iterator names = baseProps.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                if (!regularProps.containsName(name)) {
                    regularProps.put(name, SVNPropertyValue.create(null));
                }
            }
        }
        if ((regularProps != null && !regularProps.isEmpty()) || (entryProps != null && !entryProps.isEmpty()) || (davProps != null && !davProps.isEmpty())) {
            if (regularProps != null && !regularProps.isEmpty()) {
                if (myExternalsStore != null) {
                    if (regularProps.containsName(SVNProperty.EXTERNALS)) {
                        File path = db.getLocalAbspath();
                        String newValue = regularProps.getStringValue(SVNProperty.EXTERNALS);
                        String oldValue = myWcContext.getProperty(path, SVNProperty.EXTERNALS);
                        if (oldValue == null && newValue == null)
                            ;
                        else if (oldValue != null && newValue != null && oldValue.equals(newValue))
                            ;
                        else if (oldValue != null || newValue != null) {
                            myExternalsStore.addExternal(path, oldValue, newValue);
                            myExternalsStore.addDepth(path, db.getAmbientDepth());
                        }
                    }
                }
                try {
                    newBaseProps = new SVNProperties();
                    newActualProps = new SVNProperties();
                    propStatus = myWcContext.mergeProperties(newBaseProps, newActualProps, db.getLocalAbspath(), SVNWCDbKind.Dir, null, null, null, baseProps, actualProps, regularProps, true, false);
                } catch (SVNException e) {
                    SVNErrorMessage err = e.getErrorMessage().wrap("Couldn't do property merge");
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                }
            }
            AccumulatedChangeInfo change = accumulateLastChange(db.getLocalAbspath(), entryProps);
            newChangedRev = change.changedRev;
            newChangedDate = change.changedDate;
            newChangedAuthor = change.changedAuthor;
        }

        if (db.getParentDir() == null && myTargetBasename != null && !myTargetBasename.equals("")) {
            assert (db.getChangedEntryProperties() == null && db.getChangedWCProperties() == null && db.getChangedProperties() == null);
        } else {

            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(db.getLocalAbspath(), BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor, BaseInfoField.depth);
            long changedRev = baseInfo.changedRev;
            SVNDate changedDate = baseInfo.changedDate;
            String changedAuthor = baseInfo.changedAuthor;
            SVNDepth depth = baseInfo.depth;

            if (SVNRevision.isValidRevisionNumber(newChangedRev)) {
                changedRev = newChangedRev;
            }
            if (newChangedDate != null && newChangedDate.getTime() != 0) {
                changedDate = newChangedDate;
            }
            if (newChangedAuthor != null) {
                changedAuthor = newChangedAuthor;
            }

            if (depth == SVNDepth.UNKNOWN) {
                depth = SVNDepth.INFINITY;
            }

            SVNProperties props = newBaseProps;
            if (props == null) {
                props = myWcContext.getDb().getBaseProps(db.getLocalAbspath());
            }

            myWcContext.getDb().addBaseDirectory(db.getLocalAbspath(), db.getNewRelpath(), myReposRootURL, myReposUuid, myTargetRevision, props, changedRev, changedDate, changedAuthor, null, depth,
                    (davProps != null && !davProps.isEmpty() ? davProps : null), null, null);
            if (newBaseProps != null) {
                assert (newActualProps != null);
                props = newActualProps;
                SVNProperties propDiffs = SVNWCUtils.propDiffs(newActualProps, newBaseProps);
                if (propDiffs.isEmpty()) {
                    props = null;
                }
                myWcContext.getDb().opSetProps(db.getLocalAbspath(), props, null, null);
            }
        }
        myWcContext.wqRun(db.getLocalAbspath());
        maybeBumpDirInfo(db.getBumpInfo());
        if (db.isAlreadyNotified() && myWcContext.getEventHandler() != null) {
            SVNEventAction action;
            if (db.isInDeletedAndTreeConflictedSubtree()) {
                action = SVNEventAction.UPDATE_UPDATE_DELETED;
            } else if (db.isObstructionFound() || db.isAddExisted()) {
                action = SVNEventAction.UPDATE_EXISTS;
            } else {
                action = SVNEventAction.UPDATE_UPDATE;
            }
            SVNEvent event = new SVNEvent(db.getLocalAbspath(), SVNNodeKind.DIR, null, myTargetRevision, null, propStatus, null, null, action, null, null, null, null);
            event.setPreviousRevision(db.getOldRevision());
            myWcContext.getEventHandler().handleEvent(event, 0);
        }
        SVNBumpDirInfo bdi = db.getBumpInfo();
        while (bdi != null && bdi.getRefCount() == 0) {
            bdi.getEntryInfo().cleanup();
            bdi = bdi.getParent();
        }
        return;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        assert ((copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision)) || (copyFromPath == null && !SVNRevision.isValidRevisionNumber(copyFromRevision)));
        SVNDirectoryInfo pb = myCurrentDirectory;
        SVNFileInfo fb = createFileInfo(pb, new File(path), true);
        myCurrentFile = fb;
        SVNTreeConflictDescription treeConflict = null;
        if (pb.isSkipDescendants()) {
            if (!pb.isSkipThis()) {
                rememberSkippedTree(fb.getLocalAbspath());
            }
            fb.setSkipThis(true);
            fb.setAlreadyNotified(true);
            return;
        }
        checkPathUnderRoot(pb.getLocalAbspath(), fb.getName());
        fb.setDeleted(pb.isInDeletedAndTreeConflictedSubtree());
        if (SVNFileUtil.getAdminDirectoryName().equals(fb.getName())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'' : object of the same name as the administrative directory",
                    fb.getLocalAbspath());
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        SVNNodeKind kind;
        SVNWCDbKind wcKind;
        SVNWCDbStatus status;
        boolean conflicted;
        boolean versionedLocallyAndPresent;
        kind = SVNFileType.getNodeKind(SVNFileType.getType(fb.getLocalAbspath()));
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(fb.getLocalAbspath(), InfoField.status, InfoField.kind, InfoField.conflicted);
            status = readInfo.status;
            wcKind = readInfo.kind;
            conflicted = readInfo.conflicted;
            versionedLocallyAndPresent = isNodePresent(status);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            wcKind = SVNWCDbKind.Unknown;
            status = SVNWCDbStatus.Normal;
            conflicted = true;
            versionedLocallyAndPresent = false;
        }
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(fb.getLocalAbspath());
        }
        if (conflicted && status == SVNWCDbStatus.NotPresent && kind == SVNNodeKind.NONE) {
            SVNTreeConflictDescription previousTc = myWcContext.getTreeConflict(fb.getLocalAbspath());
            if (previousTc != null && previousTc.getConflictReason() == SVNConflictReason.UNVERSIONED) {
                myWcContext.getDb().opSetTreeConflict(fb.getLocalAbspath(), null);
                conflicted = isNodeAlreadyConflicted(fb.getLocalAbspath());
            }
        }
        if (conflicted) {
            rememberSkippedTree(fb.getLocalAbspath());
            fb.setSkipThis(true);
            fb.setAlreadyNotified(true);
            doNotification(fb.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        if (versionedLocallyAndPresent) {
            boolean localIsFile;
            boolean isFileExternal;

            if (status == SVNWCDbStatus.Added) {
                status = myWcContext.getDb().scanAddition(fb.getLocalAbspath(), AdditionInfoField.status).status;
            }
            localIsFile = (wcKind == SVNWCDbKind.File || wcKind == SVNWCDbKind.Symlink);
            if (localIsFile) {
                boolean wcRoot = false;
                boolean switched = false;
                try {
                    CheckWCRootInfo checkWCRoot = myWcContext.checkWCRoot(fb.getLocalAbspath(), true);
                    wcRoot = checkWCRoot.wcRoot;
                    switched = checkWCRoot.switched;
                } catch (SVNException e) {

                }
                if (switched && mySwitchRelpath == null) {
                    fb.setAlreadyNotified(true);
                    doNotification(fb.getLocalAbspath(), SVNNodeKind.FILE, SVNEventAction.UPDATE_OBSTRUCTION);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Switched file ''{0}'' does not match expected URL ''{1}''", new Object[] {
                            fb.getLocalAbspath(), fb.getNewRelpath()
                    });
                    SVNErrorManager.error(err, SVNLogType.WC);
                    return;
                }
            }
            try {
                isFileExternal = myWcContext.isFileExternal(fb.getLocalAbspath());
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    isFileExternal = false;
                } else {
                    throw e;
                }
            }
            if (!pb.isInDeletedAndTreeConflictedSubtree() && !isFileExternal && (mySwitchRelpath != null || !localIsFile || status != SVNWCDbStatus.Added)) {
                treeConflict = checkTreeConflict(fb.getLocalAbspath(), SVNConflictAction.ADD, SVNNodeKind.FILE, fb.getNewRelpath());
            }
            if (treeConflict == null) {
                fb.setAddExisted(true);
            } else {
                fb.setAddingBaseUnderLocalAdd(true);
            }
        } else if (kind != SVNNodeKind.NONE) {
            fb.setObstructionFound(true);
            if (!(kind == SVNNodeKind.FILE && myIsUnversionedObstructionsAllowed)) {
                fb.setSkipThis(true);
                myWcContext.getDb().addBaseAbsentNode(fb.getLocalAbspath(), fb.getNewRelpath(), myReposRootURL, myReposUuid, myTargetRevision != 0 ? myTargetRevision : SVNWCContext.INVALID_REVNUM,
                        SVNWCDbKind.File, SVNWCDbStatus.NotPresent, null, null);
                rememberSkippedTree(fb.getLocalAbspath());
                treeConflict = createTreeConflict(fb.getLocalAbspath(), SVNConflictReason.UNVERSIONED, SVNConflictAction.ADD, SVNNodeKind.FILE, fb.getNewRelpath());
                assert (treeConflict != null);
            }
        }
        if (treeConflict != null) {
            fb.setObstructionFound(true);
            myWcContext.getDb().opSetTreeConflict(fb.getLocalAbspath(), treeConflict);
            fb.setAlreadyNotified(true);
            doNotification(fb.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
        }
        return;
    }

    public void openFile(String path, long revision) throws SVNException {
        SVNDirectoryInfo pb = myCurrentDirectory;
        SVNFileInfo fb = createFileInfo(pb, new File(path), false);
        myCurrentFile = fb;
        if (pb.isSkipDescendants()) {
            if (!pb.isSkipThis()) {
                rememberSkippedTree(fb.getLocalAbspath());
            }
            fb.setSkipThis(true);
            fb.setAlreadyNotified(true);
            return;
        }
        checkPathUnderRoot(pb.getLocalAbspath(), fb.getName());
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(fb.getLocalAbspath()));
        WCDbInfo readInfo = myWcContext.getDb().readInfo(fb.getLocalAbspath(), InfoField.revision, InfoField.conflicted);
        fb.setOldRevision(readInfo.revision);
        boolean conflicted = readInfo.conflicted;
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(fb.getLocalAbspath());
        }
        if (conflicted) {
            rememberSkippedTree(fb.getLocalAbspath());
            fb.setSkipThis(true);
            fb.setAlreadyNotified(true);
            doNotification(fb.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        fb.setDeleted(pb.isInDeletedAndTreeConflictedSubtree());
        SVNTreeConflictDescription treeConflict = null;
        if (!pb.isInDeletedAndTreeConflictedSubtree()) {
            treeConflict = checkTreeConflict(fb.getLocalAbspath(), SVNConflictAction.EDIT, SVNNodeKind.FILE, fb.getNewRelpath());
        }
        if (treeConflict != null) {
            myWcContext.getDb().opSetTreeConflict(fb.getLocalAbspath(), treeConflict);
            if (treeConflict.getConflictReason() == SVNConflictReason.DELETED || treeConflict.getConflictReason() == SVNConflictReason.REPLACED) {
                fb.setDeleted(true);
            } else {
                rememberSkippedTree(fb.getLocalAbspath());
            }
            if (!fb.isDeleted()) {
                fb.setSkipThis(true);
            }
            fb.setAlreadyNotified(true);
            doNotification(fb.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
        }
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        SVNFileInfo fb = myCurrentFile;
        if (fb.isSkipThis()) {
            return;
        }
        fb.getChangedProperties().put(propertyName, propertyValue);
        if (myIsUseCommitTimes && SVNProperty.COMMITTED_DATE.equals(propertyName)) {
            fb.setLastChangedDate(propertyValue.getString());
            if (fb.getLastChangedDate() != null) {
                fb.setLastChangedDate(fb.getLastChangedDate().trim());
            }
        }
        return;
    }

    public void closeFile(String path, String expectedMd5Digest) throws SVNException {
        SVNFileInfo fb = myCurrentFile;
        if (fb.isSkipThis()) {
            maybeBumpDirInfo(fb.getBumpInfo());
            return;
        }

        SVNChecksum expectedMd5Checksum = null;
        if (expectedMd5Digest != null) {
            expectedMd5Checksum = new SVNChecksum(SVNChecksumKind.MD5, expectedMd5Digest);
        }

        SVNChecksum newTextBaseMd5Checksum;
        SVNChecksum newTextBaseSha1Checksum;
        if (fb.isReceivedTextdelta()) {
            newTextBaseMd5Checksum = fb.getNewTextBaseMd5Checksum();
            newTextBaseSha1Checksum = fb.getNewTextBaseSha1Checksum();
            assert (newTextBaseMd5Checksum != null && newTextBaseSha1Checksum != null);
        } else if (fb.isAddedWithHistory()) {
            assert (fb.getNewTextBaseSha1Checksum() == null);
            newTextBaseMd5Checksum = fb.getCopiedTextBaseMd5Checksum();
            newTextBaseSha1Checksum = fb.getCopiedTextBaseSha1Checksum();
            assert (newTextBaseMd5Checksum != null && newTextBaseSha1Checksum != null);
        } else {
            assert (fb.getNewTextBaseSha1Checksum() == null && fb.getCopiedTextBaseSha1Checksum() == null);
            newTextBaseMd5Checksum = null;
            newTextBaseSha1Checksum = null;
        }

        if (newTextBaseMd5Checksum != null && expectedMd5Checksum != null && !newTextBaseMd5Checksum.getDigest().equals(expectedMd5Checksum.getDigest())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''", new Object[] {
                    fb.getLocalAbspath(), expectedMd5Checksum, newTextBaseMd5Checksum
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(fb.getLocalAbspath()));
        if (kind == SVNNodeKind.NONE && !fb.isAddingFile()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", fb.getLocalAbspath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNProperties entryProps = fb.getChangedEntryProperties();
        SVNProperties davProps = fb.getChangedWCProperties();
        SVNProperties regularProps = fb.getChangedProperties();

        AccumulatedChangeInfo lastChange = accumulateLastChange(fb.getLocalAbspath(), entryProps);
        long newChangedRev = lastChange.changedRev;
        SVNDate newChangedDate = lastChange.changedDate;
        String newChangedAuthor = lastChange.changedAuthor;

        SVNStatusType lockState = SVNStatusType.LOCK_UNCHANGED;
        {
            for (Iterator i = entryProps.nameSet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                if (SVNProperty.LOCK_TOKEN.equals(name)) {
                    assert (entryProps.getStringValue(name) == null);
                    myWcContext.getDb().removeLock(fb.getLocalAbspath());
                    lockState = SVNStatusType.LOCK_UNLOCKED;
                    break;
                }
            }
        }

        SVNProperties localActualProps = null;
        if (kind != SVNNodeKind.NONE) {
            localActualProps = myWcContext.getActualProps(fb.getLocalAbspath());
        }
        if (localActualProps == null)
            localActualProps = new SVNProperties();

        SVNProperties currentBaseProps = null;
        SVNProperties currentActualProps = null;
        if (fb.getCopiedBaseProps() != null) {
            currentBaseProps = fb.getCopiedBaseProps();
            currentActualProps = fb.getCopiedWorkingProps();
        } else if (kind != SVNNodeKind.NONE) {
            currentBaseProps = myWcContext.getPristineProps(fb.getLocalAbspath());
            currentActualProps = localActualProps;
        }
        if (currentBaseProps == null) {
            currentBaseProps = new SVNProperties();
        }
        if (currentActualProps == null) {
            currentActualProps = new SVNProperties();
        }

        if (fb.isAddingFile() && fb.isAddExisted()) {
            boolean localIsLink = localActualProps.getStringValue(SVNProperty.SPECIAL) != null;
            boolean incomingIsLink = false;
            if (fb.getCopiedBaseProps() != null) {
                incomingIsLink = fb.getCopiedWorkingProps() != null && fb.getCopiedWorkingProps().getStringValue(SVNProperty.SPECIAL) != null;
            } else {
                for (Iterator i = regularProps.nameSet().iterator(); i.hasNext();) {
                    String propName = (String) i.next();
                    if (SVNProperty.SPECIAL.equals(propName)) {
                        incomingIsLink = true;
                    }
                }
            }
            if (localIsLink != incomingIsLink) {
                fb.setAddingBaseUnderLocalAdd(true);
                fb.setObstructionFound(true);
                fb.setAddExisted(false);
                SVNTreeConflictDescription treeConflict = checkTreeConflict(fb.getLocalAbspath(), SVNConflictAction.ADD, SVNNodeKind.FILE, fb.getNewRelpath());
                assert (treeConflict != null);
                myWcContext.getDb().opSetTreeConflict(fb.getLocalAbspath(), treeConflict);
                fb.setAlreadyNotified(true);
                doNotification(fb.getLocalAbspath(), SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
            }
        }

        SVNStatusType propState = SVNStatusType.UNKNOWN;
        SVNProperties newBaseProps = new SVNProperties();
        SVNProperties newActualProps = new SVNProperties();

        SVNSkel workItem;
        SVNSkel allWorkItems = null;
        boolean installPristine;
        File installFrom = null;
        SVNStatusType contentState = null;

        if (!fb.isAddingBaseUnderLocalAdd()) {
            propState = myWcContext.mergeProperties(newBaseProps, newActualProps, fb.getLocalAbspath(), SVNWCDbKind.File, null, null, null, currentBaseProps, currentActualProps, regularProps, true,
                    false);
            assert (!newBaseProps.isEmpty() && !newActualProps.isEmpty());

            MergeFileInfo mergeFile = mergeFile(fb, newTextBaseSha1Checksum);
            allWorkItems = mergeFile.workItems;
            installPristine = mergeFile.installPristine;
            installFrom = mergeFile.installFrom;
            contentState = mergeFile.contentState;

            if (installPristine) {
                boolean recordFileinfo = installFrom == null;
                workItem = myWcContext.wqBuildFileInstall(fb.getLocalAbspath(), installFrom, myIsUseCommitTimes, recordFileinfo);
                allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);
            }

        } else {
            SVNProperties noNewActualProps = new SVNProperties();
            SVNProperties noWorkingProps = new SVNProperties();
            SVNProperties copiedBaseProps = fb.getCopiedBaseProps();
            if (copiedBaseProps == null) {
                copiedBaseProps = new SVNProperties();
            }
            SVNStatusType noPropState = myWcContext.mergeProperties(newBaseProps, noNewActualProps, fb.getLocalAbspath(), SVNWCDbKind.File, null, null, null, copiedBaseProps, noWorkingProps,
                    regularProps, true, false);
            propState = SVNStatusType.UNCHANGED;
            newActualProps = localActualProps;
        }

        if (newTextBaseSha1Checksum == null && lockState == SVNStatusType.LOCK_UNLOCKED) {
            workItem = myWcContext.wqBuildSyncFileFlags(fb.getLocalAbspath());
            allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);
        }

        if (installFrom != null && !installFrom.equals(fb.getLocalAbspath())) {
            workItem = myWcContext.wqBuildFileRemove(installFrom);
            allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);
        }

        if (fb.getCopiedTextBaseSha1Checksum() != null) {
            /*
             * ### TODO: Add a WQ item to remove this pristine if unreferenced:
             * svn_wc__wq_build_pristine_remove(&work_item, eb->db,
             * fb->local_abspath, fb->copied_text_base_sha1_checksum, pool);
             * all_work_items = svn_wc__wq_merge(all_work_items, work_item,
             * pool);
             */
        }

        {
            SVNChecksum newChecksum = newTextBaseSha1Checksum;
            String serialised = null;

            if (newChecksum == null) {
                newChecksum = myWcContext.getDb().getBaseInfo(fb.getLocalAbspath(), BaseInfoField.checksum).checksum;
            }

            if (kind != SVNNodeKind.NONE) {
                serialised = myWcContext.getDb().getFileExternalTemp(fb.getLocalAbspath());
            }

            myWcContext.getDb().addBaseFile(fb.getLocalAbspath(), fb.getNewRelpath(), myReposRootURL, myReposUuid, myTargetRevision, newBaseProps, newChangedRev, newChangedDate, newChangedAuthor,
                    newChecksum, -1, (davProps != null && !davProps.isEmpty()) ? davProps : null, null, allWorkItems);

            if (kind != SVNNodeKind.NONE && serialised != null) {
                Map map = new HashMap();
                SVNAdminUtil.unserializeExternalFileData(map, serialised);
                File fileExternalReposRelpath = SVNFileUtil.createFilePath((String) map.get(SVNProperty.FILE_EXTERNAL_PATH));
                SVNRevision fileExternalPegRev = (SVNRevision) map.get(SVNProperty.FILE_EXTERNAL_PEG_REVISION);
                SVNRevision fileExternalRev = (SVNRevision) map.get(SVNProperty.FILE_EXTERNAL_REVISION);
                myWcContext.getDb().opSetFileExternal(fb.getLocalAbspath(), fileExternalReposRelpath, fileExternalPegRev, fileExternalRev);
            }
        }

        if (fb.getParentDir().isInDeletedAndTreeConflictedSubtree() && fb.isAddingFile()) {
            myWcContext.getDb().opDeleteTemp(fb.getLocalAbspath());
        }

        if (fb.isAddExisted() && fb.isAddingFile()) {
            myWcContext.getDb().opRemoveWorkingTemp(fb.getLocalAbspath());
        }

        if (!fb.isAddingBaseUnderLocalAdd()) {
            assert (newActualProps != null);
            SVNProperties props = newActualProps;
            SVNProperties prop_diffs = SVNWCUtils.propDiffs(newActualProps, newBaseProps);
            if (prop_diffs.isEmpty()) {
                props = null;
            }
            myWcContext.getDb().opSetProps(fb.getLocalAbspath(), props, null, null);
        }

        myWcContext.wqRun(fb.getParentDir().getLocalAbspath());
        maybeBumpDirInfo(fb.getBumpInfo());

        if (myWcContext.getEventHandler() != null && !fb.isAlreadyNotified()) {
            SVNEventAction action = SVNEventAction.UPDATE_UPDATE;
            if (fb.isDeleted())
                action = SVNEventAction.UPDATE_ADD_DELETED;
            else if (fb.isObstructionFound() || fb.isAddExisted()) {
                if (contentState != SVNStatusType.CONFLICTED)
                    action = SVNEventAction.UPDATE_EXISTS;
            } else if (fb.isAddingFile()) {
                action = SVNEventAction.UPDATE_ADD;
            }
            String mimeType = myWcContext.getProperty(fb.getLocalAbspath(), SVNProperty.MIME_TYPE);
            SVNEvent event = SVNEventFactory.createSVNEvent(fb.getLocalAbspath(), SVNNodeKind.FILE, mimeType, myTargetRevision, contentState, propState, lockState, action, null, null, null);
            event.setPreviousRevision(fb.getOldRevision());
            myWcContext.getEventHandler().handleEvent(event, 0);
        }

    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpened) {
            completeDirectory(myAnchorAbspath, true);
        }
        if (!myIsTargetDeleted) {
            doUpdateCleanup(myTargetAbspath, myRequestedDepth, mySwitchRelpath, myReposRootURL, myReposUuid, myTargetRevision, mySkippedTrees);
        }
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        SVNFileInfo fb = myCurrentFile;
        if (fb.isSkipThis()) {
            return;
        }
        fb.setReceivedTextdelta(true);
        String recordedBaseChecksum;
        {
            SVNChecksum checksum = getUltimateBaseChecksums(fb.getLocalAbspath(), false, true).md5Checksum;
            recordedBaseChecksum = checksum != null ? checksum.getDigest() : null;
            if (recordedBaseChecksum != null && baseChecksum != null && !baseChecksum.equals(recordedBaseChecksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}'':\n " + "   expected:  ''{1}''\n" + "   recorded:  ''{2}''\n",
                        new Object[] {
                                myCurrentDirectory.getLocalAbspath(), baseChecksum, recordedBaseChecksum
                        });
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
        }
        InputStream source;
        if (!fb.isAddingFile()) {
            source = getUltimateBaseContents(fb.getLocalAbspath());
            if (source == null) {
                source = SVNFileUtil.DUMMY_IN;
            }
        } else {
            if (fb.getCopiedTextBaseSha1Checksum() != null) {
                source = myWcContext.getDb().readPristine(fb.getLocalAbspath(), fb.getCopiedTextBaseSha1Checksum());
            } else {
                source = SVNFileUtil.DUMMY_IN;
            }
        }
        if (recordedBaseChecksum == null) {
            recordedBaseChecksum = baseChecksum;
        }
        if (recordedBaseChecksum != null) {
            fb.setExpectedSourceMd5Checksum(new SVNChecksum(SVNChecksumKind.MD5, recordedBaseChecksum));
            if (source != SVNFileUtil.DUMMY_IN) {
                fb.setSourceChecksumStream(new SVNChecksumInputStream(source, SVNChecksumInputStream.MD5_ALGORITHM));
                source = fb.getSourceChecksumStream();
            }

        }
        WritableBaseInfo openWritableBase = myWcContext.openWritableBase(fb.getLocalAbspath(), false, true);
        OutputStream target = openWritableBase.stream;
        fb.setNewTextBaseTmpAbspath(openWritableBase.tempBaseAbspath);
        myDeltaProcessor.applyTextDelta(source, target, true);
        fb.setNewTextBaseSha1ChecksumStream(openWritableBase.sha1ChecksumStream);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (!myCurrentFile.isSkipThis()) {
            try {
                myDeltaProcessor.textDeltaChunk(diffWindow);
            } catch (SVNException svne) {
                myDeltaProcessor.textDeltaEnd();
                SVNFileUtil.deleteFile(myCurrentFile.getNewTextBaseTmpAbspath());
                myCurrentFile.setNewTextBaseTmpAbspath(null);
                throw svne;
            }
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (!myCurrentFile.isSkipThis()) {
            myCurrentFile.setNewTextBaseMd5Checksum(new SVNChecksum(SVNChecksumKind.MD5, myDeltaProcessor.textDeltaEnd()));
        }

        if (myCurrentFile.getNewTextBaseSha1ChecksumStream() != null) {
            myCurrentFile.setNewTextBaseSha1Checksum(new SVNChecksum(SVNChecksumKind.SHA1, myCurrentFile.getNewTextBaseSha1ChecksumStream().getDigest()));
        }

        if (myCurrentFile.getExpectedSourceMd5Checksum() != null) {
            String actualSourceChecksum = myCurrentFile.sourceChecksumStream != null ? myCurrentFile.sourceChecksumStream.getDigest() : null;
            if (!myCurrentFile.getExpectedSourceMd5Checksum().getDigest().equals(actualSourceChecksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch while updating ''{0}''; expected: ''{1}'', actual: ''{2}''", new Object[] {
                        myCurrentFile.getLocalAbspath(), myCurrentFile.getExpectedSourceMd5Checksum().getDigest(), actualSourceChecksum
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        if (myCurrentFile.getNewTextBaseTmpAbspath() != null && myCurrentFile.getNewTextBaseSha1Checksum() != null && myCurrentFile.getNewTextBaseMd5Checksum() != null) {
            myWcContext.getDb().installPristine(myCurrentFile.getNewTextBaseTmpAbspath(), myCurrentFile.getNewTextBaseSha1Checksum(), myCurrentFile.getNewTextBaseMd5Checksum());
        }

    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, File path, boolean added) throws SVNException {
        assert (path != null || parent == null);
        SVNDirectoryInfo d = new SVNDirectoryInfo();
        if (path != null) {
            d.setName(SVNFileUtil.getFileName(path));
            d.setLocalAbspath(SVNFileUtil.createFilePath(parent.getLocalAbspath(), d.getName()));
            d.setInDeletedAndTreeConflictedSubtree(parent.isInDeletedAndTreeConflictedSubtree());
        } else {
            d.setLocalAbspath(myAnchorAbspath);
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
        SVNBumpDirInfo bdi = new SVNBumpDirInfo(d);
        bdi.setParent(parent != null ? parent.getBumpInfo() : null);
        bdi.setRefCount(1);
        bdi.setLocalAbspath(d.getLocalAbspath());
        bdi.setSkipped(false);
        if (parent != null)
            bdi.getParent().setRefCount(bdi.getParent().getRefCount() + 1);
        d.setParentDir(parent);
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

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, File path, boolean added) throws SVNException {
        assert (path != null);
        SVNFileInfo f = new SVNFileInfo();
        f.setName(SVNFileUtil.getFileName(path));
        f.setOldRevision(SVNWCContext.INVALID_REVNUM);
        f.setLocalAbspath(SVNFileUtil.createFilePath(parent.getLocalAbspath(), f.getName()));
        if (mySwitchRelpath != null) {
            f.setNewRelpath(SVNFileUtil.createFilePath(parent.getNewRelpath(), f.getName()));
        } else {
            f.setNewRelpath(getNodeRelpathIgnoreErrors(f.getLocalAbspath()));
        }
        if (f.getNewRelpath() == null) {
            f.setNewRelpath(SVNFileUtil.createFilePath(parent.getNewRelpath(), f.getName()));
        }
        f.setBumpInfo(parent.getBumpInfo());
        f.setAddingFile(added);
        f.setObstructionFound(false);
        f.setAddExisted(false);
        f.setDeleted(false);
        f.setParentDir(parent);
        f.getBumpInfo().setRefCount(f.getBumpInfo().getRefCount() + 1);
        return f;
    }

    private class SVNBumpDirInfo {

        private final SVNEntryInfo entryInfo;

        private SVNBumpDirInfo parent;
        private int refCount;
        private File localAbspath;
        private boolean skipped;

        public SVNBumpDirInfo(SVNEntryInfo entryInfo) {
            this.entryInfo = entryInfo;
        }

        public SVNEntryInfo getEntryInfo() {
            return entryInfo;
        }

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

    private class SVNEntryInfo implements SVNWCContext.CleanupHandler {

        private String name;
        private File localAbspath;
        private File newRelpath;
        private long oldRevision;
        private SVNDirectoryInfo parentDir;
        private boolean skipThis;
        private boolean alreadyNotified;
        private boolean obstructionFound;
        private boolean addExisted;
        private SVNBumpDirInfo bumpInfo;

        private SVNProperties myChangedProperties = new SVNProperties();
        private SVNProperties myChangedEntryProperties = new SVNProperties();
        private SVNProperties myChangedWCProperties = new SVNProperties();

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

        public SVNBumpDirInfo getBumpInfo() {
            return bumpInfo;
        }

        public void setBumpInfo(SVNBumpDirInfo bumpInfo) {
            this.bumpInfo = bumpInfo;
        }

        public void propertyChanged(String name, SVNPropertyValue value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new SVNProperties() : myChangedEntryProperties;
                // trim value of svn:entry property
                if (value != null) {
                    String strValue = value.getString();
                    if (strValue != null) {
                        strValue = strValue.trim();
                        value = SVNPropertyValue.create(strValue);
                    }
                }
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new SVNProperties() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new SVNProperties() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }

        public SVNProperties getChangedProperties() {
            return myChangedProperties;
        }

        public void setChangedProperties(SVNProperties changedProperties) {
            myChangedProperties = changedProperties;
        }

        public SVNProperties getChangedEntryProperties() {
            return myChangedEntryProperties;
        }

        public void setChangedEntryProperties(SVNProperties changedEntryProperties) {
            myChangedEntryProperties = changedEntryProperties;
        }

        public SVNProperties getChangedWCProperties() {
            return myChangedWCProperties;
        }

        public void setChangedWCProperties(SVNProperties changedWCProperties) {
            myChangedWCProperties = changedWCProperties;
        }

        public void cleanup() throws SVNException {
        }

    }

    private class SVNDirectoryInfo extends SVNEntryInfo {

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
            SVNUpdateEditor17.this.myWcContext.wqRun(getLocalAbspath());
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
        private SVNProperties copiedWorkingProps;
        private boolean receivedTextdelta;
        private String lastChangedDate;
        private boolean addingBaseUnderLocalAdd;
        private SVNChecksum expectedSourceMd5Checksum;
        private SVNChecksumInputStream sourceChecksumStream;
        private File newTextBaseTmpAbspath;

        private SVNChecksumOutputStream newTextBaseSha1ChecksumStream;

        public boolean isAddingFile() {
            return addingFile;
        }

        public void setNewTextBaseTmpAbspath(File tempBaseAbspath) {
            this.newTextBaseTmpAbspath = tempBaseAbspath;
        }

        public File getNewTextBaseTmpAbspath() {
            return newTextBaseTmpAbspath;
        }

        public void setSourceChecksumStream(SVNChecksumInputStream source) {
            sourceChecksumStream = source;
        }

        public SVNChecksumInputStream getSourceChecksumStream() {
            return sourceChecksumStream;
        }

        public SVNChecksum getActualSourceMd5Checksum() {
            return sourceChecksumStream != null ? new SVNChecksum(SVNChecksumKind.MD5, sourceChecksumStream.getDigest()) : null;
        }

        public void setExpectedSourceMd5Checksum(SVNChecksum checksum) {
            this.expectedSourceMd5Checksum = checksum;
        }

        public SVNChecksum getExpectedSourceMd5Checksum() {
            return expectedSourceMd5Checksum;
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

        public SVNProperties getCopiedWorkingProps() {
            return copiedWorkingProps;
        }

        public void setCopiedWorkingProps(SVNProperties copiedWorkingProps) {
            this.copiedWorkingProps = copiedWorkingProps;
        }

        public boolean isReceivedTextdelta() {
            return receivedTextdelta;
        }

        public void setReceivedTextdelta(boolean receivedTextdelta) {
            this.receivedTextdelta = receivedTextdelta;
        }

        public String getLastChangedDate() {
            return lastChangedDate;
        }

        public void setLastChangedDate(String lastChangedDate) {
            this.lastChangedDate = lastChangedDate;
        }

        public boolean isAddingBaseUnderLocalAdd() {
            return addingBaseUnderLocalAdd;
        }

        public void setAddingBaseUnderLocalAdd(boolean addingBaseUnderLocalAdd) {
            this.addingBaseUnderLocalAdd = addingBaseUnderLocalAdd;
        }

        public SVNChecksumOutputStream getNewTextBaseSha1ChecksumStream() {
            return newTextBaseSha1ChecksumStream;
        }

        public void setNewTextBaseSha1ChecksumStream(SVNChecksumOutputStream newTextBaseSha1ChecksumStream) {
            this.newTextBaseSha1ChecksumStream = newTextBaseSha1ChecksumStream;
        }

    }

    private static boolean isNodePresent(SVNWCDbStatus status) {
        return status != SVNWCDbStatus.Absent && status != SVNWCDbStatus.Excluded && status != SVNWCDbStatus.NotPresent;
    }

    private void prepareDirectory(SVNDirectoryInfo db, SVNURL ancestorUrl, long ancestorRevision) throws SVNException {
        SVNFileUtil.ensureDirectoryExists(db.getLocalAbspath());
    }

    private SVNTreeConflictDescription createTreeConflict(File localAbspath, SVNConflictReason reason, SVNConflictAction action, SVNNodeKind theirNodeKind, File theirRelpath) throws SVNException {
        assert (reason != null);
        File leftReposRelpath;
        long leftRevision;
        SVNNodeKind leftKind = null;
        File addedReposRelpath = null;
        SVNURL reposRootUrl;
        if (reason == SVNConflictReason.ADDED) {
            leftKind = SVNNodeKind.NONE;
            leftRevision = SVNWCContext.INVALID_REVNUM;
            leftReposRelpath = null;
            WCDbAdditionInfo scanAddition = myWcContext.getDb().scanAddition(localAbspath, AdditionInfoField.status, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);
            SVNWCDbStatus addedStatus = scanAddition.status;
            addedReposRelpath = scanAddition.reposRelPath;
            reposRootUrl = scanAddition.reposRootUrl;
            assert (addedStatus == SVNWCDbStatus.Added || addedStatus == SVNWCDbStatus.Copied || addedStatus == SVNWCDbStatus.MovedHere);
        } else if (reason == SVNConflictReason.UNVERSIONED) {
            leftKind = SVNNodeKind.NONE;
            leftRevision = SVNWCContext.INVALID_REVNUM;
            leftReposRelpath = null;
            reposRootUrl = myReposRootURL;
        } else {
            assert (reason == SVNConflictReason.EDITED || reason == SVNConflictReason.DELETED || reason == SVNConflictReason.REPLACED || reason == SVNConflictReason.OBSTRUCTED);
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.kind, BaseInfoField.revision, BaseInfoField.reposRelPath, BaseInfoField.reposRootUrl);
            SVNWCDbKind baseKind = baseInfo.kind;
            leftRevision = baseInfo.revision;
            leftReposRelpath = baseInfo.reposRelPath;
            reposRootUrl = baseInfo.reposRootUrl;
            if (baseKind == SVNWCDbKind.File || baseKind == SVNWCDbKind.Symlink)
                leftKind = SVNNodeKind.FILE;
            else if (baseKind == SVNWCDbKind.Dir)
                leftKind = SVNNodeKind.DIR;
            else {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL), SVNLogType.WC);
            }
        }
        assert (reposRootUrl.equals(myReposRootURL));
        File rightReposRelpath;
        if (mySwitchRelpath != null) {
            if (theirRelpath != null)
                rightReposRelpath = theirRelpath;
            else {
                rightReposRelpath = mySwitchRelpath;
                rightReposRelpath = SVNFileUtil.createFilePath(rightReposRelpath.getPath() + "_THIS_IS_INCOMPLETE");
            }
        } else {
            rightReposRelpath = (reason == SVNConflictReason.ADDED ? addedReposRelpath : leftReposRelpath);
            if (rightReposRelpath == null)
                rightReposRelpath = theirRelpath;
        }
        assert (rightReposRelpath != null);
        SVNNodeKind conflictNodeKind = (action == SVNConflictAction.DELETE ? leftKind : theirNodeKind);
        assert (conflictNodeKind == SVNNodeKind.FILE || conflictNodeKind == SVNNodeKind.DIR);
        SVNConflictVersion srcLeftVersion;
        if (leftReposRelpath == null) {
            srcLeftVersion = null;
        } else {
            srcLeftVersion = new SVNConflictVersion(reposRootUrl, leftReposRelpath.getPath(), leftRevision, leftKind);
        }
        SVNConflictVersion srcRightVersion = new SVNConflictVersion(reposRootUrl, rightReposRelpath.getPath(), myTargetRevision, theirNodeKind);
        return new SVNTreeConflictDescription(localAbspath, conflictNodeKind, action, reason, mySwitchRelpath != null ? SVNOperation.SWITCH : SVNOperation.UPDATE, srcLeftVersion, srcRightVersion);
    }

    private void checkPathUnderRoot(File localAbspath, String name) throws SVNException {
        if (SVNFileUtil.isWindows && localAbspath != null) {
            String path = name != null ? SVNFileUtil.createFilePath(localAbspath, name).toString() : localAbspath.toString();
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

    private void maybeBumpDirInfo(SVNBumpDirInfo bdi) throws SVNException {
        while (bdi != null) {
            if (--bdi.refCount > 0) {
                return;
            }
            if (!bdi.isSkipped()) {
                completeDirectory(bdi.getLocalAbspath(), bdi.getParent() == null);
            }
            bdi = bdi.getParent();
        }
        return;
    }

    private static class AccumulatedChangeInfo {

        public long changedRev;
        public SVNDate changedDate;
        public String changedAuthor;
    }

    private AccumulatedChangeInfo accumulateLastChange(File localAbspath, SVNProperties entryProps) throws SVNException {
        AccumulatedChangeInfo info = new AccumulatedChangeInfo();
        info.changedRev = SVNWCContext.INVALID_REVNUM;
        info.changedDate = null;
        info.changedAuthor = null;
        for (Iterator i = entryProps.nameSet().iterator(); i.hasNext();) {
            String propertyName = (String) i.next();
            String propertyValue = entryProps.getStringValue(propertyName);
            if (propertyValue == null) {
                continue;
            }
            if (SVNProperty.LAST_AUTHOR.equals(propertyName)) {
                info.changedAuthor = propertyValue;
            } else if (SVNProperty.COMMITTED_REVISION.equals(propertyName)) {
                info.changedRev = Long.valueOf(propertyValue);
            } else if (SVNProperty.COMMITTED_DATE.equals(propertyName)) {
                info.changedDate = SVNDate.parseDate(propertyValue);
            }
        }
        return info;
    }

    private File getNodeRelpathIgnoreErrors(File localAbspath) {
        SVNWCDbStatus status;
        File relpath = null;
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(localAbspath, InfoField.status, InfoField.reposRelPath);
            status = readInfo.status;
            relpath = readInfo.reposRelPath;
        } catch (SVNException e) {
            return null;
        }
        if (relpath != null) {
            return relpath;
        }
        if (status == SVNWCDbStatus.Added) {
            try {
                relpath = myWcContext.getDb().scanAddition(localAbspath, AdditionInfoField.reposRelPath).reposRelPath;
            } catch (SVNException e) {

            }
        } else if (status != SVNWCDbStatus.Deleted) {
            try {
                relpath = myWcContext.getDb().scanBaseRepository(localAbspath, RepositoryInfoField.relPath).relPath;
            } catch (SVNException e) {

            }
        }
        return relpath;
    }

    private static class UltimateBaseChecksumsInfo {

        public SVNChecksum sha1Checksum;
        public SVNChecksum md5Checksum;
    }

    private UltimateBaseChecksumsInfo getUltimateBaseChecksums(File localAbspath, boolean fetchSHA1, boolean fetchMD5) throws SVNException {
        UltimateBaseChecksumsInfo info = new UltimateBaseChecksumsInfo();
        SVNChecksum checksum = null;
        try {
            checksum = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.checksum).checksum;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                info.md5Checksum = null;
                info.sha1Checksum = null;
                return info;
            }
        }
        if (checksum.getKind() == SVNChecksumKind.SHA1) {
            info.sha1Checksum = checksum;
            if (fetchMD5) {
                info.md5Checksum = myWcContext.getDb().getPristineMD5(localAbspath, checksum);
            }
        } else {
            if (fetchSHA1) {
                info.sha1Checksum = myWcContext.getDb().getPristineSHA1(localAbspath, checksum);
            }
            info.md5Checksum = checksum;
        }
        return info;
    }

    private InputStream getUltimateBaseContents(File localAbspath) throws SVNException {
        SVNWCDbKind kind;
        SVNWCDbStatus status;
        SVNChecksum checksum;
        WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.checksum);
        status = baseInfo.status;
        kind = baseInfo.kind;
        checksum = baseInfo.checksum;
        if (kind != SVNWCDbKind.File) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_FILE, "Base node of ''{0}'' is not a file", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (status != SVNWCDbStatus.Normal) {
            assert (checksum == null);
            return null;
        }
        assert (checksum != null);
        return myWcContext.getDb().readPristine(localAbspath, checksum);
    }

    private static class MergeFileInfo {

        public SVNSkel workItems;
        public boolean installPristine;
        public File installFrom;
        public SVNStatusType contentState;
    }

    private MergeFileInfo mergeFile(SVNFileInfo fb, SVNChecksum newTextBaseSha1Checksum) throws SVNException {
        SVNDirectoryInfo pb = fb.getParentDir();
        boolean isLocallyModified;
        boolean isReplaced = false;
        boolean magicPropsChanged;
        SVNStatusType mergeOutcome = SVNStatusType.UNCHANGED;
        SVNSkel workItem;
        File newTextBaseTmpAbspath;
        boolean fileExists = true;
        SVNWCDbStatus status;
        boolean haveBase;
        long revision;
        String fileExternal = null;
        MergeFileInfo info = new MergeFileInfo();
        info.workItems = null;
        info.installPristine = false;
        info.installFrom = null;
        if (newTextBaseSha1Checksum != null) {
            newTextBaseTmpAbspath = myWcContext.getDb().getPristinePath(fb.getLocalAbspath(), newTextBaseSha1Checksum);
        } else {
            newTextBaseTmpAbspath = null;
        }
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(fb.getLocalAbspath(), InfoField.status, InfoField.revision, InfoField.haveBase);
            status = readInfo.status;
            revision = readInfo.revision;
            haveBase = readInfo.haveBase;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                fileExists = false;
                status = SVNWCDbStatus.NotPresent;
                revision = SVNWCContext.INVALID_REVNUM;
                haveBase = false;
            } else {
                throw e;
            }
        }
        if (fileExists) {
            fileExternal = myWcContext.getDb().getFileExternalTemp(fb.getLocalAbspath());
        }
        magicPropsChanged = myWcContext.hasMagicProperty(fb.getChangedProperties());
        if (fb.getCopiedWorkingText() != null) {
            isLocallyModified = true;
        } else if (fileExternal != null && status == SVNWCDbStatus.Added) {
            isLocallyModified = false;
        } else if (!fb.isObstructionFound()) {
            isLocallyModified = myWcContext.isTextModified(fb.getLocalAbspath(), false, false);
        } else if (newTextBaseSha1Checksum != null && !fb.isObstructionFound()) {
            InputStream pristineStream = myWcContext.getDb().readPristine(fb.getLocalAbspath(), newTextBaseSha1Checksum);
            isLocallyModified = modcheckVersionedFile(fb.getLocalAbspath(), pristineStream, false);
        } else {
            if (fb.isObstructionFound()) {
                isLocallyModified = true;
            } else {
                isLocallyModified = false;
            }
        }
        if (haveBase) {
            SVNWCDbStatus baseStatus;

            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(fb.getLocalAbspath(), BaseInfoField.status, BaseInfoField.revision);
            baseStatus = baseInfo.status;
            revision = baseInfo.revision;
            if (status == SVNWCDbStatus.Added && baseStatus != SVNWCDbStatus.NotPresent) {
                isReplaced = true;
            }
        }
        if (newTextBaseSha1Checksum != null) {
            if (isReplaced) {
            } else if (!isLocallyModified) {
                if (!fb.isDeleted()) {
                    info.installPristine = true;
                    if (fileExternal != null) {
                        assert (status == SVNWCDbStatus.Added || status == SVNWCDbStatus.Normal);
                    }
                }
            } else {
                SVNNodeKind wfileKind = SVNFileType.getNodeKind(SVNFileType.getType(fb.getLocalAbspath()));
                if (wfileKind == SVNNodeKind.NONE && !fb.isAddedWithHistory()) {
                    info.installPristine = true;
                } else if (!fb.isObstructionFound()) {
                    String oldrevStr, newrevStr, mineStr;
                    File mergeLeft;
                    boolean deleteLeft = false;
                    String pathExt = null;
                    if (myExtensionPatterns != null && myExtensionPatterns.length > 0) {
                        pathExt = SVNFileUtil.getFileExtension(fb.getLocalAbspath());
                        if (pathExt != null && !"".equals(pathExt)) {
                            boolean matches = false;
                            for (int i = 0; i < myExtensionPatterns.length; i++) {
                                String extPattern = myExtensionPatterns[i];
                                matches = DefaultSVNOptions.matches(extPattern, pathExt);
                                if (matches) {
                                    break;
                                }
                            }
                            if (!matches) {
                                pathExt = null;
                            }
                        }
                    }
                    if (fb.isAddedWithHistory()) {
                        oldrevStr = String.format(".copied%s%s", pathExt != null ? "." : "", pathExt != null ? pathExt : "");
                    } else {
                        long oldRev = revision;
                        if (!SVNRevision.isValidRevisionNumber(oldRev)) {
                            oldRev = 0;
                        }
                        oldrevStr = String.format(".r%d%s%s", oldRev, pathExt != null ? "." : "", pathExt != null ? pathExt : "");
                    }
                    newrevStr = String.format(".r%d%s%s", myTargetRevision, pathExt != null ? "." : "", pathExt != null ? pathExt : "");
                    mineStr = String.format(".mine%s%s", pathExt != null ? "." : "", pathExt != null ? pathExt : "");
                    if (fb.isAddExisted() && !isReplaced) {
                        mergeLeft = getEmptyTmpFile(pb.getLocalAbspath());
                        deleteLeft = true;
                    } else if (fb.getCopiedTextBaseSha1Checksum() != null) {
                        mergeLeft = myWcContext.getDb().getPristinePath(fb.getLocalAbspath(), fb.getCopiedTextBaseSha1Checksum());
                    } else {
                        mergeLeft = getUltimateBaseTextPathToRead(fb.getLocalAbspath());
                    }
                    SVNWCContext.MergeInfo mergeInfo = myWcContext.merge(mergeLeft, null, newTextBaseTmpAbspath, null, fb.getLocalAbspath(), fb.getCopiedWorkingText(), oldrevStr, newrevStr, mineStr,
                            false, null, fb.getChangedProperties());
                    workItem = mergeInfo.workItems;
                    mergeOutcome = mergeInfo.mergeOutcome;
                    info.workItems = myWcContext.wqMerge(info.workItems, workItem);
                    if (deleteLeft) {
                        workItem = myWcContext.wqBuildFileRemove(mergeLeft);
                        info.workItems = myWcContext.wqMerge(info.workItems, workItem);
                    }
                    if (fb.getCopiedWorkingText() != null) {
                        workItem = myWcContext.wqBuildFileRemove(fb.getCopiedWorkingText());
                        info.workItems = myWcContext.wqMerge(info.workItems, workItem);
                    }
                }
            }
        } else {
            Map keywords = myWcContext.getKeyWords(fb.getLocalAbspath(), null);
            if (magicPropsChanged || keywords != null) {
                File tmptext = myWcContext.getTranslatedFile(fb.getLocalAbspath(), fb.getLocalAbspath(), true, false, false, false);
                info.installPristine = true;
                info.installFrom = tmptext;
            }
        }
        if (!info.installPristine && !isLocallyModified && (fb.isAddingFile() || status == SVNWCDbStatus.Normal)) {
            SVNDate setDate = null;
            if (fb.getLastChangedDate() != null && !fb.isObstructionFound()) {
                setDate = SVNDate.parseDate(fb.getLastChangedDate());
            }

            workItem = myWcContext.wqBuildRecordFileinfo(fb.getLocalAbspath(), setDate);
            info.workItems = myWcContext.wqMerge(info.workItems, workItem);
        }
        if (mergeOutcome == SVNStatusType.CONFLICTED) {
            info.contentState = SVNStatusType.CONFLICTED;
        } else if (newTextBaseSha1Checksum != null) {
            if (isLocallyModified) {
                info.contentState = SVNStatusType.MERGED;
            } else {
                info.contentState = SVNStatusType.CHANGED;
            }
        } else {
            info.contentState = SVNStatusType.UNCHANGED;
        }
        return info;
    }

    private File getUltimateBaseTextPathToRead(File localAbspath) throws SVNException {
        File resultAbspath = getUltimateBaseTextPath(localAbspath);
        {
            SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(resultAbspath));
            if (kind != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "File ''{0}'' has no text base", localAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        return resultAbspath;
    }

    private File getUltimateBaseTextPath(File localAbspath) throws SVNException {
        SVNChecksum checksum = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.checksum).checksum;
        if (checksum == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Node ''{0}'' has no pristine base text", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return myWcContext.getDb().getPristinePath(localAbspath, checksum);
    }

    private File getEmptyTmpFile(File localAbspath) throws SVNException {
        File tempDirPath = myWcContext.getDb().getWCRootTempDir(localAbspath);
        return SVNFileUtil.createUniqueFile(tempDirPath, "tempfile", ".tmp", false).getAbsoluteFile();
    }

    private boolean modcheckVersionedFile(File versioned, InputStream pristineStream, boolean compareTextbases) throws SVNException {
        return myWcContext.compareAndVerify(versioned, pristineStream, compareTextbases, false);
    }

    private void completeDirectory(File localAbspath, boolean isRootDir) throws SVNException {
        if (isRootDir && myTargetBasename != null) {
            SVNWCDbStatus status;
            assert (localAbspath != null && localAbspath.equals(myAnchorAbspath));
            try {
                status = myWcContext.getDb().getBaseInfo(myTargetAbspath, BaseInfoField.status).status;
                if (status == SVNWCDbStatus.Excluded) {
                    doEntryDeletion(myTargetAbspath, null, false);
                }
                return;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                    throw e;
                }
            }
        }
        myWcContext.getDb().opSetBaseIncompleteTemp(localAbspath, false);
        if (myIsDepthSticky) {
            SVNDepth depth = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.depth).depth;
            if (depth != myRequestedDepth) {
                if (myRequestedDepth == SVNDepth.INFINITY || localAbspath.equals(myTargetAbspath) && myRequestedDepth.getId() > depth.getId()) {
                    myWcContext.getDb().opSetDirDepthTemp(localAbspath, myRequestedDepth);
                }
            }
        }
        List<String> children = myWcContext.getDb().getBaseChildren(localAbspath);
        for (String name : children) {
            File nodeAbspath = SVNFileUtil.createFilePath(localAbspath, name);
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(nodeAbspath, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.revision);
            SVNWCDbStatus status = baseInfo.status;
            SVNWCDbKind kind = baseInfo.kind;
            long revnum = baseInfo.revision;
            if (status == SVNWCDbStatus.NotPresent) {
                SVNConflictDescription treeConflict = myWcContext.getDb().opReadTreeConflict(nodeAbspath);
                if (treeConflict == null || treeConflict.getConflictReason() != SVNConflictReason.UNVERSIONED) {
                    myWcContext.getDb().removeBase(nodeAbspath);
                }
            } else if (status == SVNWCDbStatus.Absent && revnum != myTargetRevision) {
                myWcContext.getDb().removeBase(nodeAbspath);
            }
        }
    }

    private void doUpdateCleanup(File localAbspath, SVNDepth depth, File newReposRelpath, SVNURL newReposRootUrl, String newReposUuid, long newRevision, Set<File> excludePaths) throws SVNException {
        if (excludePaths.contains(localAbspath)) {
            return;
        }
        SVNWCDbStatus status = null;
        SVNWCDbKind kind = null;
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(localAbspath, InfoField.status, InfoField.kind);
            status = readInfo.status;
            kind = readInfo.kind;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
        }
        switch (status) {
            case Excluded:
            case Absent:
            case NotPresent:
                return;
            default:
                break;
        }
        if (kind == SVNWCDbKind.File || kind == SVNWCDbKind.Symlink) {
            tweakNode(localAbspath, kind, newReposRelpath, newReposRootUrl, newReposUuid, newRevision, false);
        } else if (kind == SVNWCDbKind.Dir) {
            tweakEntries(localAbspath, newReposRelpath, newReposRootUrl, newReposUuid, newRevision, depth, excludePaths);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unrecognized node kind: ''{0}''", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return;
    }

    private void tweakNode(File localAbspath, SVNWCDbKind kind, File newReposRelpath, SVNURL newReposRootUrl, String newReposUuid, long newRevision, boolean allowRemoval) throws SVNException {
        SVNWCDbStatus status = null;
        SVNWCDbKind dbKind = null;
        long revision = SVNWCContext.INVALID_REVNUM;
        File reposRelpath = null;
        SVNURL reposRootUrl = null;
        String reposUuid = null;
        boolean setReposRelpath = false;
        try {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(localAbspath, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.revision, BaseInfoField.reposRelPath,
                    BaseInfoField.reposRootUrl, BaseInfoField.reposUuid);
            status = baseInfo.status;
            dbKind = baseInfo.kind;
            revision = baseInfo.revision;
            reposRelpath = baseInfo.reposRelPath;
            reposRootUrl = baseInfo.reposRootUrl;
            reposUuid = baseInfo.reposUuid;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
        }
        assert (dbKind == kind);
        if (allowRemoval && (status == SVNWCDbStatus.NotPresent || (status == SVNWCDbStatus.Absent && revision != newRevision))) {
            myWcContext.getDb().opRemoveEntryTemp(localAbspath);
            return;
        }
        if (newReposRelpath != null) {
            if (reposRelpath == null) {
                WCDbRepositoryInfo reposInfo = myWcContext.getDb().scanBaseRepository(localAbspath, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl, RepositoryInfoField.uuid);
                reposRelpath = reposInfo.relPath;
                reposRootUrl = reposInfo.rootUrl;
                reposUuid = reposInfo.uuid;
            }
            if (!reposRelpath.equals(newReposRelpath)) {
                setReposRelpath = true;
            }
        }
        if (SVNRevision.isValidRevisionNumber(newRevision) && newRevision == revision) {
            newRevision = SVNWCContext.INVALID_REVNUM;
        }
        if (SVNRevision.isValidRevisionNumber(newRevision) || setReposRelpath) {
            myWcContext.getDb().opSetRevAndReposRelpathTemp(localAbspath, newRevision, setReposRelpath, newReposRelpath, reposRootUrl, reposUuid);
        }
        return;
    }

    private void tweakEntries(File dirAbspath, File newReposRelpath, SVNURL newReposRootUrl, String newReposUuid, long newRevision, SVNDepth depth, Set<File> excludePaths) throws SVNException {
        if (excludePaths.contains(dirAbspath)) {
            return;
        }
        tweakNode(dirAbspath, SVNWCDbKind.Dir, newReposRelpath, newReposRootUrl, newReposUuid, newRevision, false);
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        if (depth.getId() <= SVNDepth.EMPTY.getId()) {
            return;
        }
        List<String> children = myWcContext.getDb().getBaseChildren(dirAbspath);
        for (String childBaseName : children) {
            if (childBaseName == null || "".equals(childBaseName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL, "''{0}'' has empty childs", dirAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            File childAbspath;
            SVNWCDbKind kind;
            SVNWCDbStatus status;
            File childReposRelpath = null;
            boolean excluded;
            if (newReposRelpath != null) {
                childReposRelpath = SVNFileUtil.createFilePath(newReposRelpath, childBaseName);
            }
            childAbspath = SVNFileUtil.createFilePath(dirAbspath, childBaseName);
            excluded = excludePaths.contains(childAbspath);
            if (excluded) {
                continue;
            }
            WCDbInfo readInfo = myWcContext.getDb().readInfo(childAbspath, InfoField.status, InfoField.kind);
            status = readInfo.status;
            kind = readInfo.kind;
            if (kind == SVNWCDbKind.File || status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.Absent || status == SVNWCDbStatus.Excluded) {
                tweakNode(childAbspath, kind, childReposRelpath, newReposRootUrl, newReposUuid, newRevision, true);
            } else if ((depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES) && (kind == SVNWCDbKind.Dir)) {
                SVNDepth depthBelowHere = depth;
                if (depth == SVNDepth.IMMEDIATES) {
                    depthBelowHere = SVNDepth.EMPTY;
                }
                tweakEntries(childAbspath, childReposRelpath, newReposRootUrl, newReposUuid, newRevision, depthBelowHere, excludePaths);
            }
        }
        return;
    }
}
