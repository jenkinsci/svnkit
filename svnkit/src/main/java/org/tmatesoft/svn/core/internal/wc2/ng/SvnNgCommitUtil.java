package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.CheckSpecialInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ConflictInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCNodeReposInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeOriginInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbReader;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbReader.ReplaceInfo;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgCommitUtil {
    
    private enum NodeCommitStatus {
        kind,
        added,
        deleted,
        notPresent,
        excluded,
        isOpRoot,
        isReplaceRoot,
        symlink,
        reposRelPath,
        revision,
        originalReposRelPath,
        originalRevision,
        changelist,
        conflicted,
        updateRoot,
        lockToken, propsMod
    }
    
    interface ISvnUrlKindCallback {
        public SVNNodeKind getUrlKind(SVNURL url, long revision) throws SVNException;
    }
    
    public static SvnCommitPacket harversCommittables(SVNWCContext context, SvnCommitPacket packet, Map<SVNURL, String> lockTokens,
            File baseDirPath,
            Collection<String> targets,
            SVNDepth depth, boolean justLocked, Collection<String> changelists, ISvnUrlKindCallback urlKindCallback) throws SVNException {
        
        Map<File, File> danglers = new HashMap<File, File>();
        
        
        for (String target : targets) {
            File targetPath = SVNFileUtil.createFilePath(baseDirPath, target);
            SVNNodeKind kind = context.readKind(targetPath, false);
            if (kind == SVNNodeKind.NONE) {
                SVNTreeConflictDescription tc = context.getTreeConflict(targetPath);
                if (tc != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                            "Aborting commit: ''{0}'' remains in conflict", targetPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                            "''{0}'' is not under version control", targetPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            SVNWCNodeReposInfo reposInfo = context.getNodeReposInfo(targetPath);
            SVNURL repositoryRootUrl = reposInfo.reposRootUrl;
            
            boolean added = context.isNodeAdded(targetPath);
            if (added) {
                File parentPath = SVNFileUtil.getParentFile(targetPath);
                try {
                    boolean parentIsAdded = context.isNodeAdded(parentPath);
                    if (parentIsAdded) {
                        danglers.put(parentPath, targetPath);
                    }
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, 
                                "''{0}'' is scheduled for addition within unversioned parent", targetPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    throw e;
                }
            }
            bailOnTreeConflictedAncestor(context, targetPath);
            harvestCommittables(context, targetPath, packet, lockTokens, repositoryRootUrl, null, false, depth, justLocked, changelists, false, false, urlKindCallback, context.getEventHandler());
        }
        for(SVNURL root : packet.getRepositoryRoots()) {
            handleDescendants(context, packet, root, packet.getItems(root), urlKindCallback, context.getEventHandler());
        }
        
        for(File danglingParent : danglers.keySet()) {
            if (!packet.hasItem(danglingParent)) {
                // TODO fail no parent event
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                    "''{0}'' is not under version control and is not part of the commit, yet its child ''{1}'' is part of the commit",
                    danglingParent, danglers.get(danglingParent));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        return packet;
    }

    private static void handleDescendants(SVNWCContext context, SvnCommitPacket packet, SVNURL rootUrl, Collection<SvnCommitItem> items, ISvnUrlKindCallback urlKindCallback, ISVNEventHandler eventHandler) throws SVNException {
        for (SvnCommitItem item : items) {
            if (!item.hasFlag(SvnCommitItem.ADD) || item.getCopyFromUrl() == null) {
                continue;
            }
            if (eventHandler != null) {
                eventHandler.checkCancelled();
            }
            Collection<File> notPresent = SvnWcDbReader.getNotPresentDescendants((SVNWCDb) context.getDb(), item.getPath());
            for (File absent : notPresent) {
                boolean itemFound = false;
                File localAbsPath = SVNFileUtil.createFilePath(item.getPath(), absent);
                for (SvnCommitItem i : items) {
                    if (i.getPath().equals(localAbsPath)) {
                        itemFound = true;
                        break; 
                    }
                }
                if (itemFound) {
                    continue;
                }
                SVNURL url = SVNWCUtils.join(item.getCopyFromUrl(), absent);
                if (urlKindCallback != null) {
                    if (urlKindCallback.getUrlKind(url, item.getCopyFromRevision()) == SVNNodeKind.NONE) {
                        continue;
                    }
                }
                String reposRelativePath = SVNURLUtil.getRelativeURL(rootUrl, url);
                packet.addItem(localAbsPath, SVNNodeKind.NONE, rootUrl,  reposRelativePath, -1, null, -1, SvnCommitItem.DELETE);
            }
        }
    }

    public static void harvestCommittables(SVNWCContext context, File localAbsPath, SvnCommitPacket committables, 
            Map<SVNURL, String> lockTokens, SVNURL repositoryRootUrl, File commitRelPath, boolean copyModeRoot, 
            SVNDepth depth, boolean justLocked, Collection<String> changelists, boolean skipFiles, boolean skipDirs, 
            ISvnUrlKindCallback urlKindCallback, ISVNEventHandler eventHandler) throws SVNException {
        
        if (committables.hasItem(localAbsPath)) {
            return;
        }
        
        boolean copyMode = commitRelPath != null;

        if (eventHandler != null) {
            eventHandler.checkCancelled();
        }
        
        Structure<NodeCommitStatus> commitStatus = getNodeCommitStatus(context, localAbsPath);
        if ((skipFiles && commitStatus.get(NodeCommitStatus.kind) == SVNNodeKind.FILE) || commitStatus.is(NodeCommitStatus.excluded)) {
            commitStatus.release();
            return;
        }
        if (commitStatus.get(NodeCommitStatus.reposRelPath) == null && commitRelPath != null) {
            commitStatus.set(NodeCommitStatus.reposRelPath, commitRelPath);
        }
        
        CheckSpecialInfo checkSpecial = SVNWCContext.checkSpecialPath(localAbsPath);
        SVNNodeKind workingKind = checkSpecial.kind;
        boolean isSpecial = checkSpecial.isSpecial;
        if ((workingKind != SVNNodeKind.FILE) && (workingKind != SVNNodeKind.DIR) && (workingKind != SVNNodeKind.NONE)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        boolean matchesChangelists = context.isChangelistMatch(localAbsPath, changelists);
        if (workingKind != SVNNodeKind.DIR && workingKind != SVNNodeKind.NONE && !matchesChangelists) {
            commitStatus.release();
            return;
        }
        if ((!commitStatus.is(NodeCommitStatus.symlink) && isSpecial ||
                (SVNFileUtil.symlinksSupported() && (commitStatus.is(NodeCommitStatus.symlink) && !isSpecial))) &&
                workingKind != SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Entry ''{0}'' has unexpectedly changed special status", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (copyMode && 
                commitStatus.is(NodeCommitStatus.updateRoot) && 
                commitStatus.get(NodeCommitStatus.kind) == SVNNodeKind.FILE) {
            if (copyMode) {
                commitStatus.release();
                return;
            }
        }
        
        if (commitStatus.is(NodeCommitStatus.conflicted) && matchesChangelists) {
            ConflictInfo ci = context.getConflicted(localAbsPath, true, true, true);
            if (ci.propConflicted || ci.textConflicted || ci.treeConflicted) {
                if (eventHandler != null) {
                    // TODO failed conflict event.
                }
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", localAbsPath);
                SVNErrorManager.error(error, SVNLogType.WC);
            }
        }
        if (commitStatus.is(NodeCommitStatus.deleted) && !commitStatus.is(NodeCommitStatus.isOpRoot)) {
            commitStatus.release();
            return;
        }
        if (commitStatus.get(NodeCommitStatus.reposRelPath) == null) {
            File reposRelPath = context.getNodeReposRelPath(localAbsPath);
            commitStatus.set(NodeCommitStatus.reposRelPath, reposRelPath);
        }
        int stateFlags = 0;
        
        if (commitStatus.is(NodeCommitStatus.deleted) || commitStatus.is(NodeCommitStatus.isReplaceRoot)) {
            stateFlags |= SvnCommitItem.DELETE;
        } else if (commitStatus.is(NodeCommitStatus.notPresent)) {
            if (!copyMode) {
                commitStatus.release();
                return;
            }
            if (urlKindCallback != null) {
                Structure<NodeOriginInfo> originInfo = context.getNodeOrigin(SVNFileUtil.getParentFile(localAbsPath), false, NodeOriginInfo.revision, NodeOriginInfo.reposRelpath);
                File reposRelPath = SVNFileUtil.createFilePath(originInfo.<File>get(NodeOriginInfo.reposRelpath), SVNFileUtil.getFileName(localAbsPath));
                SVNURL url = SVNWCUtils.join(repositoryRootUrl, reposRelPath);
                long revision = originInfo.lng(NodeOriginInfo.revision);
                originInfo.release();
                
                if (urlKindCallback.getUrlKind(url, revision) == SVNNodeKind.NONE) {
                    commitStatus.release();
                    return;
                }
            }
            stateFlags |= SvnCommitItem.DELETE;
        }
        
        File copyFromPath = null;
        long copyFromRevision = -1;
        if (commitStatus.is(NodeCommitStatus.added) && commitStatus.is(NodeCommitStatus.isOpRoot)) {
            stateFlags |= SvnCommitItem.ADD;
            if (commitStatus.get(NodeCommitStatus.originalReposRelPath) != null) {
                stateFlags |= SvnCommitItem.COPY;
                copyFromPath = commitStatus.get(NodeCommitStatus.originalReposRelPath);
                copyFromRevision = commitStatus.lng(NodeCommitStatus.originalRevision);
            }
        }
        if (copyMode
                && (!commitStatus.is(NodeCommitStatus.added) || copyModeRoot)
                && !((stateFlags & SvnCommitItem.DELETE) != 0)) {
            long dirRevision = 0;
            if (!copyModeRoot) {
                dirRevision = context.getNodeBaseRev(localAbsPath);
            }
            if (copyModeRoot || dirRevision != commitStatus.lng(NodeCommitStatus.revision)) {
                stateFlags |= SvnCommitItem.ADD;
                Structure<NodeOriginInfo> originInfo = context.getNodeOrigin(localAbsPath, false, NodeOriginInfo.revision, NodeOriginInfo.reposRelpath);
                copyFromPath = originInfo.get(NodeOriginInfo.reposRelpath);
                copyFromRevision = originInfo.lng(NodeOriginInfo.revision);
                originInfo.release();
                if (copyFromPath != null) {
                    stateFlags |= SvnCommitItem.COPY;
                }
            }
        }
        
        boolean textModified = false;
        if ((stateFlags & SvnCommitItem.ADD) != 0) {
            if (workingKind == SVNNodeKind.NONE) {
                // TODO failed missing event.
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' is scheduled for addition, but is missing", localAbsPath);
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            if (commitStatus.get(NodeCommitStatus.kind) == SVNNodeKind.FILE) {
                if ((stateFlags & SvnCommitItem.COPY) != 0) {
                    textModified = context.isTextModified(localAbsPath, false, false);
                } else {
                    textModified = true;
                }
            }
        } else if ((stateFlags & SvnCommitItem.DELETE) == 0) {
            if (commitStatus.get(NodeCommitStatus.kind) == SVNNodeKind.FILE) {
                textModified = context.isTextModified(localAbsPath, false, false);
            }
        }
        
        boolean propsModified = commitStatus.is(NodeCommitStatus.propsMod);
        if (textModified) {
            stateFlags |= SvnCommitItem.TEXT_MODIFIED;
        }
        if (propsModified) {
            stateFlags |= SvnCommitItem.PROPS_MODIFIED;
        }
        if (commitStatus.get(NodeCommitStatus.lockToken) != null && lockTokens != null && (stateFlags != 0 || justLocked)) {
            stateFlags |= SvnCommitItem.LOCK;
        }
        
        if (stateFlags != 0 && matchesChangelists) {
            SvnCommitItem item = committables.addItem(localAbsPath, 
                    commitStatus.<SVNNodeKind>get(NodeCommitStatus.kind), 
                    repositoryRootUrl, 
                    SVNFileUtil.getFilePath(copyMode ? commitRelPath : commitStatus.<File>get(NodeCommitStatus.reposRelPath)), 
                    copyMode ? -1 : commitStatus.lng(NodeCommitStatus.revision), 
                    SVNFileUtil.getFilePath(copyFromPath), 
                    copyFromRevision, 
                    stateFlags);
            if (item.hasFlag(SvnCommitItem.LOCK) && lockTokens != null) {
                lockTokens.put(item.getUrl(), commitStatus.<String>get(NodeCommitStatus.lockToken));
            }
        }
        if (lockTokens != null && (stateFlags & SvnCommitItem.DELETE) != 0) {
            collectLocks(context, localAbsPath, lockTokens);
        }
        try {
            if (commitStatus.get(NodeCommitStatus.kind) != SVNNodeKind.DIR || depth.compareTo(SVNDepth.EMPTY) <= 0) {
                return;
            }
            bailOnTreeConflictedChildren(context, localAbsPath, commitStatus.<SVNNodeKind>get(NodeCommitStatus.kind), depth, changelists);
        } finally {
            commitStatus.release();
        }
        
        if ((stateFlags & SvnCommitItem.DELETE) == 0 || (stateFlags & SvnCommitItem.ADD) != 0) {
            SVNDepth depthBelowHere = depth;
            if (depth.compareTo(SVNDepth.INFINITY) < 0) {
                depthBelowHere = SVNDepth.EMPTY;
            }
            List<File> children = context.getChildrenOfWorkingNode(localAbsPath, copyMode);
            for (File child : children) {
                String name = SVNFileUtil.getFileName(child);
                File childCommitRelPath = null;
                if (commitRelPath != null) {
                    childCommitRelPath = SVNFileUtil.createFilePath(commitRelPath, name);
                }
                harvestCommittables(context, child, committables, lockTokens, repositoryRootUrl, childCommitRelPath, false, depthBelowHere, justLocked, changelists, 
                        depth.compareTo(SVNDepth.FILES) < 0, 
                        depth.compareTo(SVNDepth.IMMEDIATES) < 0, 
                        urlKindCallback, eventHandler);
            }
        }

    }

    private static Structure<NodeCommitStatus> getNodeCommitStatus(SVNWCContext context, File localAbsPath) throws SVNException {
        Structure<NodeCommitStatus> result = Structure.obtain(NodeCommitStatus.class);
        Structure<NodeInfo> nodeInfo = context.getDb().readInfo(localAbsPath, NodeInfo.status, NodeInfo.kind, NodeInfo.revision,
                NodeInfo.reposRelPath, NodeInfo.originalRevision, NodeInfo.lock,
                NodeInfo.changelist, NodeInfo.conflicted, NodeInfo.opRoot, NodeInfo.hadProps,
                NodeInfo.propsMod, NodeInfo.haveBase, NodeInfo.haveMoreWork);
        
        if (nodeInfo.get(NodeInfo.kind) == SVNWCDbKind.File) {
            result.set(NodeCommitStatus.kind, SVNNodeKind.FILE);
        } else if (nodeInfo.get(NodeInfo.kind) == SVNWCDbKind.Dir) {
            result.set(NodeCommitStatus.kind, SVNNodeKind.DIR);
        } else {
            result.set(NodeCommitStatus.kind, SVNNodeKind.UNKNOWN);
        }
        result.set(NodeCommitStatus.reposRelPath, nodeInfo.get(NodeInfo.reposRelPath));
        result.set(NodeCommitStatus.revision, nodeInfo.lng(NodeInfo.revision));
        result.set(NodeCommitStatus.originalReposRelPath, nodeInfo.get(NodeInfo.originalReposRelpath));
        result.set(NodeCommitStatus.originalRevision, nodeInfo.lng(NodeInfo.originalRevision));
        result.set(NodeCommitStatus.changelist, nodeInfo.get(NodeInfo.changelist));
        result.set(NodeCommitStatus.propsMod, nodeInfo.get(NodeInfo.propsMod));
        
        SVNWCDbStatus nodeStatus = nodeInfo.get(NodeInfo.status);
        
        result.set(NodeCommitStatus.added, nodeStatus == SVNWCDbStatus.Added);
        result.set(NodeCommitStatus.deleted, nodeStatus == SVNWCDbStatus.Deleted);
        result.set(NodeCommitStatus.notPresent, nodeStatus == SVNWCDbStatus.NotPresent);
        result.set(NodeCommitStatus.excluded, nodeStatus == SVNWCDbStatus.Excluded);
        result.set(NodeCommitStatus.isOpRoot, nodeInfo.is(NodeInfo.opRoot));
        result.set(NodeCommitStatus.conflicted, nodeInfo.is(NodeInfo.conflicted));
        
        if (nodeStatus == SVNWCDbStatus.Added && nodeInfo.is(NodeInfo.opRoot) &&
                (nodeInfo.is(NodeInfo.haveBase) || nodeInfo.is(NodeInfo.haveMoreWork))) {
            Structure<ReplaceInfo> replaceInfo = SvnWcDbReader.readNodeReplaceInfo((SVNWCDb) context.getDb(), localAbsPath, ReplaceInfo.replaceRoot);
            result.set(NodeCommitStatus.isReplaceRoot, replaceInfo.is(ReplaceInfo.replaceRoot));
            replaceInfo.release();
        } else {
            result.set(NodeCommitStatus.isReplaceRoot, false);
        }
        if (nodeInfo.get(NodeInfo.kind) == SVNWCDbKind.File && (nodeInfo.is(NodeInfo.hadProps) || nodeInfo.is(NodeInfo.propsMod))) {
            SVNProperties properties = context.getDb().readProperties(localAbsPath);
            result.set(NodeCommitStatus.symlink, properties.getStringValue(SVNProperty.SPECIAL) != null);
        }
        
        if (nodeInfo.is(NodeInfo.haveBase) && (nodeInfo.lng(NodeInfo.revision) < 0 || nodeStatus == SVNWCDbStatus.Normal)) {
            WCDbBaseInfo baseInfo =context.getDb().getBaseInfo(localAbsPath, BaseInfoField.revision, BaseInfoField.updateRoot);
            result.set(NodeCommitStatus.revision, baseInfo.revision);
            result.set(NodeCommitStatus.updateRoot, baseInfo.updateRoot);
        } else {
            result.set(NodeCommitStatus.updateRoot, false);
        }
        SVNLock lock = nodeInfo.get(NodeInfo.lock);
        result.set(NodeCommitStatus.lockToken, lock != null ? lock.getID() : null);
        nodeInfo.release();
        return result;
    }


    private static void bailOnTreeConflictedChildren(SVNWCContext context, File localAbsPath, SVNNodeKind kind, SVNDepth depth, Collection<String> changelistsSet) throws SVNException {
        if ((depth == SVNDepth.EMPTY) || (kind != SVNNodeKind.DIR)) {
            return;
        }
        Map<String, SVNTreeConflictDescription> conflicts = context.getDb().opReadAllTreeConflicts(localAbsPath);
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }
        for (SVNTreeConflictDescription conflict : conflicts.values()) {
            if ((conflict.getNodeKind() == SVNNodeKind.DIR) && (depth == SVNDepth.FILES)) {
                continue;
            }
            if (!context.isChangelistMatch(localAbsPath, changelistsSet)) {
                continue;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", conflict.getPath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    private static void collectLocks(final SVNWCContext context, File path, final Map<SVNURL, String> lockTokens) throws SVNException {
        ISVNWCNodeHandler nodeHandler = new ISVNWCNodeHandler() {
            public void nodeFound(File localAbspath, SVNWCDbKind kind) throws SVNException {
                SVNWCDbLock nodeLock = context.getNodeLock(localAbspath);
                if (nodeLock == null || nodeLock.token == null) {
                    return;
                }
                SVNURL url = context.getNodeUrl(localAbspath);
                if (url != null) {
                    lockTokens.put(url, nodeLock.token);
                }
            }
        };
        context.nodeWalkChildren(path, nodeHandler, false, SVNDepth.INFINITY, null);
    }
    
    private static void bailOnTreeConflictedAncestor(SVNWCContext context, File firstAbspath) throws SVNException {
        File localAbspath;
        File parentAbspath;
        boolean wcRoot;
        boolean treeConflicted;
        localAbspath = firstAbspath;
        while (true) {
            wcRoot = context.checkWCRoot(localAbspath, false).wcRoot;
            if (wcRoot) {
                break;
            }
            parentAbspath = SVNFileUtil.getFileDir(localAbspath);
            treeConflicted = context.getConflicted(parentAbspath, false, false, true).treeConflicted;
            if (treeConflicted) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in tree-conflict", localAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
            localAbspath = parentAbspath;
        }
    }
}
