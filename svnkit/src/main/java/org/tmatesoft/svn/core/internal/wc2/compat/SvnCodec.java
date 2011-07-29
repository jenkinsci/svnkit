package org.tmatesoft.svn.core.internal.wc2.compat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCConflictDescription17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnSchedule;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnWorkingCopyInfo;
import org.tmatesoft.svn.core.wc2.hooks.ISvnFileListHook;

public class SvnCodec {
    
    public static SvnStatus status(SVNStatus status) {
        SvnStatus result = new SvnStatus();
        result.setUserData(status);
        
        result.setPath(status.getFile());
        result.setChangedAuthor(status.getAuthor());
        result.setChangedDate(SVNDate.fromDate(status.getCommittedDate()));
        result.setChangedRevision(revisionNumber(status.getCommittedRevision()));
        result.setChangelist(status.getChangelistName());
        result.setConflicted(status.isConflicted());
        result.setCopied(status.isCopied());
        result.setDepth(status.getDepth());
        result.setFileExternal(status.isFileExternal());
        // TODO
        //result.setFileSize()
        result.setKind(status.getKind());
        result.setLock(status.getLocalLock());
        
        // combine node and contents?
        result.setNodeStatus(status.getNodeStatus());
        result.setTextStatus(status.getContentsStatus());
        result.setPropertiesStatus(status.getPropertiesStatus());
        
        result.setRepositoryChangedAuthor(status.getRemoteAuthor());
        result.setRepositoryChangedDate(SVNDate.fromDate(status.getRemoteDate()));
        result.setRepositoryChangedRevision(revisionNumber(status.getRemoteRevision()));
        result.setRepositoryKind(status.getRemoteKind());
        result.setRepositoryLock(status.getRemoteLock());
        
        // combine node and contents?
        result.setRepositoryNodeStatus(status.getRemoteNodeStatus());
        result.setRepositoryTextStatus(status.getRemoteContentsStatus());
        result.setRepositoryPropertiesStatus(status.getRemotePropertiesStatus());
        
        result.setRepositoryRelativePath(status.getRepositoryRelativePath());
        result.setRepositoryRootUrl(status.getRepositoryRootURL());
        result.setRepositoryUuid(status.getRepositoryUUID());
        
        result.setRevision(revisionNumber(status.getRevision()));
        result.setSwitched(status.isSwitched());
        result.setVersioned(status.isVersioned());
        result.setWcLocked(status.isLocked());
        
        return result;
    }
    
    public static long revisionNumber(SVNRevision revision) {
        if (revision == null) {
            return SVNWCContext.INVALID_REVNUM;
        }
        return revision.getNumber();
    } 
    
    public static SVNStatus status(SVNWCContext context, SvnStatus status) throws SVNException {
        if (status.getUserData() instanceof SVNStatus) {
            return (SVNStatus) status.getUserData();
        }
        
        SVNStatus result = new SVNStatus();
        result.setFile(status.getPath());
        result.setKind(status.getKind());
        // TODO filesize
        result.setIsVersioned(status.isVersioned());
        result.setIsConflicted(status.isConflicted());
        
        result.setNodeStatus(status.getNodeStatus());
        result.setContentsStatus(status.getTextStatus());
        result.setPropertiesStatus(status.getPropertiesStatus());

        if (status.getKind() == SVNNodeKind.DIR) {
            result.setIsLocked(status.isWcLocked());
        }
        result.setIsCopied(status.isConflicted());
        result.setRevision(SVNRevision.create(status.getRevision()));
        
        result.setCommittedRevision(SVNRevision.create(status.getChangedRevision()));
        result.setAuthor(status.getChangedAuthor());
        result.setCommittedDate(status.getChangedDate());
        
        result.setRepositoryRootURL(status.getRepositoryRootUrl());
        result.setRepositoryRelativePath(status.getRepositoryRelativePath());
        result.setRepositoryUUID(status.getRepositoryUuid());
        
        result.setIsSwitched(status.isSwitched());
        if (status.isVersioned() && status.isSwitched() && status.getKind() == SVNNodeKind.FILE) {
           // TODO fileExternal
        }
        result.setLocalLock(status.getLock());
        result.setChangelistName(status.getChangelist());
        result.setDepth(status.getDepth());
        
        result.setRemoteKind(status.getRepositoryKind());
        result.setRemoteNodeStatus(status.getRepositoryNodeStatus());
        result.setRemoteContentsStatus(status.getRepositoryTextStatus());
        result.setRemotePropertiesStatus(status.getRepositoryPropertiesStatus());
        result.setRemoteLock(status.getRepositoryLock());
        
        result.setRemoteAuthor(status.getRepositoryChangedAuthor());
        result.setRemoteRevision(SVNRevision.create(status.getRepositoryChangedRevision()));
        result.setRemoteDate(status.getRepositoryChangedDate());
        
        // do all that on demand in SVNStatus class later.
        // compose URL on demand in SVNStatus
        
        if (status.isVersioned() && status.isConflicted()) {
            SVNWCContext.ConflictInfo info = context.getConflicted(status.getPath(), true, true, true);
            if (info.textConflicted) {
                result.setContentsStatus(SVNStatusType.STATUS_CONFLICTED);
            }
            if (info.propConflicted) {
                result.setPropertiesStatus(SVNStatusType.STATUS_CONFLICTED);
            }
            if (info.textConflicted || info.propConflicted) {
                result.setNodeStatus(SVNStatusType.STATUS_CONFLICTED);
            }
        }
        
        if (status.getRepositoryRootUrl() != null && status.getRepositoryRelativePath() != null) {
            SVNURL url = status.getRepositoryRootUrl().appendPath(status.getRepositoryRelativePath(), false);
            result.setURL(url);
            // TODO when may these two urls differ?
            result.setRemoteURL(url);
        }
        
        if (context != null && status.isVersioned() && status.getRevision() == SVNWCContext.INVALID_REVNUM && !status.isCopied()) {
            if (status.getNodeStatus() == SVNStatusType.STATUS_REPLACED) {
                fetchStatusRevision(context, status, result);
            } else if (status.getNodeStatus() == SVNStatusType.STATUS_DELETED ) {
                fetchStatusRevision(context, status, result);
            }
        }
        
        if (context != null && status.isConflicted()) {
            boolean hasTreeConflict = false;
            SVNWCContext.ConflictInfo conflictedInfo = null;
            if (status.isVersioned()) {
                try {
                    conflictedInfo = context.getConflicted(status.getPath(), true, true, true);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UPGRADE_REQUIRED) {
                    } else {
                        throw e;
                    }
                }
                hasTreeConflict = conflictedInfo != null && conflictedInfo.treeConflicted;
            } else {
                hasTreeConflict = true;
            }
        
            if (hasTreeConflict) {
                SVNTreeConflictDescription treeConflictDescription = context.getTreeConflict(status.getPath());
                result.setTreeConflict(treeConflictDescription);
            }
            
            if (conflictedInfo != null) {
                result.setConflictWrkFile(conflictedInfo.localFile);
                result.setConflictOldFile(conflictedInfo.baseFile);
                result.setConflictNewFile(conflictedInfo.repositoryFile);                    
                result.setPropRejectFile(conflictedInfo.propRejectFile);                    
            }
        }
        
        if (result.getNodeStatus() == SVNStatusType.STATUS_ADDED) {
            result.setPropertiesStatus(SVNStatusType.STATUS_NONE);
        }
        result.setWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
        
        return result;
    }
    
    private static void fetchStatusRevision(SVNWCContext context, SvnStatus source, SVNStatus result) throws SVNException {
        Structure<NodeInfo> info = context.getDb().readInfo(source.getPath(), NodeInfo.revision, NodeInfo.changedAuthor, NodeInfo.changedDate, NodeInfo.changedRev, 
                NodeInfo.haveBase, NodeInfo.haveWork, NodeInfo.haveMoreWork, NodeInfo.status);
        
        if (source.getNodeStatus() == SVNStatusType.STATUS_DELETED) {
            result.setAuthor(info.text(NodeInfo.changedAuthor));
            result.setCommittedDate(info.<SVNDate>get(NodeInfo.changedDate));
            result.setCommittedRevision(SVNRevision.create(info.lng(NodeInfo.changedRev)));
        }
        result.setRevision(SVNRevision.create(info.lng(NodeInfo.revision)));
        SVNWCDbStatus st = info.<SVNWCDbStatus>get(NodeInfo.status); 
        if (info.is(NodeInfo.haveWork) || info.lng(NodeInfo.revision) == SVNWCContext.INVALID_REVNUM || 
                (source.getNodeStatus() == SVNStatusType.STATUS_DELETED && info.lng(NodeInfo.changedRev) == SVNWCContext.INVALID_REVNUM) || 
                (st != SVNWCDbStatus.Added && st != SVNWCDbStatus.Deleted)) {
            info.release();
            
            ISVNWCDb.WCDbBaseInfo binfo = context.getDb().getBaseInfo(source.getPath(), BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedAuthor, BaseInfoField.changedDate);            
            if (source.getNodeStatus() == SVNStatusType.STATUS_DELETED) {
                result.setAuthor(binfo.changedAuthor);
                result.setCommittedDate(binfo.changedDate);
                result.setCommittedRevision(SVNRevision.create(binfo.changedRev));
            }
            result.setRevision(SVNRevision.create(binfo.revision));
        } else {
            info.release();
        }
    }
    
    public static SvnInfo info(SVNInfo info) {
        SvnInfo result = new SvnInfo();
        result.setUserData(info);
        result.setKind(info.getKind());
        result.setLastChangedAuthor(info.getAuthor());
        result.setLastChangedDate(SVNDate.fromDate(info.getCommittedDate()));
        result.setLastChangedRevision(info.getCommittedRevision().getNumber());
        result.setLock(info.getLock());
        result.setRepositoryRootURL(info.getRepositoryRootURL());
        result.setRepositoryUuid(info.getRepositoryUUID());
        result.setRevision(info.getRevision().getNumber());
        result.setSize(-1);
        result.setUrl(info.getURL());
        
        SvnWorkingCopyInfo wcInfo = new SvnWorkingCopyInfo();
        
        result.setWcInfo(wcInfo);
        wcInfo.setChangelist(info.getChangelistName());
        if (info.getChecksum() != null) {
            SvnChecksum checksum = new SvnChecksum(SvnChecksum.Kind.md5, info.getChecksum());
            wcInfo.setChecksum(checksum);
        }
        
        if (info.getTreeConflict() != null || 
                info.getConflictWrkFile() != null || info.getConflictNewFile() != null || info.getConflictOldFile() != null || 
                info.getPropConflictFile() != null) {
            Collection<SVNConflictDescription> conflicts = new ArrayList<SVNConflictDescription>();
            if (info.getTreeConflict() != null) {
                conflicts.add(info.getTreeConflict());
            }
            if (info.getConflictWrkFile() != null || info.getConflictNewFile() != null || info.getConflictOldFile() != null) {
                SVNWCConflictDescription17 cd = SVNWCConflictDescription17.createText(info.getFile());
                cd.setTheirFile(info.getConflictNewFile());
                cd.setBaseFile(info.getConflictOldFile());
                cd.setMyFile(info.getConflictWrkFile());
                conflicts.add(cd.toConflictDescription());
            }
            if (info.getPropConflictFile() != null) {
                SVNWCConflictDescription17 cd = SVNWCConflictDescription17.createProp(info.getFile(), info.getKind(), null);
                cd.setTheirFile(info.getPropConflictFile());
                conflicts.add(cd.toConflictDescription());
            }
            wcInfo.setConflicts(conflicts);
        }
        
        wcInfo.setCopyFromRevision(info.getCommittedRevision().getNumber());
        wcInfo.setCopyFromUrl(info.getCopyFromURL());
        wcInfo.setDepth(info.getDepth());
        wcInfo.setPath(info.getFile());
        wcInfo.setRecordedSize(info.getWorkingSize());
        if (info.getTextTime() != null) {
            wcInfo.setRecordedTime(info.getTextTime().getTime());
        }
        wcInfo.setSchedule(SvnSchedule.fromString(info.getSchedule()));
        
        File wcRoot = null;
        try {
            wcRoot = SVNWCUtil.getWorkingCopyRoot(info.getFile(), true);
        } catch (SVNException e) {
        }
        wcInfo.setWcRoot(wcRoot);
        
        return result;
    }
    
    public static SVNInfo info(SvnInfo info) {
        if (info.getUserData() instanceof SVNInfo) {
            return ((SVNInfo) info.getUserData());
        }
        if (info.getWcInfo() == null) {
            String rootPath = info.getRepositoryRootUrl().getPath();
            String itemPath = info.getUrl().getPath();
            itemPath = SVNPathUtil.getPathAsChild(rootPath, itemPath);
            if (itemPath == null) {
                itemPath = "";
            }
            return new SVNInfo(itemPath, info.getUrl(), SVNRevision.create(info.getRevision()), info.getKind(), info.getRepositoryUuid(), info.getRepositoryRootUrl(), 
                    info.getLastChangedRevision(), info.getLastChangedDate(), info.getLastChangedAuthor(), info.getLock(), SVNDepth.UNKNOWN, info.getSize());
        }
        SvnWorkingCopyInfo wcInfo = info.getWcInfo();
        
        String conflictOld = null;
        String conflictNew = null;
        String conflictWorking = null;
        String propRejectFile = null;
        SVNTreeConflictDescription treeConflict = null;
        
        Collection<SVNConflictDescription> conflicts = wcInfo.getConflicts();
        if (conflicts != null) {
            for (SVNConflictDescription conflictDescription : conflicts) {
                if (conflictDescription.isTreeConflict() && conflictDescription instanceof SVNTreeConflictDescription) {
                    treeConflict = (SVNTreeConflictDescription) conflictDescription;
                } else if (conflictDescription.isTextConflict()) {
                    if (conflictDescription.getMergeFiles() != null) {
                        if (conflictDescription.getMergeFiles().getBaseFile() != null) {
                            conflictOld = conflictDescription.getMergeFiles().getBaseFile().getName();
                        }
                        if (conflictDescription.getMergeFiles().getRepositoryFile() != null) {
                            conflictNew = conflictDescription.getMergeFiles().getRepositoryFile().getName();
                        }
                        if (conflictDescription.getMergeFiles().getLocalFile() != null) {
                            conflictWorking = conflictDescription.getMergeFiles().getLocalFile().getName();
                        }
                    }
                } else if (conflictDescription.isPropertyConflict()) {
                    if (conflictDescription.getMergeFiles() != null) {
                        propRejectFile = conflictDescription.getMergeFiles().getRepositoryFile().getName();
                    }
                }
            }
        }
        
        String schedule = wcInfo.getSchedule() != null ? wcInfo.getSchedule().asString() : null;
        return new SVNInfo(wcInfo.getPath(), 
                info.getUrl(), 
                info.getRepositoryRootUrl(), 
                info.getRevision(), 
                info.getKind(), 
                info.getRepositoryUuid(), 
                info.getLastChangedRevision(),
                info.getLastChangedDate() != null ? info.getLastChangedDate().format() : null, 
                info.getLastChangedAuthor(), 
                schedule, 
                wcInfo.getCopyFromUrl(), 
                wcInfo.getCopyFromRevision(), 
                wcInfo.getRecordedTime() > 0 ? SVNWCUtils.readDate(wcInfo.getRecordedTime()).format() : null, 
                null,
                wcInfo.getChecksum() != null ? wcInfo.getChecksum().getDigest() : null, 
                conflictOld, 
                conflictNew, 
                conflictWorking, 
                propRejectFile, 
                info.getLock(), 
                wcInfo.getDepth(), 
                wcInfo.getChangelist(), 
                wcInfo.getRecordedSize(), 
                treeConflict);
    }
    
    public static ISvnFileListHook fileListHook(final ISVNStatusFileProvider provider) {
        if (provider == null) {
            return null;
        }
        return new ISvnFileListHook() {
            public Map<String, File> listFiles(File parent) {
                return provider.getChildrenFiles(parent);
            }
        };
    }
    
    public static ISVNStatusFileProvider fileListProvider(final ISvnFileListHook hook) {
        if (hook == null) {
            return null;
        }
        return new ISVNStatusFileProvider() {
            public Map<String, File> getChildrenFiles(File parent) {
                return hook.listFiles(parent);
            }
        };
    }
}
