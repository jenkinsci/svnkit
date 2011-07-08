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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumOutputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictedInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.MergeInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.TranslateInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.WritableBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
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
    private long targetRevision;
    private SVNDepth myRequestedDepth;
    private boolean myIsDepthSticky;
    private boolean myIsUseCommitTimes;
    private boolean rootOpened;
    private boolean myIsTargetDeleted;
    private boolean myIsUnversionedObstructionsAllowed;
    private File mySwitchRelpath;
    private SVNURL myReposRootURL;
    private String myReposUuid;
    private Set<File> mySkippedTrees = new HashSet<File>();
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNExternalsStore myExternalsStore;
    private DirectoryBaton myCurrentDirectory;

    private FileBaton myCurrentFile;

    private boolean myAddsAsModification;

    private Map<File, Map<String, SVNEntry>> myDirEntries;

    private boolean myIsCleanCheckout;

    public static ISVNUpdateEditor createUpdateEditor(SVNWCContext wcContext, File anchorAbspath, String target, SVNURL reposRoot, SVNURL switchURL, SVNExternalsStore externalsStore,
            boolean allowUnversionedObstructions, boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, boolean updateLocksOnDemand) throws SVNException {
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        WCDbInfo info = wcContext.getDb().readInfo(anchorAbspath, InfoField.status, InfoField.reposRootUrl, InfoField.reposUuid);

        /* ### For adds, REPOS_ROOT and REPOS_UUID would be NULL now. */
        if (info.status == SVNWCDbStatus.Added) {
            WCDbAdditionInfo addition = wcContext.getDb().scanAddition(anchorAbspath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid);
            info.reposRootUrl = addition.reposRootUrl;
            info.reposUuid = addition.reposUuid;
        }

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
        targetRevision = -1;
        myRequestedDepth = depth;
        myIsDepthSticky = depthIsSticky;
        myDeltaProcessor = new SVNDeltaProcessor();
        myExtensionPatterns = preservedExts;
//        myFileFetcher = fileFetcher;
        myTargetAbspath = anchorAbspath;
        myReposRootURL = reposRootUrl;
        myReposUuid = reposUuid;
//        myIsLockOnDemand = lockOnDemand;
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
        targetRevision = revision;
    }

    public long getTargetRevision() {
        return targetRevision;
    }

    private void rememberSkippedTree(File localAbspath) {
        assert (SVNFileUtil.isAbsolute(localAbspath));
        mySkippedTrees.add(localAbspath);
        return;
    }

    public void openRoot(long revision) throws SVNException {
        boolean alreadyConflicted;
        rootOpened = true;
        myCurrentDirectory = makeDirectoryBaton(null, null, false);
        try {
            alreadyConflicted = alreadyInATreeConflict(myCurrentDirectory.localAbsolutePath);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            alreadyConflicted = false;
        }
        if (alreadyConflicted) {
            myCurrentDirectory.skipThis = true;
            myCurrentDirectory.alreadyNotified = true;
            doNotification(myTargetAbspath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        
        if ("".equals(myTargetBasename) || myTargetBasename == null) {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(myCurrentDirectory.localAbsolutePath, BaseInfoField.changedAuthor, BaseInfoField.changedDate, BaseInfoField.changedRev, BaseInfoField.status, BaseInfoField.depth);
            
            myCurrentDirectory.ambientDepth = baseInfo.depth;
            myCurrentDirectory.wasIncomplete = baseInfo.status == SVNWCDbStatus.Incomplete;
            myCurrentDirectory.changedAuthor = baseInfo.changedAuthor;
            myCurrentDirectory.changedDate = baseInfo.changedDate;
            myCurrentDirectory.changedRevsion = baseInfo.changedRev;
            
            myWcContext.getDb().opStartDirectoryUpdateTemp(myCurrentDirectory.localAbsolutePath, myCurrentDirectory.newRelativePath, targetRevision);
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
        while (true && ancestorAbspath != null) {
            SVNConflictDescription conflict;
            WCDbInfo readInfo = myWcContext.getDb().readInfo(ancestorAbspath, InfoField.conflicted);
            if (readInfo.conflicted) {
                conflict = myWcContext.getDb().opReadTreeConflict(ancestorAbspath);
                if (conflict != null) {
                    return true;
                }
            }
            if (myWcContext.getDb().isWCRoot(ancestorAbspath)) {
                break;
            }
            ancestorAbspath = SVNFileUtil.getParentFile(ancestorAbspath);
        }
        return conflicted;
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        if (myCurrentDirectory.skipThis) {
            return;
        }
        myCurrentDirectory.markEdited();
        String base = SVNFileUtil.getFileName(SVNFileUtil.createFilePath(path));
        File localAbsPath = SVNFileUtil.createFilePath(myCurrentDirectory.localAbsolutePath, base);
        
        boolean isRoot = myWcContext.getDb().isWCRoot(localAbsPath);
        if (isRoot) {
            rememberSkippedTree(localAbsPath);
            doNotification(localAbsPath, SVNNodeKind.UNKNOWN, SVNEventAction.UPDATE_OBSTRUCTION);
            return;
        }       
        
        boolean deletingTarget = localAbsPath.equals(myTargetAbspath);
        SVNWCDbStatus baseStatus;
        SVNWCDbKind baseKind;
        
        WCDbInfo info = myWcContext.getDb().readInfo(localAbsPath, InfoField.status, InfoField.kind, InfoField.reposRelPath, InfoField.conflicted, InfoField.haveBase, InfoField.haveWork);
       
        if (!info.haveWork) {
            baseStatus = info.status;
            baseKind = info.kind;
        } else {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(localAbsPath, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.reposRelPath);
            baseStatus = baseInfo.status;
            baseKind = baseInfo.kind;
            info.reposRelPath = baseInfo.reposRelPath;
        }
        
        if (info.conflicted) {
            info.conflicted = isNodeAlreadyConflicted(localAbsPath);
        }
        if (info.conflicted) {
            rememberSkippedTree(localAbsPath);
            doNotification(localAbsPath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        
        if (baseStatus == SVNWCDbStatus.NotPresent || baseStatus == SVNWCDbStatus.Excluded || baseStatus == SVNWCDbStatus.ServerExcluded) {
            myWcContext.getDb().removeBase(localAbsPath);
            if (deletingTarget) {
                myIsTargetDeleted = true;
            }
            return;
        }
     
        SVNTreeConflictDescription treeConflict = null;
        if (!myCurrentDirectory.shadowed) {
            treeConflict = checkTreeConflict(localAbsPath, info.status, info.kind, true, SVNConflictAction.DELETE, SVNNodeKind.NONE, info.reposRelPath);
        }
        if (treeConflict != null) {
            if (myCurrentDirectory.deletionConflicts == null) {
                myCurrentDirectory.deletionConflicts = new HashMap<String, SVNTreeConflictDescription>();
            }
            myCurrentDirectory.deletionConflicts.put(base, treeConflict);
            myWcContext.getDb().opSetTreeConflict(localAbsPath, treeConflict);
            doNotification(localAbsPath, SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
            
            if (treeConflict.getConflictReason() == SVNConflictReason.EDITED) {
                myWcContext.getDb().opMakeCopyTemp(localAbsPath, false);
            } else if (treeConflict.getConflictReason() == SVNConflictReason.DELETED || treeConflict.getConflictReason() == SVNConflictReason.REPLACED) {
                // skip
            } else {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL), SVNLogType.WC);
            }
        }
        SVNSkel workItem;
        if (!deletingTarget) {
            workItem = myWcContext.wqBuildBaseRemove(localAbsPath, -1, SVNWCDbKind.Unknown);
        } else {
            workItem = myWcContext.wqBuildBaseRemove(localAbsPath, targetRevision, baseKind);
            myIsTargetDeleted = true;
        }
        myWcContext.getDb().addWorkQueue(localAbsPath, workItem);
        myWcContext.wqRun(localAbsPath);
        
        if (treeConflict == null) {
            SVNEventAction action = SVNEventAction.UPDATE_DELETE;
            if (myCurrentDirectory.shadowed) {
                // TODO shadow deletion event.
                action = SVNEventAction.UPDATE_UPDATE_DELETED;
            }
            doNotification(localAbsPath, info.kind == SVNWCDbKind.Dir ? SVNNodeKind.DIR : SVNNodeKind.FILE, action);
        }
        
    }

    private boolean isNodeAlreadyConflicted(File localAbspath) throws SVNException {
        List<SVNConflictDescription> conflicts = myWcContext.getDb().readConflicts(localAbspath);
        for (SVNConflictDescription cd : conflicts) {
            if (cd.isTreeConflict()) {
                return true;
            } else if (cd.isPropertyConflict() || cd.isTextConflict()) {
                ConflictedInfo info = myWcContext.getConflicted(localAbspath, true, true, true);
                return (info.textConflicted || info.propConflicted || info.treeConflicted);
            }
        }
        return false;
    }

    private SVNTreeConflictDescription checkTreeConflict(File localAbspath, SVNWCDbStatus workingStatus, SVNWCDbKind workingKind, boolean existsInRepos, SVNConflictAction action, SVNNodeKind theirNodeKind, File theirRelpath) throws SVNException {
        SVNConflictReason reason = null;
        boolean locally_replaced = false;
        boolean modified = false;
        boolean all_mods_are_deletes = false;
        switch (workingStatus) {
            case Added:
            case MovedHere:
            case Copied:
                if (existsInRepos) {
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
                if (action == SVNConflictAction.EDIT) {
                    return null;
                }
                switch (workingKind) {
                    case File:
                    case Symlink:
                        all_mods_are_deletes = false;
                        modified = hasEntryLocalMods(localAbspath, workingKind);
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

            case ServerExcluded:
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
        return createTreeConflict(localAbspath, reason, action, theirNodeKind, theirRelpath);
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
        modInfo.allModsAreDeletes = true;
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
/*
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
*/
    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind nodeKind) throws SVNException {
        if (myCurrentDirectory.skipThis) {
            return;
        }
        String name = SVNPathUtil.tail(path);
        SVNWCDbKind absentKind = nodeKind == SVNNodeKind.DIR ? SVNWCDbKind.Dir : SVNWCDbKind.File;
        myCurrentDirectory.markEdited();
        File localAbsPath = SVNFileUtil.createFilePath(myCurrentDirectory.localAbsolutePath, name);
        
        WCDbInfo info = null;
        SVNWCDbStatus status = null;
        SVNWCDbKind kind = null;
        try {
            info = myWcContext.getDb().readInfo(localAbsPath, InfoField.status, InfoField.kind);
            status = info.status;
            kind = info.kind;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            status = SVNWCDbStatus.NotPresent;
            kind = SVNWCDbKind.Unknown;
        }
        if (status == SVNWCDbStatus.Normal && kind == SVNWCDbKind.Dir) {
            // ok
        } else if (status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.ServerExcluded || status == SVNWCDbStatus.Excluded) {
            // ok
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        File reposRelPath = SVNFileUtil.createFilePath(myCurrentDirectory.newRelativePath, name);
        myWcContext.getDb().addBaseExcludedNode(localAbsPath, reposRelPath, myReposRootURL, myReposUuid, targetRevision, absentKind, SVNWCDbStatus.ServerExcluded, null, null);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        assert ((copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision)) || (copyFromPath == null && !SVNRevision.isValidRevisionNumber(copyFromRevision)));
        DirectoryBaton pb = myCurrentDirectory;
        myCurrentDirectory = makeDirectoryBaton(path, myCurrentDirectory, true);
        DirectoryBaton db = myCurrentDirectory;
        if (db.skipThis) {
            return;
        }
        db.markEdited();
        
        if (myTargetAbspath.equals(db.localAbsolutePath)) {
            db.ambientDepth = (myRequestedDepth == SVNDepth.UNKNOWN) ? SVNDepth.INFINITY : myRequestedDepth;
        } else if (myRequestedDepth == SVNDepth.IMMEDIATES || (myRequestedDepth == SVNDepth.UNKNOWN && pb.ambientDepth == SVNDepth.IMMEDIATES)) {
            db.ambientDepth = SVNDepth.EMPTY;
        } else {
            db.ambientDepth = SVNDepth.INFINITY;
        }
        if (SVNFileUtil.getAdminDirectoryName().equals(db.name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name as the administrative directory",
                    db.localAbsolutePath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(db.localAbsolutePath));
        SVNWCDbStatus status;
        SVNWCDbKind wc_kind;
        boolean conflicted;
        boolean versionedLocallyAndPresent;
        boolean error = false;
        try {
            WCDbInfo readInfo = myWcContext.getDb().readInfo(db.localAbsolutePath, InfoField.status, InfoField.kind, InfoField.conflicted);
            status = readInfo.status;
            wc_kind = readInfo.kind;
            conflicted = readInfo.conflicted;
            versionedLocallyAndPresent = isNodePresent(status);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            error = true;
            wc_kind = SVNWCDbKind.Unknown;
            status = SVNWCDbStatus.Normal;
            conflicted = false;
            versionedLocallyAndPresent = false;
        }
        if (!error) {
            if (wc_kind == SVNWCDbKind.Dir && status == SVNWCDbStatus.Normal) {
                myWcContext.getDb().addBaseNotPresentNode(db.localAbsolutePath, db.newRelativePath, myReposRootURL, myReposUuid, targetRevision, SVNWCDbKind.File, null, null);
                rememberSkippedTree(db.localAbsolutePath);
                db.skipThis = true;
                db.alreadyNotified = true;
                doNotification(db.localAbsolutePath, SVNNodeKind.DIR, SVNEventAction.UPDATE_OBSTRUCTION);
                return;
            } else if (status == SVNWCDbStatus.Normal && (wc_kind == SVNWCDbKind.File ||  wc_kind == SVNWCDbKind.Symlink)) {
                rememberSkippedTree(db.localAbsolutePath);
                db.skipThis = true;
                db.alreadyNotified = true;
                doNotification(db.localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.UPDATE_OBSTRUCTION);
                return;
            } else if (wc_kind == SVNWCDbKind.Unknown) {
                versionedLocallyAndPresent = false;
            } else {
                versionedLocallyAndPresent = isNodePresent(status);
            }
        }
        SVNTreeConflictDescription treeConflict = null;
        if (conflicted) {
            if (pb.deletionConflicts != null) {
                treeConflict = pb.deletionConflicts.get(db.name);
            }
            if (treeConflict != null) {
                treeConflict.setConflictAction(SVNConflictAction.REPLACE);
                myWcContext.getDb().opSetTreeConflict(db.localAbsolutePath, treeConflict);
                treeConflict = null;
                db.shadowed = true;
                conflicted = false;
            } else {
                conflicted = isNodeAlreadyConflicted(db.localAbsolutePath);                
            }
        }        
        if (conflicted) {
            rememberSkippedTree(db.localAbsolutePath);
            db.skipThis = true;
            db.alreadyNotified = true;
            myWcContext.getDb().addBaseNotPresentNode(db.localAbsolutePath, db.newRelativePath, myReposRootURL, myReposUuid, targetRevision, SVNWCDbKind.Dir, null, null);
            doNotification(db.localAbsolutePath, SVNNodeKind.DIR, SVNEventAction.SKIP);
            return;
        }
        if (db.shadowed) {
            
        } else if (versionedLocallyAndPresent) {
            SVNWCDbStatus addStatus = SVNWCDbStatus.Normal;
            if (status == SVNWCDbStatus.Added) {
                addStatus = myWcContext.getDb().scanAddition(db.localAbsolutePath, AdditionInfoField.status).status;
            }
            boolean localIsNonDir = wc_kind != SVNWCDbKind.Dir && status != SVNWCDbStatus.Deleted;  
            if (!myAddsAsModification || localIsNonDir || addStatus != SVNWCDbStatus.Added) {
                treeConflict = checkTreeConflict(db.localAbsolutePath, status, wc_kind, false, SVNConflictAction.ADD, SVNNodeKind.DIR, db.newRelativePath);
            }
            if (treeConflict == null) {
                db.addExisted = true;
            } else {
                db.shadowed = true;
            }
        } else if (kind != SVNNodeKind.NONE) {
            db.obstructionFound = true;
            if (!(kind == SVNNodeKind.DIR && myIsUnversionedObstructionsAllowed)) {
                db.shadowed = true;
                treeConflict = createTreeConflict(db.localAbsolutePath, SVNConflictReason.UNVERSIONED, SVNConflictAction.ADD, SVNNodeKind.DIR, db.newRelativePath);
            }
        }
        myWcContext.getDb().opSetNewDirToIncompleteTemp(db.localAbsolutePath, db.newRelativePath, myReposRootURL, myReposUuid, targetRevision, db.ambientDepth);
        if (!db.shadowed) {
            SVNFileUtil.ensureDirectoryExists(db.localAbsolutePath);
        }
        if (!db.shadowed && status == SVNWCDbStatus.Added) {
            myWcContext.getDb().opRemoveWorkingTemp(db.localAbsolutePath);
        }
        if (db.shadowed && db.obstructionFound) {
            myWcContext.getDb().opDelete(db.localAbsolutePath);
        }
        if (treeConflict != null) {
            myWcContext.getDb().opSetTreeConflict(db.localAbsolutePath, treeConflict);
            db.alreadyNotified = true;
            doNotification(db.localAbsolutePath, SVNNodeKind.DIR, SVNEventAction.TREE_CONFLICT);
        }
        
        if (myWcContext.getEventHandler() != null && !db.alreadyNotified && !db.addExisted) {
            SVNEventAction action;
            if (db.shadowed) {
                action = SVNEventAction.UPDATE_ADD_DELETED;
            } else if (db.obstructionFound) {
                action = SVNEventAction.UPDATE_EXISTS;
            } else {
                action = SVNEventAction.UPDATE_ADD;
            }
            db.alreadyNotified = true;
            doNotification(db.localAbsolutePath, SVNNodeKind.DIR, action);
            
        }
        return;
    }

    public void openDir(String path, long revision) throws SVNException {
        DirectoryBaton pb = myCurrentDirectory;
        myCurrentDirectory = makeDirectoryBaton(path, pb, false);
        DirectoryBaton db = myCurrentDirectory;
        if (db.skipThis) {
            return;
        }
        
        boolean isWCRoot = myWcContext.getDb().isWCRoot(db.localAbsolutePath);
        if (isWCRoot) {
            rememberSkippedTree(db.localAbsolutePath);
            db.skipThis = true;
            db.alreadyNotified = true;
            doNotification(db.localAbsolutePath, SVNNodeKind.DIR, SVNEventAction.UPDATE_OBSTRUCTION);
            return;
        }
        
        myWcContext.writeCheck(db.localAbsolutePath);
        WCDbInfo readInfo = myWcContext.getDb().readInfo(db.localAbsolutePath, InfoField.status, InfoField.kind, InfoField.revision, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor, InfoField.depth, InfoField.haveWork, InfoField.conflicted, InfoField.haveWork);
        SVNWCDbStatus status = readInfo.status;
        SVNWCDbKind wcKind = readInfo.kind;
        db.oldRevision = readInfo.revision;
        db.ambientDepth = readInfo.depth;
        db.changedRevsion = readInfo.changedRev;
        db.changedAuthor = readInfo.changedAuthor;
        db.changedDate = readInfo.changedDate;
        
        boolean have_work = readInfo.haveWork;
        boolean conflicted = readInfo.conflicted;
        
        SVNTreeConflictDescription treeConflict = null;
        SVNWCDbStatus baseStatus;
        if (!have_work) {
            baseStatus = status;
        } else {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(db.localAbsolutePath, BaseInfoField.status, BaseInfoField.revision, 
                    BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor, BaseInfoField.depth);
            baseStatus = baseInfo.status;
            db.oldRevision = baseInfo.revision;
            db.ambientDepth = baseInfo.depth;
            db.changedAuthor = baseInfo.changedAuthor;
            db.changedDate = baseInfo.changedDate;
            db.changedRevsion = baseInfo.changedRev;
        }
        db.wasIncomplete  = baseStatus == SVNWCDbStatus.Incomplete;
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(db.localAbsolutePath);
        }
        
        if (conflicted) {
            rememberSkippedTree(db.localAbsolutePath);
            db.skipThis = true;
            db.alreadyNotified = true;
            doNotification(db.localAbsolutePath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        if (!db.shadowed) {
            treeConflict = checkTreeConflict(db.localAbsolutePath, status, wcKind, true, SVNConflictAction.EDIT, SVNNodeKind.DIR, db.newRelativePath);
        }
        if (treeConflict != null) {
            db.editConflict = treeConflict;
            db.shadowed = true;
        }
        myWcContext.getDb().opStartDirectoryUpdateTemp(db.localAbsolutePath, db.newRelativePath, targetRevision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentDirectory.skipThis) {
            return;
        }
        // TODO use String pool for property names and some of the values.
        if (SVNProperty.isRegularProperty(name)) {
            if (myCurrentDirectory.regularPropChanges == null) {
                myCurrentDirectory.regularPropChanges = new SVNProperties();
            }
            myCurrentDirectory.regularPropChanges.put(name, value);
        } else if (SVNProperty.isEntryProperty(name)) {
            if (myCurrentDirectory.entryPropChanges == null) {
                myCurrentDirectory.entryPropChanges = new SVNProperties();
            }
            myCurrentDirectory.entryPropChanges.put(name, value);
        } else if (SVNProperty.isWorkingCopyProperty(name)) {
            if (myCurrentDirectory.davPropChanges == null) {
                myCurrentDirectory.davPropChanges = new SVNProperties();
            }
            myCurrentDirectory.davPropChanges.put(name, value);
        }
        if (!myCurrentDirectory.edited && SVNProperty.isRegularProperty(name)) {
            myCurrentDirectory.markEdited();
        }
    }

    public void closeDir() throws SVNException {
        DirectoryBaton db = myCurrentDirectory;
        if (db.skipThis) {
            maybeBumpDirInfo(db.bumpInfo);
            myCurrentDirectory = myCurrentDirectory.parentBaton;
            return;
        }
        SVNSkel allWorkItems = null;
        SVNProperties entryProps = db.entryPropChanges;
        SVNProperties davProps = db.davPropChanges;
        SVNProperties regularProps = db.regularPropChanges;
        
        SVNProperties actualProps = null;
        SVNProperties baseProps = null;
        
        if ((!db.addingDir || db.addExisted) && !db.shadowed) {
            actualProps = myWcContext.getActualProps(db.localAbsolutePath);
        } else {
            actualProps = new SVNProperties();
        }
        
        if (db.addExisted) {
            baseProps = myWcContext.getPristineProps(db.localAbsolutePath);
        } else if (!db.addingDir) {
            baseProps = myWcContext.getDb().getBaseProps(db.localAbsolutePath);
        } else {
            baseProps = new SVNProperties();
        }

        SVNStatusType[] propStatus = new SVNStatusType[] {SVNStatusType.UNKNOWN};
        SVNProperties newBaseProps = null;
        SVNProperties newActualProps = null;
        long newChangedRev = -1;
        SVNDate newChangedDate = null;
        String newChangedAuthor = null;
        
        if (db.wasIncomplete) {
            SVNProperties propertiesToDelete = new SVNProperties(baseProps);
            if (regularProps == null) {
                regularProps = new SVNProperties();
            }
            for (Iterator<?> names = regularProps.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                propertiesToDelete.remove(name);
            }
            for (Iterator<?> names = propertiesToDelete.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                regularProps.put(name, SVNPropertyValue.create(null));
            }
        }
        if (regularProps != null && regularProps.size() > 0) {
            if (myExternalsStore != null) {
                if (regularProps.containsName(SVNProperty.EXTERNALS)) {
                    String newValue = regularProps.getStringValue(SVNProperty.EXTERNALS);
                    String oldValue = baseProps.getStringValue(SVNProperty.EXTERNALS);
                    if (oldValue == null && newValue == null)
                        ;
                    else if (oldValue != null && newValue != null && oldValue.equals(newValue))
                        ;
                    else if (oldValue != null || newValue != null) {
                        myExternalsStore.addExternal(db.localAbsolutePath, oldValue, newValue);
                        myExternalsStore.addDepth(db.localAbsolutePath, db.ambientDepth);
                    }
                }
            }
            if (db.shadowed) {
                if (db.addingDir) {
                    actualProps = new SVNProperties();
                } else {
                    actualProps = baseProps;
                }
            }
            SVNSkel workItem = myWcContext.mergeProperties2(propStatus, newBaseProps, newActualProps, db.localAbsolutePath, SVNWCDbKind.Dir, null, null, null, baseProps, actualProps, regularProps, true, false);
            allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);            
        }
        AccumulatedChangeInfo change = accumulateLastChange(db.localAbsolutePath, entryProps);
        newChangedRev = change.changedRev;
        newChangedDate = change.changedDate;
        newChangedAuthor = change.changedAuthor;
        
        Map<String, SVNEntry> newChildren = myDirEntries != null ? myDirEntries.get(db.newRelativePath) : null;
        if (newChildren != null) {
            for(String childName : newChildren.keySet()) {
                SVNEntry childEntry = newChildren.get(childName);
                File childAbsPath = SVNFileUtil.createFilePath(db.localAbsolutePath, childName);
                if (db.ambientDepth.compareTo(SVNDepth.IMMEDIATES) < 0 && childEntry.getKind() == SVNNodeKind.DIR) {
                    continue;
                }
                try {
                    myWcContext.getDb().getBaseInfo(childAbsPath, BaseInfoField.status);
                    if (!myWcContext.getDb().isWCRoot(childAbsPath)) {
                        continue;
                    }
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                        throw e;
                    }
                }
                File childRelPath = SVNFileUtil.createFilePath(db.newRelativePath, childName);
                SVNWCDbKind childKind = childEntry.getKind() == SVNNodeKind.DIR ? SVNWCDbKind.Dir : SVNWCDbKind.File; 
                myWcContext.getDb().addBaseNotPresentNode(childAbsPath, childRelPath, myReposRootURL, myReposUuid, targetRevision, childKind, null, null);
            }
        }
        
        if (db.notPresentFiles != null && db.notPresentFiles.size() > 0) {
            for(String fileName : db.notPresentFiles) {
                File childAbsPath = SVNFileUtil.createFilePath(db.localAbsolutePath, fileName);
                File childRelPath = SVNFileUtil.createFilePath(db.newRelativePath, fileName);
                
                myWcContext.getDb().addBaseNotPresentNode(childAbsPath, childRelPath, myReposRootURL, myReposUuid, targetRevision, SVNWCDbKind.File, null, null);
            }
        }
        
        if (db.parentBaton == null && (!"".equals(myTargetBasename) && myTargetBasename != null)) {
            // there should be no prop changes here.
        } else {
            if (newChangedRev >= 0) {
                db.changedRevsion = newChangedRev;                
            }
            if (newChangedDate != null && newChangedDate.getTime() != 0) {
                db.changedDate = newChangedDate;
            }
            if (newChangedAuthor != null) {
                db.changedAuthor = newChangedAuthor;
            }
            if (db.ambientDepth == SVNDepth.UNKNOWN) {
                db.ambientDepth = SVNDepth.INFINITY;
            }
            if (myIsDepthSticky && db.ambientDepth != myRequestedDepth) {
                if (myRequestedDepth == SVNDepth.INFINITY || (db.localAbsolutePath.equals(myTargetAbspath) && myRequestedDepth.compareTo(db.ambientDepth) > 0)) {
                    db.ambientDepth = myRequestedDepth;
                }
            }
            SVNProperties props = newBaseProps;
            if (props == null) {
                props = baseProps;
            }
            myWcContext.getDb().addBaseDirectory(db.localAbsolutePath, db.newRelativePath, myReposRootURL, myReposUuid, targetRevision, props, db.changedRevsion, db.changedDate, db.changedAuthor, null, db.ambientDepth, 
                    davProps != null && !davProps.isEmpty() ? davProps : null, null, !db.shadowed && newBaseProps != null, newActualProps, allWorkItems);
            
        }
        myWcContext.wqRun(db.localAbsolutePath);
        if (!db.alreadyNotified && myWcContext.getEventHandler() != null && db.edited) {
            SVNEventAction action = null;
            if (db.shadowed) {
                action = SVNEventAction.UPDATE_UPDATE_DELETED;
            } else if (db.obstructionFound || db.addExisted) {
                action = SVNEventAction.UPDATE_EXISTS;
            } else {
                action = SVNEventAction.UPDATE_UPDATE;
            }
            SVNEvent event = new SVNEvent(db.localAbsolutePath, SVNNodeKind.DIR, null, targetRevision, null, propStatus[0], null, null, action, null, null, null, null);
            event.setPreviousRevision(db.oldRevision);
            myWcContext.getEventHandler().handleEvent(event, 0);
        }            
        maybeBumpDirInfo(db.bumpInfo);
        myCurrentDirectory = myCurrentDirectory.parentBaton;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        assert ((copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision)) || (copyFromPath == null && !SVNRevision.isValidRevisionNumber(copyFromRevision)));
        DirectoryBaton pb = myCurrentDirectory;
        FileBaton fb = makeFileBaton(pb, path, true);
        myCurrentFile = fb;
        if (fb.skipThis) {
            return;
        }
        fb.markEdited();
        if (SVNFileUtil.getAdminDirectoryName().equals(fb.name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'' : object of the same name as the administrative directory",
                    fb.localAbsolutePath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        
        SVNNodeKind kind = SVNNodeKind.NONE;
        SVNWCDbKind wcKind = SVNWCDbKind.Unknown;;
        SVNWCDbStatus status = SVNWCDbStatus.Normal;
        boolean conflicted = false;
        boolean versionedLocallyAndPresent = false;
        boolean error = false;
        SVNTreeConflictDescription treeConflict = null;

        if (!myIsCleanCheckout) {
            try {
                WCDbInfo readInfo = myWcContext.getDb().readInfo(fb.localAbsolutePath, InfoField.status, InfoField.kind, InfoField.conflicted);
                status = readInfo.status;
                wcKind = readInfo.kind;
                conflicted = readInfo.conflicted;
                versionedLocallyAndPresent = isNodePresent(status);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                    throw e;
                }
                error = true;
                wcKind = SVNWCDbKind.Unknown;
                conflicted = false;
                versionedLocallyAndPresent = false;
            }            
        }
        
        if (!error) {
            if (wcKind == SVNWCDbKind.Dir && status == SVNWCDbStatus.Normal) {
                if (pb.notPresentFiles == null) {
                    pb.notPresentFiles = new HashSet<String>();
                }
                pb.notPresentFiles.add(fb.name);
                rememberSkippedTree(fb.localAbsolutePath);
                fb.skipThis = true;
                fb.alreadyNotified = true;
                doNotification(fb.localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.UPDATE_OBSTRUCTION);
                return;
            } else if (status == SVNWCDbStatus.Normal && (wcKind == SVNWCDbKind.File || wcKind == SVNWCDbKind.Symlink)) {
                rememberSkippedTree(fb.localAbsolutePath);
                fb.skipThis = true;
                fb.alreadyNotified = true;
                doNotification(fb.localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.UPDATE_OBSTRUCTION);
            } else if (wcKind == SVNWCDbKind.Unknown) {
                versionedLocallyAndPresent = false;
            } else {
                versionedLocallyAndPresent = isNodePresent(status);
            }
        }
        
        if (conflicted) {
            if (pb.deletionConflicts != null) {
                treeConflict = pb.deletionConflicts.get(fb.name);
            }
            if (treeConflict != null) {
                treeConflict.setConflictAction(SVNConflictAction.REPLACE);
                myWcContext.getDb().opSetTreeConflict(fb.localAbsolutePath, treeConflict);
                treeConflict = null;
                fb.shadowed = true;
                conflicted = false;
            } else {
                conflicted = isNodeAlreadyConflicted(fb.localAbsolutePath);
            }
        }
        
        if (conflicted) {
            rememberSkippedTree(fb.localAbsolutePath);
            fb.skipThis = true;
            fb.alreadyNotified = true;
            if (pb.notPresentFiles == null) {
                pb.notPresentFiles = new HashSet<String>();
            }
            pb.notPresentFiles.add(fb.name);
            doNotification(fb.localAbsolutePath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        
        if (fb.shadowed) {
            //
        } else if (versionedLocallyAndPresent) {
            boolean localIsFile = false;
            if (status == SVNWCDbStatus.Added) {
                status = myWcContext.getDb().scanAddition(fb.localAbsolutePath, AdditionInfoField.status).status;
            }
            localIsFile = wcKind == SVNWCDbKind.File || wcKind == SVNWCDbKind.Symlink;
            if (!myAddsAsModification || !localIsFile || status != SVNWCDbStatus.Added) {
                treeConflict = checkTreeConflict(fb.localAbsolutePath, status, wcKind, false, SVNConflictAction.ADD, SVNNodeKind.FILE, fb.newRelativePath);
            }
            if (treeConflict == null) {
                fb.addExisted = true;
            } else {
                fb.shadowed = true;
            }
        } else if (kind != SVNNodeKind.NONE) {
            fb.obstructionFound = true;
            if (!(kind == SVNNodeKind.FILE && myIsUnversionedObstructionsAllowed)) {
                fb.shadowed = true;
                treeConflict = createTreeConflict(fb.localAbsolutePath, SVNConflictReason.UNVERSIONED, SVNConflictAction.ADD, SVNNodeKind.FILE, fb.newRelativePath);
                
                assert (treeConflict != null);
            }        
        }
        return;
    }

    public void openFile(String path, long revision) throws SVNException {
        DirectoryBaton pb = myCurrentDirectory;
        FileBaton fb = makeFileBaton(pb, path, false);
        myCurrentFile = fb;
        if (fb.skipThis) {
            return;
        }
        boolean isRoot = myWcContext.getDb().isWCRoot(fb.localAbsolutePath);
        if (isRoot) {
            rememberSkippedTree(fb.localAbsolutePath);
            fb.skipThis = true;
            fb.alreadyNotified = true;
            doNotification(fb.localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.UPDATE_OBSTRUCTION);
            return;
        }
        boolean conflicted = false;
        SVNWCDbStatus status;
        SVNWCDbKind kind = SVNWCDbKind.Unknown;
        SVNTreeConflictDescription treeConflict = null;
        
        WCDbInfo readInfo = myWcContext.getDb().readInfo(fb.localAbsolutePath, InfoField.status, InfoField.kind,
                InfoField.revision, InfoField.changedRev, InfoField.changedDate, InfoField.changedRev,
                InfoField.checksum, InfoField.haveWork, InfoField.conflicted);
        status = readInfo.status;
        fb.changedAuthor = readInfo.changedAuthor;
        fb.changedDate = readInfo.changedDate;
        fb.changedRevison = readInfo.changedRev;
        fb.oldRevision = readInfo.revision;
        fb.originalChecksum = readInfo.checksum;
        conflicted = readInfo.conflicted;
        
        if (readInfo.haveWork) {
            WCDbBaseInfo baseInfo = myWcContext.getDb().getBaseInfo(fb.localAbsolutePath, BaseInfoField.revision, 
                    BaseInfoField.changedRev, BaseInfoField.changedAuthor, BaseInfoField.changedDate,
                    BaseInfoField.checksum);
            fb.changedAuthor = baseInfo.changedAuthor;
            fb.changedDate = baseInfo.changedDate;
            fb.changedRevison = baseInfo.changedRev;
            fb.oldRevision = baseInfo.revision;
            fb.originalChecksum = baseInfo.checksum;
        }
        if (conflicted) {
            conflicted = isNodeAlreadyConflicted(fb.localAbsolutePath);
        }
        if (conflicted ) {
            rememberSkippedTree(fb.localAbsolutePath);
            fb.skipThis  = true;
            fb.alreadyNotified = true;
            doNotification(fb.localAbsolutePath, SVNNodeKind.UNKNOWN, SVNEventAction.SKIP);
            return;
        }
        fb.shadowed = pb.shadowed;
        if (!fb.shadowed) {
            treeConflict = checkTreeConflict(fb.localAbsolutePath, status, kind, true, SVNConflictAction.EDIT, SVNNodeKind.FILE, fb.newRelativePath);
        }
        if (treeConflict != null) {
            fb.editConflict = treeConflict;
            fb.shadowed = true;
        }
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        FileBaton fb = myCurrentFile;
        if (fb.skipThis) {
            return;
        }
        if (SVNProperty.isRegularProperty(propertyName)) {
            if (myCurrentFile.regularPropChanges == null) {
                myCurrentFile.regularPropChanges = new SVNProperties();
            }
            myCurrentFile.regularPropChanges.put(propertyName, propertyValue);
        } else if (SVNProperty.isEntryProperty(propertyName)) {
            if (myCurrentFile.entryPropChanges == null) {
                myCurrentFile.entryPropChanges = new SVNProperties();
            }
            myCurrentFile.entryPropChanges.put(propertyName, propertyValue);
        } else if (SVNProperty.isWorkingCopyProperty(propertyName)) {
            if (myCurrentFile.davPropChanges == null) {
                myCurrentFile.davPropChanges = new SVNProperties();
            }
            myCurrentFile.davPropChanges.put(propertyName, propertyValue);
        }
        if (!fb.edited && SVNProperty.isRegularProperty(propertyName)) {
            fb.markEdited();
        }
        return;
    }

    public void closeFile(String path, String expectedMd5Digest) throws SVNException {
        FileBaton fb = myCurrentFile;
        if (fb.skipThis) {
            maybeBumpDirInfo(fb.bumpInfo);
            return;
        }

        SVNChecksum expectedMd5Checksum = null;
        if (expectedMd5Digest != null) {
            expectedMd5Checksum = new SVNChecksum(SVNChecksumKind.MD5, expectedMd5Digest);
        }
        if (expectedMd5Checksum != null && fb.newTextBaseMD5Digest != null &&
                !expectedMd5Checksum.getDigest().equals(fb.newTextBaseMD5Digest)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''", new Object[] {
                    fb.localAbsolutePath, expectedMd5Checksum, fb.newTextBaseMD5Digest
            });
            SVNErrorManager.error(err, SVNLogType.WC);            
        }

        SVNProperties entryProps = fb.entryPropChanges;
        SVNProperties davProps = fb.davPropChanges;
        SVNProperties regularProps = fb.regularPropChanges;

        AccumulatedChangeInfo lastChange = accumulateLastChange(fb.localAbsolutePath, entryProps);
        long newChangedRev = lastChange.changedRev;
        SVNDate newChangedDate = lastChange.changedDate;
        String newChangedAuthor = lastChange.changedAuthor;
        if (newChangedRev >= 0) {
            fb.changedRevison = newChangedRev;
        }
        if (newChangedDate != null) {
            fb.changedDate = newChangedDate;
        }
        if (newChangedAuthor != null) {
            fb.changedAuthor = newChangedAuthor;
        }

        SVNStatusType lockState = SVNStatusType.LOCK_UNCHANGED;
        for (Iterator<?> i = entryProps.nameSet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            if (SVNProperty.LOCK_TOKEN.equals(name)) {
                assert (entryProps.getStringValue(name) == null);
                myWcContext.getDb().removeLock(fb.localAbsolutePath);
                lockState = SVNStatusType.LOCK_UNLOCKED;
                break;
            }
        }
        
        SVNProperties localActualProps = null;
        SVNProperties currentBaseProps = null;
        SVNProperties currentActualProps = null;
        if ((!fb.addingFile || fb.addExisted) && !fb.shadowed) {
            localActualProps = myWcContext.getActualProps(fb.localAbsolutePath);
        }
        if (localActualProps == null) {
            localActualProps = new SVNProperties();
        }
        if (fb.addExisted) {
            currentBaseProps = myWcContext.getPristineProps(fb.localAbsolutePath);
            currentActualProps = localActualProps;
        } else if (!fb.addingFile) {
            currentBaseProps = myWcContext.getDb().getBaseProps(fb.localAbsolutePath);
            currentActualProps = localActualProps;
        }
        
        if (currentBaseProps == null) {
            currentBaseProps = new SVNProperties();
        }
        if (currentActualProps == null) {
            currentActualProps = new SVNProperties();
        }
        
        if (!fb.shadowed && (!fb.addingFile || fb.addExisted)) {
            boolean localIsLink = false;
            boolean incomingIsLink = false;
            
            localIsLink = localActualProps.getStringValue(SVNProperty.SPECIAL) != null;
            for(Iterator<?> names = regularProps.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                incomingIsLink = SVNProperty.SPECIAL.equals(name);
                if (incomingIsLink) {
                    break;
                }
            }
            
            if (localIsLink != incomingIsLink) {
                SVNTreeConflictDescription treeConflict = null;
                fb.shadowed = true;
                fb.obstructionFound = true;
                fb.addExisted = false;
                
                treeConflict = checkTreeConflict(fb.localAbsolutePath, SVNWCDbStatus.Added, SVNWCDbKind.File, true, SVNConflictAction.ADD, SVNNodeKind.FILE, fb.newRelativePath);
                myWcContext.getDb().opSetTreeConflict(fb.localAbsolutePath, treeConflict);
                fb.alreadyNotified = true;
                doNotification(fb.localAbsolutePath, SVNNodeKind.UNKNOWN, SVNEventAction.TREE_CONFLICT);
            }
        }
        
        SVNStatusType[] propState = new SVNStatusType[] {SVNStatusType.UNKNOWN};
        SVNStatusType contentState = null;
        SVNProperties newBaseProps = new SVNProperties();
        SVNProperties newActualProps = new SVNProperties();
        SVNSkel allWorkItems = null;
        
        boolean keepRecordedInfo = false;
        if (!fb.shadowed) {
            boolean installPristine = false;
            SVNSkel workItem = myWcContext.mergeProperties2(propState, newBaseProps, newActualProps, fb.localAbsolutePath, 
                    SVNWCDbKind.File, null, null, null, currentBaseProps, currentActualProps, regularProps, true, false);
            allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);

            File installFrom = null;
            if (!fb.obstructionFound) {
                MergeFileInfo fileInfo = null;                
                try {
                    fileInfo = mergeFile(fb, currentActualProps, fb.changedDate);
                    installFrom = fileInfo.installFrom;
                    installPristine = fileInfo.installPristine;
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.IO_ERROR) {
                        // TODO catch 'access denied' here
                        doNotification(fb.localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.SKIP);
                        rememberSkippedTree(fb.localAbsolutePath);
                        fb.skipThis = true;
                        maybeBumpDirInfo(fb.bumpInfo);
                        return;
                    }
                    throw e;
                }
                if (fileInfo != null) {
                    allWorkItems = myWcContext.wqMerge(allWorkItems, fileInfo.workItems);
                }
            } else {
                installPristine = false;
                if (fb.newTextBaseSHA1Checksum != null) {
                    contentState = SVNStatusType.CHANGED;
                } else {
                    contentState = SVNStatusType.UNCHANGED;
                }
            }
            if (installPristine) {
                boolean recordFileInfo = installFrom != null;
                SVNSkel wi = myWcContext.wqBuildFileInstall(fb.localAbsolutePath, installFrom, myIsUseCommitTimes, recordFileInfo);
                allWorkItems = myWcContext.wqMerge(allWorkItems, wi);
            } else if (lockState == SVNStatusType.LOCK_UNLOCKED && !fb.obstructionFound) {
                SVNSkel wi = myWcContext.wqBuildSyncFileFlags(fb.localAbsolutePath);
                allWorkItems = myWcContext.wqMerge(allWorkItems, wi);
            }
            
            if (!installPristine && contentState == SVNStatusType.UNCHANGED) {
                keepRecordedInfo = true;
            }
            if (installFrom != null && !fb.localAbsolutePath.equals(installFrom)) {
                SVNSkel wi = myWcContext.wqBuildFileRemove(installFrom);
                allWorkItems = myWcContext.wqMerge(allWorkItems, wi);
                
            }
        } else {
            SVNProperties fakeActualProperties;
            if (fb.addingFile) {
                fakeActualProperties = new SVNProperties();
            } else {
                fakeActualProperties = currentBaseProps;
            }
            
            SVNSkel workItem = myWcContext.mergeProperties2(propState, newBaseProps, newActualProps, fb.localAbsolutePath, SVNWCDbKind.File, null, null, null, currentBaseProps, fakeActualProperties, regularProps, true, false);
            allWorkItems = myWcContext.wqMerge(allWorkItems, workItem);
            if (fb.newTextBaseSHA1Checksum != null) {
                contentState = SVNStatusType.CHANGED;
            } else {
                contentState = SVNStatusType.UNCHANGED;
            }
        }
        
        SVNChecksum newChecksum = fb.newTextBaseSHA1Checksum;
        if (newChecksum == null) {
            newChecksum = fb.originalChecksum;
        }
        
        myWcContext.getDb().addBaseFile(fb.localAbsolutePath, fb.newRelativePath, myReposRootURL, myReposUuid, targetRevision, newBaseProps, fb.changedRevison, fb.changedDate, fb.changedAuthor, newChecksum, 
                davProps != null && !davProps.isEmpty() ? davProps : null, null, 
                !fb.shadowed && newBaseProps != null, newActualProps, keepRecordedInfo, fb.shadowed && fb.obstructionFound, allWorkItems);

        if (fb.addExisted && fb.addingFile) {
            myWcContext.getDb().opRemoveWorkingTemp(fb.localAbsolutePath);
        }
        if (fb.directoryBaton.notPresentFiles != null) {
            fb.directoryBaton.notPresentFiles.remove(fb.name);
        }
        
        if (myWcContext.getEventHandler() != null && !fb.alreadyNotified && fb.edited) {
            SVNEventAction action = SVNEventAction.UPDATE_UPDATE;
            if (fb.shadowed) {
                action = fb.addingFile ? SVNEventAction.UPDATE_ADD_DELETED : SVNEventAction.UPDATE_UPDATE_DELETED;
            } else if (fb.obstructionFound || fb.addExisted) {
                if (contentState != SVNStatusType.CONFLICTED) {
                    action = SVNEventAction.UPDATE_EXISTS;
                }
            } else if (fb.addingFile) {
                action = SVNEventAction.UPDATE_ADD;
            }
            
            String mimeType = myWcContext.getProperty(fb.localAbsolutePath, SVNProperty.MIME_TYPE);
            SVNEvent event = SVNEventFactory.createSVNEvent(fb.localAbsolutePath, SVNNodeKind.FILE, mimeType, targetRevision, contentState, propState[0], lockState, action, null, null, null);
            event.setPreviousRevision(fb.oldRevision);
            myWcContext.getEventHandler().handleEvent(event, 0);
        }
        maybeBumpDirInfo(fb.bumpInfo);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!rootOpened && ("".equals(myTargetBasename) || myTargetBasename == null)) {
            myWcContext.getDb().opSetBaseIncompleteTemp(myAnchorAbspath, false);
//            completeDirectory(myAnchorAbspath, true);
        }
        if (!myIsTargetDeleted) {
            myWcContext.getDb().opBumpRevisionPostUpdate(myTargetAbspath , myRequestedDepth, mySwitchRelpath, myReposRootURL, myReposUuid, targetRevision, mySkippedTrees);
            if (myTargetBasename == null || "".equals(myTargetBasename)) {
                SVNWCDbStatus status = null;
                boolean error = false;
                try {
                    status = myWcContext.getDb().getBaseInfo(myTargetAbspath, BaseInfoField.status).status;
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                        throw e;
                    }
                    error = true;
                }
                if (!error && status == SVNWCDbStatus.Excluded) {
                    myWcContext.getDb().removeBase(myTargetAbspath);
                }
            }
//            doUpdateCleanup(myTargetAbspath, myRequestedDepth, mySwitchRelpath, myReposRootURL, myReposUuid, targetRevision, mySkippedTrees);
        }
        
        myWcContext.wqRun(myAnchorAbspath);
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String expectedChecksum) throws SVNException {
        FileBaton fb = myCurrentFile;
        if (fb.skipThis) {
            return;
        }
        fb.markEdited();
        SVNChecksum expectedBaseChecksum = expectedChecksum != null ? new SVNChecksum(SVNChecksumKind.MD5, expectedChecksum) : null;
        SVNChecksum recordedBaseChecksum = fb.originalChecksum;
        
        if (recordedBaseChecksum != null && expectedBaseChecksum != null && recordedBaseChecksum.getKind() != SVNChecksumKind.MD5) {
            recordedBaseChecksum = myWcContext.getDb().getPristineMD5(myAnchorAbspath, recordedBaseChecksum);
        }
        if (recordedBaseChecksum != null && expectedChecksum != null && !expectedChecksum.equals(recordedBaseChecksum)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}'':\n " + "   expected:  ''{1}''\n" + "   recorded:  ''{2}''\n",
                    new Object[] {
                            fb.localAbsolutePath, expectedChecksum, recordedBaseChecksum
                    });
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
            
        }
        InputStream source;
        if (!fb.addingFile) {
            source = myWcContext.getDb().readPristine(fb.localAbsolutePath, fb.originalChecksum);
            if (source == null) {
                source = SVNFileUtil.DUMMY_IN;
            }
        } else {
            source = SVNFileUtil.DUMMY_IN;
        }
        if (recordedBaseChecksum == null) {
            recordedBaseChecksum = expectedBaseChecksum;
        }
        
        if (recordedBaseChecksum != null) {
            fb.expectedSourceChecksum = new SVNChecksum(recordedBaseChecksum.getKind(), recordedBaseChecksum.getDigest());
            if (source != SVNFileUtil.DUMMY_IN) {
                source = new SVNChecksumInputStream(source, SVNChecksumInputStream.MD5_ALGORITHM);
                fb.sourceChecksumStream = (SVNChecksumInputStream) source;
            }
        }
        WritableBaseInfo openWritableBase = myWcContext.openWritableBase(fb.localAbsolutePath, false, true);
        OutputStream target = openWritableBase.stream;
        fb.newTextBaseTmpAbsPath = openWritableBase.tempBaseAbspath;
        myDeltaProcessor.applyTextDelta(source, target, true);
        fb.newTextBaseSHA1ChecksumStream = openWritableBase.sha1ChecksumStream;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentFile.skipThis) {
            return SVNFileUtil.DUMMY_OUT;
        }
        try {
            myDeltaProcessor.textDeltaChunk(diffWindow);
        } catch (SVNException svne) {
            myDeltaProcessor.textDeltaEnd();
            SVNFileUtil.deleteFile(myCurrentFile.newTextBaseTmpAbsPath);
            myCurrentFile.newTextBaseTmpAbsPath = null;
            throw svne;
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (myCurrentFile.skipThis) {
            return;
        }
        myCurrentFile.newTextBaseMD5Digest = myDeltaProcessor.textDeltaEnd();
        if (myCurrentFile.newTextBaseSHA1ChecksumStream != null) {
            myCurrentFile.newTextBaseSHA1Checksum = new SVNChecksum(SVNChecksumKind.SHA1, myCurrentFile.newTextBaseSHA1ChecksumStream.getDigest());
        }

        if (myCurrentFile.expectedSourceChecksum != null) {
            String actualSourceChecksum = myCurrentFile.sourceChecksumStream != null ? myCurrentFile.sourceChecksumStream.getDigest() : null;
            if (!myCurrentFile.expectedSourceChecksum.equals(actualSourceChecksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch while updating ''{0}''; expected: ''{1}'', actual: ''{2}''", new Object[] {
                        myCurrentFile.localAbsolutePath, myCurrentFile.expectedSourceChecksum.getDigest(), actualSourceChecksum
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (myCurrentFile.newTextBaseTmpAbsPath != null && myCurrentFile.newTextBaseSHA1Checksum != null && myCurrentFile.newTextBaseMD5Digest != null) {
            myWcContext.getDb().installPristine(myCurrentFile.newTextBaseTmpAbsPath, myCurrentFile.newTextBaseSHA1Checksum, 
                    new SVNChecksum(SVNChecksumKind.MD5, myCurrentFile.newTextBaseMD5Digest));
        }

    }
/*
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
                // d.setNewRelpath(myWcContext.getDb().scanBaseRepository(d.getLocalAbspath(),
                // RepositoryInfoField.relPath).relPath);

                WCDbInfo readInfo = myWcContext.getDb().readInfo(d.getLocalAbspath(), InfoField.status, InfoField.reposRelPath, InfoField.originalReposRelpath);
                SVNWCDbStatus status = readInfo.status;
                File reposRelPath = readInfo.reposRelPath;
                File originalReposRelpath = readInfo.originalReposRelpath;

                if (status == SVNWCDbStatus.Added) {
                    WCDbAdditionInfo addition = myWcContext.getDb().scanAddition(d.getLocalAbspath(), AdditionInfoField.reposRelPath, AdditionInfoField.originalReposRelPath);
                    reposRelPath = addition.reposRelPath;
                    originalReposRelpath = addition.originalReposRelPath;
                }

                if (originalReposRelpath != null) {
                    d.setNewRelpath(originalReposRelpath);
                } else if (reposRelPath != null) {
                    d.setNewRelpath(reposRelPath);
                } else {
                    d.setNewRelpath(myWcContext.getDb().scanBaseRepository(d.getLocalAbspath(), RepositoryInfoField.relPath).relPath);
                }
                assert (d.getNewRelpath() != null);

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
                myChangedEntryProperties.put(name, value);
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
  */  
    private static class BumpDirectoryInfo {
        private int refCount;
        private BumpDirectoryInfo parent;
        public File absPath;
    }
    
    private class DirectoryBaton {
        private String name;
        private File localAbsolutePath;
        private File newRelativePath;
        
        private long oldRevision;
        DirectoryBaton parentBaton;
        
        private boolean skipThis;
        private boolean alreadyNotified;
        private boolean addingDir;
        private boolean shadowed;
        
        private long changedRevsion;
        private SVNDate changedDate;
        private String changedAuthor;
        
        private Map<String, SVNTreeConflictDescription> deletionConflicts;
        private Set<String> notPresentFiles;
        
        private boolean obstructionFound;
        private boolean addExisted;
        
        private SVNProperties entryPropChanges;
        private SVNProperties regularPropChanges;
        private SVNProperties davPropChanges;
        
        private boolean edited;
        private SVNTreeConflictDescription editConflict;
        
        private BumpDirectoryInfo bumpInfo;
        private SVNDepth ambientDepth;
        private boolean wasIncomplete;
        
        public void markEdited() throws SVNException {
            if (edited) {
                return;
            }
            if (parentBaton != null) {
                parentBaton.markEdited();
            }
            edited = true;
            if (editConflict != null) {
                myWcContext.getDb().opSetTreeConflict(localAbsolutePath, editConflict);
                doNotification(localAbsolutePath, SVNNodeKind.DIR, SVNEventAction.TREE_CONFLICT);
                alreadyNotified = true;
            }
        }
    }
    
    private class FileBaton {
        private String name;
        private File localAbsolutePath;
        private File newRelativePath;
        private long oldRevision;
        private DirectoryBaton directoryBaton;
        
        private boolean skipThis;
        private boolean alreadyNotified;
        private boolean addingFile;
        private boolean obstructionFound;
        private boolean addExisted;
        private boolean shadowed;
        
        private long changedRevison;
        private SVNDate changedDate;
        private String changedAuthor;
        
        private SVNChecksum originalChecksum;
        
        private SVNProperties entryPropChanges;
        private SVNProperties regularPropChanges;
        private SVNProperties davPropChanges;
        
        private BumpDirectoryInfo bumpInfo;
        
        private boolean edited;
        private SVNTreeConflictDescription editConflict;
        
        public void markEdited() throws SVNException {
            if (edited) {
                return;
            }
            
            if (directoryBaton != null) {
                directoryBaton.markEdited();
            }
            edited = true;
            if (editConflict != null) {
                myWcContext.getDb().opSetTreeConflict(localAbsolutePath, editConflict);
                doNotification(localAbsolutePath, SVNNodeKind.FILE, SVNEventAction.TREE_CONFLICT);
                alreadyNotified = true;
            }
        }
        
        // delta handling
        File newTextBaseTmpAbsPath;
        SVNChecksumInputStream sourceChecksumStream;        
        SVNChecksumOutputStream newTextBaseSHA1ChecksumStream;

        SVNChecksum expectedSourceChecksum;
        String newTextBaseMD5Digest;
        public SVNChecksum newTextBaseSHA1Checksum;
    }
    
    private DirectoryBaton makeDirectoryBaton(String path, DirectoryBaton parent, boolean adding) throws SVNException {
        DirectoryBaton d = new DirectoryBaton();
        if (path != null) {
            d.name = SVNPathUtil.tail(path);
            d.localAbsolutePath = SVNFileUtil.createFilePath(parent.localAbsolutePath, d.name);
        } else {
            d.name = null;
            d.localAbsolutePath = myAnchorAbspath;
        }
        
        if (mySwitchRelpath != null) {
            if (parent == null ) {
                if ("".equals(myTargetBasename) || myTargetBasename == null) {
                    d.newRelativePath = mySwitchRelpath;
                } else {
                    d.newRelativePath = myWcContext.getDb().scanBaseRepository(d.localAbsolutePath, RepositoryInfoField.relPath).relPath;
                }
            } else {
                if (parent.parentBaton ==null && myTargetBasename.equals(d.name)) {
                    d.newRelativePath = mySwitchRelpath;
                } else {
                    d.newRelativePath = SVNFileUtil.createFilePath(parent.newRelativePath, d.name);
                }
            }
        } else {
            if (adding) {
                d.newRelativePath = SVNFileUtil.createFilePath(parent.newRelativePath, d.name);
            } else {
                d.newRelativePath = myWcContext.getDb().scanBaseRepository(d.localAbsolutePath, RepositoryInfoField.relPath).relPath;
            }
        }
        BumpDirectoryInfo bdi = new BumpDirectoryInfo();
        bdi.parent = parent != null ? parent.bumpInfo : null;
        bdi.refCount = 1;
        bdi.absPath = d.localAbsolutePath;
        
        if (parent != null) {
            bdi.parent.refCount++;
        }
        d.parentBaton = parent;
        d.bumpInfo = bdi;
        d.oldRevision = -1;
        d.addingDir = adding;
        d.changedRevsion = -1;
        d.notPresentFiles =  new HashSet<String>();
        if (parent != null) {
            d.skipThis = parent.skipThis;
            d.shadowed = parent.shadowed;            
        }
        d.ambientDepth = SVNDepth.UNKNOWN;
        return d;
    }
    
    private FileBaton makeFileBaton(DirectoryBaton parent, String path, boolean adding) throws SVNException {
        FileBaton f = new FileBaton();
        f.name = SVNPathUtil.tail(path);
        f.oldRevision = -1;
        f.localAbsolutePath = SVNFileUtil.createFilePath(parent.localAbsolutePath, f.name);
        
        if (mySwitchRelpath != null) {
            if (parent == null && f.name.equals(myTargetBasename) || myTargetBasename == null) {
                f.newRelativePath = mySwitchRelpath;
            } else {
                f.newRelativePath = SVNFileUtil.createFilePath(parent.newRelativePath, f.name);
            }
        } else { 
            if (adding) {
                f.newRelativePath = SVNFileUtil.createFilePath(parent.newRelativePath, f.name);
            } else {
                f.newRelativePath = myWcContext.getDb().scanBaseRepository(f.localAbsolutePath, RepositoryInfoField.relPath).relPath;
            }
        }   
        f.bumpInfo = parent.bumpInfo;
        f.addingFile = adding;
        f.skipThis = parent.skipThis;
        f.shadowed = parent.shadowed;
        f.directoryBaton = parent;
        f.changedRevison = -1;
        f.bumpInfo.refCount++;
        
        return f;
    }
/*
    private class SVNDirectoryInfo extends SVNEntryInfo {

        private boolean skipDescendants;
        private boolean addingDir;
        private boolean inDeletedAndTreeConflictedSubtree;
        private SVNDepth ambientDepth;
        private boolean wasIncomplete;
        private Map<String, SVNTreeConflictDescription> deletionConflicts;

        public boolean isSkipDescendants() {
            return skipDescendants;
        }
        
        public SVNTreeConflictDescription getDeletionConflict(String name) {
            return deletionConflicts != null ? deletionConflicts.get(name) : null;
        }

        public boolean hasDeletionConflicts() {
            return deletionConflicts == null || deletionConflicts.isEmpty();
        }
        
        public void addDeletionConflict(String baseName, SVNTreeConflictDescription conflict) {
            if (deletionConflicts == null) {
                deletionConflicts = new HashMap<String, SVNTreeConflictDescription>();
            }
            deletionConflicts.put(baseName, conflict);
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
*/
    private static boolean isNodePresent(SVNWCDbStatus status) {
        return status != SVNWCDbStatus.ServerExcluded && status != SVNWCDbStatus.Excluded && status != SVNWCDbStatus.NotPresent;
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
        SVNConflictVersion srcRightVersion = new SVNConflictVersion(reposRootUrl, rightReposRelpath.getPath(), targetRevision, theirNodeKind);
        return new SVNTreeConflictDescription(localAbspath, conflictNodeKind, action, reason, mySwitchRelpath != null ? SVNOperation.SWITCH : SVNOperation.UPDATE, srcLeftVersion, srcRightVersion);
    }
/*
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
*/
    private void maybeBumpDirInfo(BumpDirectoryInfo bdi) throws SVNException {
        while (bdi != null) {
            if (--bdi.refCount > 0) {
                return;
            }
            myWcContext.getDb().opSetBaseIncompleteTemp(bdi.absPath, false);
            bdi = bdi.parent;
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
        if (entryProps != null) {
            for (Iterator<?> i = entryProps.nameSet().iterator(); i.hasNext();) {
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
        }
        return info;
    }
/*
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
*/
    /*
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
        if (checksum != null) {
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
*/
    private static class MergeFileInfo {

        public SVNSkel workItems;
        public boolean installPristine;
        public File installFrom;
        public SVNStatusType contentState;
    }
    
    private MergeInfo performFileMerge(File localAbsPath, File wriAbsPath, SVNChecksum newChecksum, SVNChecksum originalChecksum,
            SVNProperties actualProperties, String[] extPatterns,
            long oldRevision, long targetRevision, SVNProperties propChanges) throws SVNException {
        File mergeLeft = null;
        boolean deleteLeft = true;
        File newTextBaseTmpAbsPath = myWcContext.getDb().getPristinePath(wriAbsPath, newChecksum);
        if (extPatterns != null && extPatterns.length > 0) {
            
        }
        if (oldRevision < 0) {
            oldRevision = 0;
        }
        String oldRevStr = String.format(".r%s%s%s", oldRevision, "", "");
        String newRevStr = String.format(".r%s%s%s", targetRevision, "", "");
        String mineStr = String.format(".mine%s%s", "", "");
        
        if (originalChecksum == null) {
            SVNFileUtil.createEmptyFile(wriAbsPath);
            mergeLeft = wriAbsPath;
            deleteLeft = true;
        } else {
            mergeLeft = myWcContext.getDb().getPristinePath(wriAbsPath, originalChecksum);
        }
        
        MergeInfo mergeInfo = myWcContext.merge(mergeLeft, null, newTextBaseTmpAbsPath, null, localAbsPath, wriAbsPath, 
                oldRevStr, newRevStr, mineStr, actualProperties, false, null, propChanges);
        if (deleteLeft) {
            SVNSkel workItem = myWcContext.wqBuildFileRemove(mergeLeft);
            myWcContext.wqMerge(mergeInfo.workItems, workItem);
        }
        return mergeInfo;
    }

    private MergeFileInfo mergeFile(FileBaton fb, SVNProperties actualProps, SVNDate lastChangedDate) throws SVNException {
        DirectoryBaton pb = fb.directoryBaton;
        boolean isLocallyModified;
        boolean isReplaced = false;
        boolean magicPropsChanged= false;
        
        MergeFileInfo mergeFileInfo = new MergeFileInfo();
        mergeFileInfo.installPristine = false;
        mergeFileInfo.contentState = SVNStatusType.UNCHANGED;
        
        if (fb.addingFile && !fb.addExisted) {
            isLocallyModified = false;
        } else {
            isLocallyModified = myWcContext.isTextModified(fb.localAbsolutePath, false, false);
        }
        
        SVNProperties propChanges = new SVNProperties();
        if (fb.regularPropChanges != null) {
            propChanges.putAll(fb.regularPropChanges);
        } 
        if (fb.entryPropChanges != null) {
            propChanges.putAll(fb.entryPropChanges);
        }
        if (fb.davPropChanges != null) {
            propChanges.putAll(fb.davPropChanges);
        }
        
        if (!isLocallyModified && fb.newTextBaseSHA1Checksum != null) {
            mergeFileInfo.installPristine = true;
        } else if (fb.newTextBaseSHA1Checksum != null) {
            MergeInfo mergeInfo = performFileMerge(fb.localAbsolutePath, 
                    pb.localAbsolutePath, 
                    fb.newTextBaseSHA1Checksum, 
                    fb.addExisted ? null : fb.originalChecksum,
                    actualProps, 
                    myExtensionPatterns, 
                    fb.oldRevision, 
                    targetRevision, 
                    propChanges);
            mergeFileInfo.workItems = myWcContext.wqMerge(mergeFileInfo.workItems, mergeInfo.workItems);
            mergeFileInfo.contentState = mergeInfo.mergeOutcome;
        } else {
            magicPropsChanged = myWcContext.hasMagicProperty(propChanges);
            TranslateInfo translateInfo = myWcContext.getTranslateInfo(fb.localAbsolutePath, actualProps, true, false, true, false);
            if (magicPropsChanged || (translateInfo.keywords != null && !translateInfo.keywords.isEmpty())) {
                if (isLocallyModified) {
                    File tmpText = null;
                    
                    tmpText = myWcContext.getTranslatedFile(fb.localAbsolutePath, fb.localAbsolutePath, true, true, false, false);
                    mergeFileInfo.installPristine = true;
                    mergeFileInfo.installFrom = tmpText;
                } else {
                    mergeFileInfo.installPristine = true;
                }
            }
        }
        
        if (!mergeFileInfo.installPristine && !isLocallyModified) {
            SVNDate date = SVNDate.NULL;
            if (lastChangedDate != null) {
                date = lastChangedDate;
            }
            SVNSkel workItem = myWcContext.wqBuildRecordFileinfo(fb.localAbsolutePath, date);
            mergeFileInfo.workItems = myWcContext.wqMerge(mergeFileInfo.workItems, workItem);
        }
        
        if (mergeFileInfo.contentState == SVNStatusType.CONFLICTED) {
            
        } else if (fb.newTextBaseSHA1Checksum != null) {
            if (isLocallyModified) {
                mergeFileInfo.contentState = SVNStatusType.MERGED;
            } else {
                mergeFileInfo.contentState = SVNStatusType.CHANGED;                
            }
        } else {
            mergeFileInfo.contentState = SVNStatusType.UNCHANGED;                
        }
        return mergeFileInfo;
    }
/*
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
*/
    /*
    private File getEmptyTmpFile(File localAbspath) throws SVNException {
        File tempDirPath = myWcContext.getDb().getWCRootTempDir(localAbspath);
        return SVNFileUtil.createUniqueFile(tempDirPath, "tempfile", ".tmp", false).getAbsoluteFile();
    }

    private boolean modcheckVersionedFile(File versioned, InputStream pristineStream, boolean compareTextbases) throws SVNException {
        return myWcContext.compareAndVerify(versioned, pristineStream, compareTextbases, false);
    }
    */
/*
    private void completeDirectory(File localAbspath, boolean isRootDir) throws SVNException {
        if (isRootDir && myTargetBasename != null) {
            SVNWCDbStatus status;
            assert (localAbspath != null && localAbspath.equals(myAnchorAbspath));
            try {
                status = myWcContext.getDb().getBaseInfo(myTargetAbspath, BaseInfoField.status).status;
                if (status == SVNWCDbStatus.Excluded) {
                    doEntryDeletion(myTargetAbspath, null, false);
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                    throw e;
                }
            }
            return;
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
        Set<String> children = myWcContext.getDb().getBaseChildren(localAbspath);
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
            } else if (status == SVNWCDbStatus.ServerExcluded && revnum != targetRevision) {
                myWcContext.getDb().removeBase(nodeAbspath);
            }
        }
    }
*/
    /*
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
            return;
        }
        if (status != null) {
            switch (status) {
                case Excluded:
                case ServerExcluded:
                case NotPresent:
                    return;
                default:
                    break;
            }
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
*/
    /*
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
        if (allowRemoval && (status == SVNWCDbStatus.NotPresent || (status == SVNWCDbStatus.ServerExcluded && revision != newRevision))) {
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
    */
/*
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
        Set<String> children = myWcContext.getDb().getBaseChildren(dirAbspath);
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
            if (kind == SVNWCDbKind.File || status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.ServerExcluded || status == SVNWCDbStatus.Excluded) {
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
    */
}
