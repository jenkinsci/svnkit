package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.AdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbShared;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgWcToWcCopy extends SvnNgOperationRunner<Long, SvnCopy> {

    @Override
    public boolean isApplicable(SvnCopy operation, SvnWcGeneration wcGeneration) throws SVNException {
        return getOperation().getSource().isFile() && 
            getOperation().getTarget().isFile() &&
            getOperation().getRevision().isLocal();
    }
    
    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        File source = getOperation().getSource().getFile();
        File dst = getOperation().getTarget().getFile();
        
        copy(context, source, dst, false);
        
        // local copy
        return new Long(-1);
    }

    
    protected void copy(SVNWCContext context, File source, File dst, boolean metadataOnly) throws SVNException {
        File dstDirectory = SVNFileUtil.getParentFile(dst);
        
        Structure<NodeInfo> srcInfo = null;
        try {
            srcInfo = context.getDb().readInfo(source, NodeInfo.status, NodeInfo.kind, NodeInfo.reposRootUrl, NodeInfo.reposUuid, NodeInfo.checksum, NodeInfo.conflicted);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", source);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            throw e;
        }
        
        ISVNWCDb.SVNWCDbStatus srcStatus = srcInfo.get(NodeInfo.status);
        switch (srcStatus) {
        case Deleted:
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, 
                    "Deleted node ''{0}'' can''t be copied.", source);
            SVNErrorManager.error(err1, SVNLogType.WC);
            break;
        case Excluded:
        case ServerExcluded:
        case NotPresent:
            SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, 
                    "The node ''{0}'' was not found.", source);
            SVNErrorManager.error(err2, SVNLogType.WC);
        default:
            break;
        }
        
        Structure<NodeInfo> dstDirInfo = context.getDb().
            readInfo(dst, NodeInfo.status, NodeInfo.reposRootUrl, NodeInfo.reposUuid);

        SVNURL dstReposRootUrl = dstDirInfo.get(NodeInfo.reposRootUrl);
        String dstReposUuid = dstDirInfo.get(NodeInfo.reposUuid);
        SVNWCDbStatus dstDirStatus = dstDirInfo.get(NodeInfo.status);
        
        dstDirInfo.release();
        
        SVNURL srcReposRootUrl = srcInfo.get(NodeInfo.reposRootUrl);
        String srcReposUuid = srcInfo.get(NodeInfo.reposUuid);
        ISVNWCDb.SVNWCDbKind srcKind = srcInfo.get(NodeInfo.kind);
        SvnChecksum srcChecksum = srcInfo.get(NodeInfo.checksum);
        boolean srcConflicted = srcInfo.is(NodeInfo.conflicted); 
        
        if (srcReposRootUrl == null) {
            if (srcStatus == SVNWCDbStatus.Added) {
                Structure<AdditionInfo> additionInfo = SvnWcDbShared.scanAddition((SVNWCDb) context.getDb(), source, AdditionInfo.reposRootUrl, AdditionInfo.reposUuid);
                srcReposRootUrl = additionInfo.get(AdditionInfo.reposRootUrl);
                srcReposUuid = additionInfo.get(AdditionInfo.reposUuid);
                additionInfo.release();
            } else {
                WCDbRepositoryInfo reposInfo = context.getDb().scanBaseRepository(source, RepositoryInfoField.rootUrl, RepositoryInfoField.uuid);
                srcReposRootUrl = reposInfo.rootUrl;
                srcReposUuid = reposInfo.uuid;
            }
        }
        
        if (dstReposRootUrl == null) {
            if (dstDirStatus == SVNWCDbStatus.Added) {
                Structure<AdditionInfo> additionInfo = SvnWcDbShared.scanAddition((SVNWCDb) context.getDb(), dstDirectory, AdditionInfo.reposRootUrl, AdditionInfo.reposUuid);
                dstReposRootUrl = additionInfo.get(AdditionInfo.reposRootUrl);
                dstReposUuid = additionInfo.get(AdditionInfo.reposUuid);
                additionInfo.release();
            } else {
                WCDbRepositoryInfo reposInfo = context.getDb().scanBaseRepository(dstDirectory, RepositoryInfoField.rootUrl, RepositoryInfoField.uuid);
                dstReposRootUrl = reposInfo.rootUrl;
                dstReposUuid = reposInfo.uuid;
            }
        }
        
        if (!srcReposRootUrl.equals(dstReposRootUrl) || !srcReposUuid.equals(dstReposUuid)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                    "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''", dst, srcReposRootUrl, dstReposRootUrl);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (dstDirStatus == SVNWCDbStatus.Deleted) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE, 
                    "Cannot copy to ''{0}'', as it is scheduled for deletion", dst);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        try {
            Structure<NodeInfo> dstInfo = context.getDb().readInfo(dst, NodeInfo.status);
            SVNWCDbStatus dstStatus = dstInfo.get(NodeInfo.status);
            switch (dstStatus) {
            case Excluded:
                SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "''{0}'' is already under version control but is excluded.", dst);
                SVNErrorManager.error(err1, SVNLogType.WC);
                break;
            case ServerExcluded:
                SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "''{0}'' is already under version control", dst);
                SVNErrorManager.error(err2, SVNLogType.WC);
                break;
            case Deleted:
            case NotPresent:
                break;
            default:
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "There is already a versioned item ''{0}''", dst);
                SVNErrorManager.error(err, SVNLogType.WC);
                break;
            }
            dstInfo.release();
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
        }
        
        if (!metadataOnly) {
            SVNFileType dstType = SVNFileType.getType(dst);
            if (dstType != SVNFileType.NONE) {
                SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "''{0}'' already exists and is in the way", dst);
                SVNErrorManager.error(err2, SVNLogType.WC);
            }
        }
        
        File tmpDir = context.getDb().getWCRootTempDir(dst);
        if (srcKind == SVNWCDbKind.File || srcKind == SVNWCDbKind.Symlink) {
            copyVersionedFile(source, dst, dst, tmpDir, srcChecksum, metadataOnly, srcConflicted);
        } else {
            
        }
        
    }

    private void copyVersionedFile(File source, File dst, File dstOpRoot, File tmpDir, SvnChecksum srcChecksum, boolean metadataOnly, boolean conflicted) throws SVNException {
        if (srcChecksum != null) {
            // TODO copy pristine to another wc if needed.
        }
        
        File toCopy = source;
        if (!metadataOnly) {
            if (conflicted) {
                File conflictWorking = null;
                List<SVNConflictDescription> conflicts = getWcContext().getDb().readConflicts(source);
                
                for (SVNConflictDescription conflictDescription : conflicts) {
                    if (conflictDescription.isTextConflict()) {
                        conflictWorking = conflictDescription.getPath();
                        break;
                    }
                }
                if (conflictWorking != null) {
                    if (SVNFileType.getType(conflictWorking) == SVNFileType.FILE) {
                        toCopy = conflictWorking;
                    }
                }
            }
        }
        
        File tmpDst = copyToTmpDir(toCopy, tmpDir, true);
        SVNSkel workItems = null;
        if (tmpDst != null) {
            boolean needsLock = getWcContext().getProperty(source, SVNProperty.NEEDS_LOCK) != null;
            if (needsLock) {
                SVNFileUtil.setReadonly(tmpDst, false);
            }
            SVNSkel workItem = getWcContext().wqBuildFileMove(tmpDst, dst);
            workItems = getWcContext().wqMerge(workItems, workItem);
            
            SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(tmpDst));
            if (kind == SVNNodeKind.DIR) {
                if (!getWcContext().isTextModified(source, false)) {
                    SVNSkel workItem2 = getWcContext().wqBuildRecordFileinfo(dst, null);
                    workItems = getWcContext().wqMerge(workItems, workItem2);
                }
            }
        }
        getWcContext().getDb().opCopy(source, dst, workItems);
        getWcContext().wqRun(SVNFileUtil.getParentFile(dst));
        if (getOperation().getEventHandler() != null) {
            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, -1, SVNEventAction.ADD, SVNEventAction.ADD, null, null, 1, 1);
            getOperation().getEventHandler().handleEvent(event, -1);
        }
    }
    
    private File copyToTmpDir(File source, File tmpDir, boolean recursive) throws SVNException {
        SVNFileType sourceType = SVNFileType.getType(source); 
        boolean special =  sourceType == SVNFileType.SYMLINK;
        if (sourceType == SVNFileType.NONE) {
            return null;
        } else if (sourceType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, 
                    "Source ''{0}'' is unexpected kind", source);
            SVNErrorManager.error(err, SVNLogType.WC);
        } 
        
        File dstPath = SVNFileUtil.createUniqueFile(tmpDir, source.getName(), ".tmp", false);
        if (sourceType == SVNFileType.DIRECTORY || special) {
            SVNFileUtil.deleteFile(dstPath);
        }
        
        if (sourceType == SVNFileType.DIRECTORY) {
            if (recursive) {
                SVNFileUtil.copyDirectory(source, dstPath, true, getOperation().getEventHandler());
            } else {
                SVNFileUtil.ensureDirectoryExists(dstPath);
            }
        } else if (!special) {
            SVNFileUtil.copyFile(source, dstPath, false);
        } else {
            SVNFileUtil.copy(source, dstPath, false, true);
        }
        return tmpDir;
    }
}
