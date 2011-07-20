package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCSchedule;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ScheduleInternalInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnFileKind;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnSchedule;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnWorkingCopyInfo;

public class SvnNgGetInfo extends SvnNgOperationRunner<SvnGetInfo> implements ISVNWCNodeHandler {
    
    private boolean hasRootTreeConflict;
    private Map<File, SVNTreeConflictDescription> treeConflicts;

    @Override
    protected void run(SVNWCContext context) throws SVNException {
        if (getOperation().isFetchActualOnly()) {
            SVNTreeConflictDescription treeConflict = context.getDb().opReadTreeConflict(getFirstTarget());
            if (treeConflict != null) {
                hasRootTreeConflict = true;
                treeConflicts.put(getFirstTarget(), treeConflict);
            }
        }
        
        try {
            context.nodeWalkChildren(getFirstTarget(), this, getOperation().isFetchExcluded(), getOperation().getDepth());
        } catch (SVNException e) {
            if (!(e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND && hasRootTreeConflict)) {
                throw e;
            }
        }
        for (File target: treeConflicts.keySet()) {
            SVNTreeConflictDescription treeConflict = treeConflicts.get(target);
            if (isDepthIncludes(target, getOperation().getDepth(), treeConflict.getPath(), treeConflict.getNodeKind())) {
                // TODO
                // build unversioned info for path
            }
        }
    }

    public void nodeFound(File localAbspath, SVNWCDbKind kind) throws SVNException {
        SvnInfo info = buildInfo(localAbspath, kind);
        getOperation().getReceiver().receive(SvnTarget.fromFile(localAbspath), info);
    }
    
    private SvnInfo buildInfo(File localAbspath, SVNWCDbKind kind) throws SVNException {
        SvnInfo info = new SvnInfo();
        SvnWorkingCopyInfo wcInfo = new SvnWorkingCopyInfo();
        info.setWcInfo(wcInfo);
        info.setKind(toFileKind(kind));
        
        wcInfo.setCopyFromRevision(SVNWCContext.INVALID_REVNUM);
        WCDbInfo readInfo = getContext().getDb().readInfo(localAbspath, 
                InfoField.status, InfoField.kind, InfoField.revision, InfoField.reposRelPath, 
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
                // TODO
//                getContext().getDb().get
            }
            ScheduleInternalInfo scheduleInfo = getContext().getNodeScheduleInternal(localAbspath, true, false);
            wcInfo.setSchedule(toSchedule(scheduleInfo.schedule));
            info.setUrl(getContext().getNodeUrl(localAbspath));
            
        } else if (readInfo.status == SVNWCDbStatus.Deleted) {
            // TODO
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
        info.setSize(-1);
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
        // TODO Auto-generated method stub
        return null;
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

    private static SvnFileKind toFileKind(SVNWCDbKind kind) {
        if (kind == SVNWCDbKind.Dir) {
            return SvnFileKind.DIRECTORY; 
        } else if (kind == SVNWCDbKind.File) {
            return SvnFileKind.FILE; 
        } else if (kind == SVNWCDbKind.Symlink) {
            return SvnFileKind.SYMLINK; 
        } 
        return SvnFileKind.UNKNOWN;
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

}
