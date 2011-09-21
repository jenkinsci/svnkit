package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbRevert;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbRevert.RevertInfo;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgRevert extends SvnNgOperationRunner<SvnRevert, SvnRevert> {

    @Override
    protected SvnRevert run(SVNWCContext context) throws SVNException {
        boolean useCommitTimes = getOperation().getOptions().isUseCommitTimes();
        
        for (SvnTarget target : getOperation().getTargets()) {
            checkCancelled();
            
            boolean isWcRoot = context.getDb().isWCRoot(target.getFile());
            File lockTarget = isWcRoot ? target.getFile() : SVNFileUtil.getParentFile(target.getFile());
            File lockRoot = context.acquireWriteLock(lockTarget, false, true);
            try {
                revert(target.getFile(), getOperation().getDepth(), useCommitTimes, getOperation().getApplicableChangelists());
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                if (err.getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND 
                        || err.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE 
                        || err.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    SVNEvent event = SVNEventFactory.createSVNEvent(target.getFile(), SVNNodeKind.NONE, null, -1, SVNEventAction.SKIP, SVNEventAction.REVERT, err, null, -1, -1);
                    handleEvent(event);
                    continue;
                }
                if (!useCommitTimes) {
                    sleepForTimestamp();
                }
                throw e;
            } finally {
                context.releaseWriteLock(lockRoot);
            }
        }
        if (!useCommitTimes) {
            sleepForTimestamp();
        }
        return getOperation();
    }

    private void revert(File localAbsPath, SVNDepth depth, boolean useCommitTimes, Collection<String> changelists) throws SVNException {
        if (changelists != null && changelists.size() > 0) {
            revertChangelist(localAbsPath, depth, useCommitTimes, changelists);
            return;
        }
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.INFINITY) {
            revert(localAbsPath, depth, useCommitTimes);
            return;
        }
        if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
            revert(localAbsPath, SVNDepth.EMPTY, useCommitTimes);
            Set<String> children = ((SVNWCDb) getWcContext().getDb()).getWorkingChildren(localAbsPath);
            for (String childName : children) {
                File childAbsPath = SVNFileUtil.createFilePath(localAbsPath, childName);
                if (depth == SVNDepth.FILES) {
                    SVNNodeKind childKind = getWcContext().readKind(childAbsPath, true);
                    if (childKind != SVNNodeKind.FILE) {
                        continue;
                    }
                }
                revert(childAbsPath, SVNDepth.EMPTY, useCommitTimes);
            }
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH);
        SVNErrorManager.error(err, SVNLogType.WC);
        
    }

    private void revertChangelist(File localAbsPath, SVNDepth depth, boolean useCommitTimes, Collection<String> changelists) throws SVNException {
        checkCancelled();
        if (getWcContext().isChangelistMatch(localAbsPath, changelists)) {
            revert(localAbsPath, SVNDepth.EMPTY, useCommitTimes);
        }
        if (depth == SVNDepth.EMPTY) {
            return;
        }
        if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
            depth = SVNDepth.EMPTY;
        }
        Set<String> children = ((SVNWCDb) getWcContext().getDb()).getWorkingChildren(localAbsPath);
        for (String childName : children) {
            File childAbsPath = SVNFileUtil.createFilePath(localAbsPath, childName);
            revertChangelist(childAbsPath, depth, useCommitTimes, changelists);
        }
    }

    private void revert(File localAbsPath, SVNDepth depth, boolean useCommitTimes) throws SVNException {
        File wcRoot = getWcContext().getDb().getWCRoot(localAbsPath);
        if (!localAbsPath.equals(wcRoot)) {
            getWcContext().writeCheck(SVNFileUtil.getParentFile(localAbsPath));
        } else {
            getWcContext().writeCheck(localAbsPath);
        }
        try {
            getWcContext().getDb().opRevert(localAbsPath, depth);
            restore(localAbsPath, depth, useCommitTimes);
        } finally {
            SvnWcDbRevert.dropRevertList(getWcContext(), localAbsPath);
        }
    }

    private void restore(File localAbsPath, SVNDepth depth, boolean useCommitTimes) throws SVNException {
        checkCancelled();
        
        Structure<RevertInfo> revertInfo = SvnWcDbRevert.readRevertInfo(getWcContext(), localAbsPath);
        ISVNWCDb.SVNWCDbStatus status = SVNWCDbStatus.Normal;
        ISVNWCDb.SVNWCDbKind kind = SVNWCDbKind.Unknown;
        long recordedSize = -1;
        long recordedTime = 0;
        boolean notifyRequired = revertInfo.is(RevertInfo.reverted);
        
        try {
            Structure<NodeInfo> nodeInfo = getWcContext().getDb().readInfo(localAbsPath, NodeInfo.status, NodeInfo.kind,
                    NodeInfo.recordedSize, NodeInfo.recordedTime);
            status = nodeInfo.get(NodeInfo.status);
            kind = nodeInfo.get(NodeInfo.kind);
            recordedSize = nodeInfo.lng(NodeInfo.recordedSize);
            recordedTime = nodeInfo.lng(NodeInfo.recordedTime);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                if (!revertInfo.is(RevertInfo.copiedHere)) {
                    if (notifyRequired) {
                        handleEvent(SVNEventFactory.createSVNEvent(localAbsPath, SVNNodeKind.NONE, null, -1, SVNEventAction.REVERT, 
                                SVNEventAction.REVERT, null, null, -1, -1));
                    }
                    SvnWcDbRevert.notifyRevert(getWcContext(), localAbsPath, this);
                    return;
                } 
            } else {
                throw e;
            }
        }
        
        SVNFileType filetype = SVNFileType.getType(localAbsPath);
        SVNNodeKind onDisk = null;
        boolean special = false;
        if (filetype == SVNFileType.NONE) {
            onDisk = SVNNodeKind.NONE;
            special = false;
        } else {
            if (filetype == SVNFileType.FILE || filetype == SVNFileType.SYMLINK) {
                onDisk = SVNNodeKind.FILE;
            } else if (filetype == SVNFileType.DIRECTORY) {
                onDisk = SVNNodeKind.DIR;
            } else {
                onDisk = SVNNodeKind.UNKNOWN;
            }
            special = filetype == SVNFileType.SYMLINK;
        }
        
        if (revertInfo.is(RevertInfo.copiedHere)) {
            if (revertInfo.get(RevertInfo.kind) == SVNWCDbKind.File && onDisk == SVNNodeKind.FILE) {
                SVNFileUtil.deleteFile(localAbsPath);
                onDisk = SVNNodeKind.NONE;
            } else if (revertInfo.get(RevertInfo.kind) == SVNWCDbKind.Dir && onDisk == SVNNodeKind.DIR) {
                boolean removed = restoreCopiedDirectory(localAbsPath, true);
                if (removed) {
                    onDisk = SVNNodeKind.NONE;
                }
            }
        }
        
        if (onDisk != SVNNodeKind.NONE
                && status != SVNWCDbStatus.ServerExcluded
                && status != SVNWCDbStatus.Deleted
                && status != SVNWCDbStatus.Excluded
                && status != SVNWCDbStatus.NotPresent) {
            if (onDisk == SVNNodeKind.DIR && kind != SVNWCDbKind.Dir) {
                SVNFileUtil.deleteAll(localAbsPath, true, this);
                onDisk = SVNNodeKind.NONE;
            } else if (onDisk == SVNNodeKind.FILE && kind != SVNWCDbKind.File) {
                SVNFileUtil.deleteFile(localAbsPath);
                onDisk = SVNNodeKind.NONE;
            } else if (onDisk == SVNNodeKind.FILE) {
                SVNProperties pristineProperties = getWcContext().getDb().readPristineProperties(localAbsPath);
                boolean modified = false;
                if (SVNFileUtil.symlinksSupported()) {
                    String specialProperty = pristineProperties.getStringValue(SVNProperty.SPECIAL);
                    if ((specialProperty != null) != special) {
                        SVNFileUtil.deleteFile(localAbsPath);
                        onDisk = SVNNodeKind.NONE;
                    }
                } else {
                    if (recordedSize != -1
                            && recordedTime != 0
                            && recordedSize == localAbsPath.length()
                            && recordedTime == localAbsPath.lastModified()) {
                        modified = false;
                    } else {
                        modified = getWcContext().isTextModified(localAbsPath, true, false);
                    }
                }
                
                if (modified) {
                    SVNFileUtil.deleteFile(localAbsPath);
                    onDisk = SVNNodeKind.NONE;
                } else {
                    boolean isReadOnly = !localAbsPath.canWrite();
                    boolean needsLock = pristineProperties.getStringValue(SVNProperty.NEEDS_LOCK) != null;
                    if (needsLock && !isReadOnly) {
                        SVNFileUtil.setReadonly(localAbsPath, true);
                        notifyRequired = true;
                    } else if (!needsLock && isReadOnly) {
                        SVNFileUtil.setReadonly(localAbsPath, false);
                        notifyRequired = true;
                    }
                }
                if (!SVNFileUtil.symlinksSupported() || !special) {
                    boolean executable = SVNFileUtil.isExecutable(localAbsPath);
                    boolean executableProperty = pristineProperties.getStringValue(SVNProperty.EXECUTABLE) != null;
                    if (executableProperty && !executable) {
                        SVNFileUtil.setExecutable(localAbsPath, true);
                        notifyRequired = true;
                    } else if (!executableProperty && executable) {
                        SVNFileUtil.setExecutable(localAbsPath, false);
                        notifyRequired = true;
                    }
                }
            }
        }

        if (onDisk == SVNNodeKind.NONE
                && status != SVNWCDbStatus.ServerExcluded
                && status != SVNWCDbStatus.Deleted
                && status != SVNWCDbStatus.Excluded
                && status != SVNWCDbStatus.NotPresent) {
            if (kind == SVNWCDbKind.Dir) {
                SVNFileUtil.ensureDirectoryExists(localAbsPath);
            } else if (kind == SVNWCDbKind.File) {
                SVNSkel workItem = getWcContext().wqBuildFileInstall(localAbsPath, null, useCommitTimes, true);
                getWcContext().getDb().addWorkQueue(localAbsPath, workItem);
                getWcContext().wqRun(localAbsPath);
            }
            notifyRequired = true;
        }
        
        if (revertInfo.hasValue(RevertInfo.conflictOld)) {
            notifyRequired |= SVNFileUtil.deleteFile(revertInfo.<File>get(RevertInfo.conflictOld));
        }
        if (revertInfo.hasValue(RevertInfo.conflictNew)) {
            notifyRequired |= SVNFileUtil.deleteFile(revertInfo.<File>get(RevertInfo.conflictNew));
        }
        if (revertInfo.hasValue(RevertInfo.conflictWorking)) {
            notifyRequired |= SVNFileUtil.deleteFile(revertInfo.<File>get(RevertInfo.conflictWorking));
        }
        if (revertInfo.hasValue(RevertInfo.propReject)) {
            notifyRequired |= SVNFileUtil.deleteFile(revertInfo.<File>get(RevertInfo.propReject));
        }

        if (notifyRequired) {
            handleEvent(SVNEventFactory.createSVNEvent(localAbsPath, SVNNodeKind.NONE, null, -1, SVNEventAction.REVERT, 
                    SVNEventAction.REVERT, null, null, -1, -1));
        }
        
        if (depth == SVNDepth.INFINITY && kind == SVNWCDbKind.Dir) {
            restoreCopiedDirectory(localAbsPath, false);
            
            Set<String> children = ((SVNWCDb) getWcContext().getDb()).getWorkingChildren(localAbsPath);
            for (String childName : children) {
                File childAbsPath = SVNFileUtil.createFilePath(localAbsPath, childName);
                restore(childAbsPath, depth, useCommitTimes);
            }
        }
        
        SvnWcDbRevert.notifyRevert(getWcContext(), localAbsPath, this);
    }

    private boolean restoreCopiedDirectory(File localAbsPath, boolean removeSelf) throws SVNException {
        boolean selfRemoved = false;
        
        Map<File, SVNWCDbKind> children = SvnWcDbRevert.readRevertCopiedChildren(getWcContext(), localAbsPath);
        
        for (File child : children.keySet()) {
            checkCancelled();
            SVNWCDbKind childKind = children.get(child);
            if (childKind != SVNWCDbKind.File) {
                continue;
            }
            SVNFileType childFileType = SVNFileType.getType(child);
            if (childFileType != SVNFileType.FILE && childFileType != SVNFileType.SYMLINK) {
                continue;
            }
            SVNFileUtil.deleteFile(child);
        }

        for (File child : children.keySet()) {
            checkCancelled();
            SVNWCDbKind childKind = children.get(child);
            if (childKind != SVNWCDbKind.Dir) {
                continue;
            }
            SVNFileUtil.deleteFile(child);
        }
        
        if (removeSelf) {
            SVNFileUtil.deleteFile(localAbsPath);
            if (SVNFileType.getType(localAbsPath) == SVNFileType.NONE) {
                selfRemoved = true;
            }
        }        
        return selfRemoved;
    }

}
