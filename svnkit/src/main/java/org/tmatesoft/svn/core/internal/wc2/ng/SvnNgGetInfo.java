package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCNodeReposInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCSchedule;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ScheduleInternalInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeOriginInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.PristineInfo;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnSchedule;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnWorkingCopyInfo;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgGetInfo extends SvnNgOperationRunner<SvnGetInfo> implements ISVNWCNodeHandler {
    
    private boolean hasRootTreeConflict;
    private boolean isFirstInfo;
    private Map<File, SVNTreeConflictDescription> treeConflicts;
    
    public void reset() {
        hasRootTreeConflict = false;
        isFirstInfo = false;
        treeConflicts = null;
    }
    
    @Override
    protected void run(SVNWCContext context) throws SVNException {
        hasRootTreeConflict = false;
        isFirstInfo = true;
        getTreeConflicts().clear();
        
        if (getOperation().isFetchActualOnly()) {
            SVNTreeConflictDescription treeConflict = context.getDb().opReadTreeConflict(getFirstTarget());
            if (treeConflict != null) {
                hasRootTreeConflict = true;
                getTreeConflicts().put(getFirstTarget(), treeConflict);
            }
        }
        
        try {
            context.nodeWalkChildren(getFirstTarget(), this, getOperation().isFetchExcluded(), getOperation().getDepth());
        } catch (SVNException e) {
            if (!(e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND && hasRootTreeConflict)) {
                throw e;
            }
        }
        SVNWCNodeReposInfo reposInfo = null; 
        if (!getTreeConflicts().isEmpty()) {
            reposInfo = getContext().getNodeReposInfo(getFirstTarget());
            if (reposInfo.reposRootUrl == null) {
                reposInfo = null;
            }
        }
        for (File target: getTreeConflicts().keySet()) {
            SVNTreeConflictDescription treeConflict = getTreeConflicts().get(target);
            if (isDepthIncludes(target, getOperation().getDepth(), treeConflict.getPath(), treeConflict.getNodeKind())) {
                SvnInfo unversionedInfo = buildUnversionedInfo(target);
                Collection<SVNConflictDescription> conflicts = new ArrayList<SVNConflictDescription>(1);
                conflicts.add(treeConflict);
                unversionedInfo.getWcInfo().setConflicts(conflicts);
                if (reposInfo != null) {
                    unversionedInfo.setRepositoryRootURL(reposInfo.reposRootUrl);
                    unversionedInfo.setRepositoryUUID(reposInfo.reposUuid);
                }
                getOperation().getReceiver().receive(SvnTarget.fromFile(target), unversionedInfo);
            }
        }
    }

    public void nodeFound(File localAbspath, SVNWCDbKind kind) throws SVNException {
        SvnInfo info = buildInfo(localAbspath, kind);
        if (info == null && isFirstInfo) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{1}'' was not found", localAbspath), 
                    SVNLogType.WC);
        }
        isFirstInfo = false;
        if (info != null) {
            getOperation().getReceiver().receive(SvnTarget.fromFile(localAbspath), info);
        }
        if (getOperation().isFetchActualOnly() && kind == SVNWCDbKind.Dir) {
            Map<String,SVNTreeConflictDescription> treeConflicts = getContext().getDb().opReadAllTreeConflicts(localAbspath);
            for (String name : treeConflicts.keySet()) {
                getTreeConflicts().put(SVNFileUtil.createFilePath(localAbspath, name), treeConflicts.get(name));
            }
        }
        getTreeConflicts().remove(localAbspath);
    }
    
    private SvnInfo buildUnversionedInfo(File localAbspath) throws SVNException {
        SvnWorkingCopyInfo wcInfo = new SvnWorkingCopyInfo();
        wcInfo.setPath(localAbspath);
        SvnInfo info = new SvnInfo();
        info.setWcInfo(wcInfo);
        info.setRevision(SVNWCContext.INVALID_REVNUM);
        info.setLastChangedRevision(SVNWCContext.INVALID_REVNUM);
        info.setSize(ISVNWCDb.INVALID_FILESIZE);
        info.setLastChangedDate(SVNDate.NULL);
        info.setKind(SVNNodeKind.NONE);
        
        wcInfo.setDepth(SVNDepth.UNKNOWN);
        wcInfo.setRecordedSize(ISVNWCDb.INVALID_FILESIZE);
        wcInfo.setCopyFromRevision(SVNWCContext.INVALID_REVNUM);
        
        return info;
    }
    
    private SvnInfo buildInfo(File localAbspath, SVNWCDbKind kind) throws SVNException {
        SvnInfo info = new SvnInfo();
        SvnWorkingCopyInfo wcInfo = new SvnWorkingCopyInfo();
        wcInfo.setPath(localAbspath);
        info.setWcInfo(wcInfo);
        info.setKind(kind.toNodeKind());
        
        wcInfo.setCopyFromRevision(SVNWCContext.INVALID_REVNUM);
        WCDbInfo readInfo = getContext().getDb().readInfo(localAbspath, 
                InfoField.status, InfoField.kind, InfoField.revision, InfoField.reposRelPath, InfoField.reposRootUrl,
                InfoField.reposUuid,
                InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor, 
                InfoField.depth, InfoField.checksum,
                InfoField.originalReposRelpath, InfoField.originalRootUrl, InfoField.originalUuid, InfoField.originalRevision,
                InfoField.lock, InfoField.translatedSize, InfoField.lastModTime, InfoField.changelist,
                InfoField.conflicted, InfoField.opRoot, InfoField.haveBase);
        
        info.setRevision(readInfo.revision);
        info.setRepositoryRootURL(readInfo.reposRootUrl);
        info.setRepositoryUUID(readInfo.reposUuid);
        info.setLastChangedDate(readInfo.changedDate);
        info.setLastChangedAuthor(readInfo.changedAuthor);
        info.setLastChangedRevision(readInfo.changedRev);
        
        wcInfo.setDepth(readInfo.depth);
        wcInfo.setChecksum(toChecksum(readInfo.checksum));
        wcInfo.setRecordedSize(readInfo.translatedSize);
        wcInfo.setRecordedTime(readInfo.lastModTime);
        wcInfo.setChangelist(readInfo.changelist);
        
        File reposRelPath = readInfo.reposRelPath;
        
        if (readInfo.originalRootUrl != null) {
            info.setRepositoryRootURL(readInfo.originalRootUrl);
            info.setRepositoryUUID(readInfo.originalUuid);
        }
        
        if (readInfo.status == SVNWCDbStatus.Added) {
            if (readInfo.originalReposRelpath != null) {
                info.setRevision(readInfo.originalRevision);
                reposRelPath = readInfo.originalReposRelpath;
                
                if (readInfo.opRoot) {
                    wcInfo.setCopyFromUrl(SVNWCUtils.join(info.getRepositoryRootUrl(), readInfo.originalReposRelpath));
                    wcInfo.setCopyFromRevision(readInfo.originalRevision);
                }
            } else if (readInfo.opRoot) {
                WCDbAdditionInfo addInfo = getContext().getDb().scanAddition(localAbspath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid);
                info.setRepositoryRootURL(addInfo.reposRootUrl);
                info.setRepositoryUUID(addInfo.reposUuid);
                if (readInfo.haveBase) {
                    long baseRev = getContext().getDb().getBaseInfo(localAbspath, BaseInfoField.revision).revision;
                    info.setRevision(baseRev);
                }
            } else {
                Structure<NodeOriginInfo> nodeOrigin = getContext().getNodeOrigin(localAbspath, true);
                info.setRepositoryRootURL(nodeOrigin.<SVNURL>get(NodeOriginInfo.reposRootUrl));
                info.setRepositoryUUID(nodeOrigin.text(NodeOriginInfo.reposUuid));
                info.setRevision(nodeOrigin.lng(NodeOriginInfo.revision));
                nodeOrigin.release();
            }
            
            ScheduleInternalInfo scheduleInfo = getContext().getNodeScheduleInternal(localAbspath, true, false);
            wcInfo.setSchedule(toSchedule(scheduleInfo.schedule));
            info.setUrl(getContext().getNodeUrl(localAbspath));
            
        } else if (readInfo.status == SVNWCDbStatus.Deleted) {
            Structure<PristineInfo> pristineInfo = getContext().getDb().readPristineInfo(localAbspath);
            
            info.setLastChangedRevision(pristineInfo.lng(PristineInfo.changed_rev));
            info.setLastChangedDate(pristineInfo.<SVNDate>get(PristineInfo.changed_date));
            info.setLastChangedAuthor(pristineInfo.text(PristineInfo.changed_author));
            wcInfo.setDepth(pristineInfo.<SVNDepth>get(PristineInfo.depth));
            wcInfo.setChecksum(pristineInfo.<SvnChecksum>get(PristineInfo.checksum));
            
            pristineInfo.release();
            
            File workDelAbsPath = getContext().getDb().scanDeletion(localAbspath, DeletionInfoField.workDelAbsPath).workDelAbsPath;
            if (workDelAbsPath != null) {
                File addedAbsPath = SVNFileUtil.getParentFile(workDelAbsPath);
                
                WCDbAdditionInfo additionInfo = getContext().getDb().scanAddition(localAbspath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposRelPath, AdditionInfoField.reposUuid, AdditionInfoField.originalRevision);
                reposRelPath = additionInfo.reposRelPath;
                info.setRepositoryRootURL(additionInfo.reposRootUrl);
                info.setRepositoryUUID(additionInfo.reposUuid);
                info.setRevision(additionInfo.originalRevision);
                File p = SVNFileUtil.createFilePath(reposRelPath, SVNWCUtils.skipAncestor(addedAbsPath, localAbspath));
                
                info.setUrl(SVNWCUtils.join(info.getRepositoryRootUrl(), p));
            } else {
                WCDbBaseInfo baseInfo = getContext().getDb().getBaseInfo(localAbspath, BaseInfoField.revision, BaseInfoField.reposRelPath, BaseInfoField.reposRootUrl, BaseInfoField.reposUuid);
                reposRelPath = baseInfo.reposRelPath;
                info.setRevision(baseInfo.revision);
                info.setRepositoryRootURL(baseInfo.reposRootUrl);
                info.setRepositoryUUID(baseInfo.reposUuid);
                
                info.setUrl(SVNWCUtils.join(info.getRepositoryRootUrl(), reposRelPath));
            }
            wcInfo.setSchedule(SvnSchedule.DELETE);          
        } else if (readInfo.status == SVNWCDbStatus.NotPresent || readInfo.status == SVNWCDbStatus.ServerExcluded) {
            return null;
        } else {
            info.setUrl(SVNWCUtils.join(info.getRepositoryRootUrl(), reposRelPath));
            wcInfo.setSchedule(SvnSchedule.NORMAL);          
        }
        
        if (readInfo.status == SVNWCDbStatus.Excluded) {
            wcInfo.setDepth(SVNDepth.EXCLUDE);            
        }
        info.setSize(ISVNWCDb.INVALID_FILESIZE);
        wcInfo.setWcRoot(getContext().getDb().getWCRoot(localAbspath));
        
        if (readInfo.conflicted) {
            wcInfo.setConflicts(getContext().getDb().readConflicts(localAbspath));
        }
        if (readInfo.lock != null) {
            SVNLock lock = new SVNLock(null, readInfo.lock.token, readInfo.lock.owner, null, readInfo.lock.date, null);
            info.setLock(lock);
        }
        return info;
    }
    
    private SvnChecksum toChecksum(SVNChecksum checksum) {
        if (checksum == null) {
            return null;
        }
        SvnChecksum result = new SvnChecksum();
        result.setDigest(checksum.getDigest());
        result.setKind(checksum.getKind() == SVNChecksumKind.MD5 ? SvnChecksum.Kind.md5 : SvnChecksum.Kind.sha1);
        return result;
    }

    private SvnSchedule toSchedule(SVNWCSchedule schedule) {
        if (schedule == SVNWCSchedule.add) {
            return SvnSchedule.ADD;
        } else if (schedule == SVNWCSchedule.delete) {
            return SvnSchedule.DELETE;
        } else if (schedule == SVNWCSchedule.normal) {
            return SvnSchedule.NORMAL;
        } else if (schedule == SVNWCSchedule.replace) {
            return SvnSchedule.REPLACE;
        }
        return null;
    }

    private boolean isDepthIncludes(File rootPath, SVNDepth depth, File childPath, SVNNodeKind childKind) {
        if (depth == SVNDepth.INFINITY) {
            return true;
        }
        File childParentPath = SVNFileUtil.getParentFile(childPath);
        if (depth == SVNDepth.IMMEDIATES) {
            return rootPath.equals(childParentPath);
        } else if (depth == SVNDepth.FILES) {
            return childKind == SVNNodeKind.FILE && rootPath.equals(childParentPath);
            
        }
        return rootPath.equals(childPath);
    }

    private Map<File, SVNTreeConflictDescription> getTreeConflicts() {
        if (treeConflicts == null) {
            treeConflicts = new HashMap<File, SVNTreeConflictDescription>();
        }
        return treeConflicts;
    }
}
