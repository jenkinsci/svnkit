package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.PristineContentsInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.AdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.ExternalNodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbExternals;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbReader;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbShared;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgWcToWcCopy extends SvnNgOperationRunner<Void, SvnCopy> {

    @Override
    public boolean isApplicable(SvnCopy operation, SvnWcGeneration wcGeneration) throws SVNException {
        return areAllSourcesLocal(operation) && operation.getFirstTarget().isLocal();
    }
    
    private boolean areAllSourcesLocal(SvnCopy operation) {
        for(SvnCopySource source : operation.getSources()) {
            if (source.getSource().isFile() &&
                    isLocalRevision(source.getRevision()) && isLocalRevision(source.getSource().getResolvedPegRevision())) {
                continue;
            }
            return false;
        }
        return true;
    }
    
    private boolean isLocalRevision(SVNRevision revision) {
        return revision == SVNRevision.WORKING || revision == SVNRevision.UNDEFINED;
    }

    @Override
    protected Void run(SVNWCContext context) throws SVNException {
        Collection<SvnCopySource> sources = getOperation().getSources();
        try {
            return tryRun(context, sources, getFirstTarget());
        } catch (SVNException e) {
            SVNErrorCode code = e.getErrorMessage().getErrorCode();
            if (!getOperation().isFailWhenDstExists()
                    && getOperation().getSources().size() == 1 
                    && (code == SVNErrorCode.ENTRY_EXISTS || code == SVNErrorCode.FS_ALREADY_EXISTS)) {
                SvnCopySource source = sources.iterator().next();
                return tryRun(context, sources, new File(getFirstTarget(), source.getSource().getFile().getName()));
            }
            throw e;            
        } finally {
            sleepForTimestamp();
        }        
    }
    protected Void tryRun(SVNWCContext context, Collection<SvnCopySource> sources, File target) throws SVNException {
        Collection<SvnCopyPair> copyPairs = new ArrayList<SvnNgWcToWcCopy.SvnCopyPair>();

        if (sources.size() > 1) {
            if (getOperation().isFailWhenDstExists()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            for (SvnCopySource copySource : sources) {
                SvnCopyPair copyPair = new SvnCopyPair();
                copyPair.source = copySource.getSource().getFile();
                String baseName = copyPair.source.getName();
                copyPair.dst = new File(target, baseName);
                copyPairs.add(copyPair);
            }
        } else if (sources.size() == 1) {
            SvnCopyPair copyPair = new SvnCopyPair();
            SvnCopySource source = sources.iterator().next(); 
            copyPair.source = new File(SVNPathUtil.validateFilePath(source.getSource().getFile().getAbsolutePath()));
            copyPair.dst = target;
            copyPairs.add(copyPair);
        }
        
        for (SvnCopyPair pair : copyPairs) {
            File src = pair.source;
            File dst = pair.dst;
            if (getOperation().isMove() && src.equals(dst)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Cannot move path ''{0}'' into itself", src);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (SVNWCUtils.isChild(src, dst)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy path ''{0}'' into its own child ''{1}''",
                    src, dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (getOperation().isMove()) {
            for (SvnCopyPair pair : copyPairs) {
                File src = pair.source;
                try {
                    Structure<ExternalNodeInfo> externalInfo = SvnWcDbExternals.readExternal(context, src, src, ExternalNodeInfo.kind);
                    if (externalInfo.hasValue(ExternalNodeInfo.kind) && externalInfo.get(ExternalNodeInfo.kind) != SVNNodeKind.NONE) {
                        // TODO read declaring path
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CANNOT_MOVE_FILE_EXTERNAL, 
                                "Cannot move the external at ''{0}''; please edit the svn:externals property on ''{1}''.", src);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                        throw e;
                    }
                }
            }
        } 
        verifyPaths(copyPairs, getOperation().isMakeParents(), getOperation().isMove());
        if (getOperation().isMove()) {
            move(copyPairs);
        } else {
            File ancestor = getCommonCopyAncestor(copyPairs);
            if (copyPairs.size() == 1) {
                ancestor = SVNFileUtil.getParentFile(ancestor);
            }
            ancestor = context.acquireWriteLock(ancestor, false, true);
            try {
                for (SvnCopyPair copyPair : copyPairs) {
                    checkCancelled();
                    File dstPath = SVNFileUtil.createFilePath(copyPair.dstParent, copyPair.baseName);
                    copy(context, copyPair.source, dstPath, false);
                }
            } finally {
                context.releaseWriteLock(ancestor);
            }
        }
        
        return null;
    }

    private void move(Collection<SvnCopyPair> pairs) throws SVNException {
        for (SvnCopyPair copyPair : pairs) {
            Collection<File> lockPaths = new HashSet<File>();
            Collection<File> lockedPaths = new HashSet<File>();
            
            checkCancelled();
            File sourceParent = new File(SVNPathUtil.validateFilePath(SVNFileUtil.getParentFile(copyPair.source).getAbsolutePath()));
            if (sourceParent.equals(copyPair.dstParent) ||
                    SVNWCUtils.isChild(sourceParent, copyPair.dstParent)) {
                lockPaths.add(sourceParent);
            } else if (SVNWCUtils.isChild(copyPair.dstParent, sourceParent)) {
                lockPaths.add(copyPair.dstParent);
            } else {
                lockPaths.add(sourceParent);
                lockPaths.add(copyPair.dstParent);
            }
            try {
                for (File file : lockPaths) {
                    lockedPaths.add(getWcContext().acquireWriteLock(file, false, true));
                }
                
                move(getWcContext(), copyPair.source, SVNFileUtil.createFilePath(copyPair.dstParent, copyPair.baseName), false);
            } finally {
                for (File file : lockedPaths) {
                    getWcContext().releaseWriteLock(file);
                }
            }
        }
    }
    
    private File getCommonCopyAncestor(Collection<SvnCopyPair> copyPairs) {
        File ancestor = null;
        for (SvnCopyPair svnCopyPair : copyPairs) {
            if (ancestor == null) {
                ancestor = svnCopyPair.source;
                continue;
            }
            String ancestorPath = ancestor.getAbsolutePath().replace(File.separatorChar, '/');
            String sourcePath = svnCopyPair.source.getAbsolutePath().replace(File.separatorChar, '/');
            ancestorPath = SVNPathUtil.getCommonPathAncestor(ancestorPath, sourcePath);
            ancestor = new File(ancestorPath);
        }
        return ancestor;
    }

    private void verifyPaths(Collection<SvnCopyPair> copyPairs, boolean makeParents, boolean move) throws SVNException {
        for (SvnCopyPair copyPair : copyPairs) {
            SVNFileType srcType = SVNFileType.getType(copyPair.source);
            if (srcType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", copyPair.source);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNFileType dstType = SVNFileType.getType(copyPair.dst);
            if (dstType != SVNFileType.NONE) {
                if (move && copyPairs.size() == 1) {
                    File srcDir = SVNFileUtil.getFileDir(copyPair.source);
                    File dstDir = SVNFileUtil.getFileDir(copyPair.dst);
                    if (srcDir.equals(dstDir)) {
                        // check if it is case-only rename
                        if (copyPair.source.getName().equalsIgnoreCase(copyPair.dst.getName())) {
                            copyPair.dstParent = new File(SVNPathUtil.validateFilePath(dstDir.getAbsolutePath()));
                            copyPair.baseName = SVNFileUtil.getFileName(copyPair.dst);
                            return;
                        }
                    }
                    
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", copyPair.dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            copyPair.dstParent = new File(SVNPathUtil.validateFilePath(SVNFileUtil.getParentFile(copyPair.dst).getAbsolutePath()));
            copyPair.baseName = SVNFileUtil.getFileName(copyPair.dst);
            
            if (makeParents && SVNFileType.getType(copyPair.dstParent) == SVNFileType.NONE) {
                SVNFileUtil.ensureDirectoryExists(copyPair.dstParent);
                
                SvnScheduleForAddition add = getOperation().getOperationFactory().createScheduleForAddition();
                add.setSingleTarget(SvnTarget.fromFile(copyPair.dstParent));
                add.setDepth(SVNDepth.INFINITY);
                add.setIncludeIgnored(true);
                add.setForce(false);
                add.setAddParents(true);
                add.setSleepForTimestamp(false);
                
                try {
                    add.run();
                } catch (SVNException e) {
                    SVNFileUtil.deleteAll(copyPair.dstParent, true);
                    throw e;
                }
            } else if (SVNFileType.getType(copyPair.dstParent) != SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "Path ''{0}'' is not a directory", copyPair.dstParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
    }

    protected void move(SVNWCContext context, File source, File dst, boolean metadataOnly) throws SVNException {
        copy(context, source, dst, true);
        if (!metadataOnly) {
            SVNFileUtil.rename(source, dst);
        }
        Structure<NodeInfo> nodeInfo = context.getDb().readInfo(source, NodeInfo.kind, NodeInfo.conflicted);
        if (nodeInfo.get(NodeInfo.kind) == SVNWCDbKind.Dir) {
            // TODO remove conflict markers
        }
        if (nodeInfo.is(NodeInfo.conflicted)) {
            // TODO remove conflict markers
        }
        SvnNgRemove.delete(getWcContext(), source, true, false, this);
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
            readInfo(dstDirectory, NodeInfo.status, NodeInfo.reposRootUrl, NodeInfo.reposUuid);

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
        
        if ((srcReposRootUrl != null && dstReposRootUrl != null && !srcReposRootUrl.equals(dstReposRootUrl)) || 
                (srcReposUuid != null && dstReposUuid != null && !srcReposUuid.equals(dstReposUuid))) {
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
            copyVersionedFile(context, source, dst, dst, tmpDir, srcChecksum, metadataOnly, srcConflicted, true);
        } else {
            copyVersionedDirectory(context, source, dst, dst, tmpDir, metadataOnly, true);
        }
        
    }

    private void copyVersionedDirectory(SVNWCContext wcContext, File source, File dst, File dstOpRoot, File tmpDir, boolean metadataOnly, boolean notify) throws SVNException {
        SVNSkel workItems = null;
        SVNFileType srcType = null;
        
        if (!metadataOnly) {
            srcType = SVNFileType.getType(source);
            File tmpDst = copyToTmpDir(source, tmpDir, false);
            if (tmpDst != null) {
                SVNSkel workItem = wcContext.wqBuildFileMove(tmpDst, dst);
                workItems = wcContext.wqMerge(workItems, workItem);
            }
        }
        wcContext.getDb().opCopy(source, dst, workItems);
        wcContext.wqRun(SVNFileUtil.getParentFile(dst));
        
        if (notify) {
            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.DIR, null, -1, SVNEventAction.ADD, SVNEventAction.ADD, null, null, 1, 1);
            handleEvent(event);
        }
        
        Set<String> diskChildren = null;
        if (!metadataOnly && srcType == SVNFileType.DIRECTORY) {
            File[] children = SVNFileListUtil.listFiles(source);
            diskChildren = new HashSet<String>();
            for (int i = 0; children != null && i < children.length; i++) {
                String name = SVNFileUtil.getFileName(children[i]);
                if (!name.equals(SVNFileUtil.getAdminDirectoryName())) {
                    diskChildren.add(name);
                }
            }
        }
        
        Set<String> versionedChildren = wcContext.getDb().readChildren(source);
        for (String childName : versionedChildren) {
            checkCancelled();
            File childSrcPath = SVNFileUtil.createFilePath(source, childName);
            File childDstPath = SVNFileUtil.createFilePath(dst, childName);
            
            Structure<NodeInfo> childInfo = wcContext.getDb().readInfo(childSrcPath, NodeInfo.status, NodeInfo.kind, NodeInfo.checksum, NodeInfo.conflicted,
                    NodeInfo.opRoot);
            
            if (childInfo.is(NodeInfo.opRoot)) {
                wcContext.getDb().opCopyShadowedLayer(childSrcPath, childDstPath);
            }
            SVNWCDbStatus childStatus = childInfo.get(NodeInfo.status);
            SVNWCDbKind childKind = childInfo.get(NodeInfo.kind);
            if (childStatus == SVNWCDbStatus.Normal || childStatus == SVNWCDbStatus.Added) {
                if (childKind == SVNWCDbKind.File) {
                    boolean skip = false;
                    if (childStatus == SVNWCDbStatus.Normal) {
                        Structure<NodeInfo> baseChildInfo = SvnWcDbReader.getBaseInfo((SVNWCDb) wcContext.getDb(), childSrcPath, NodeInfo.updateRoot);
                        skip = baseChildInfo.is(NodeInfo.updateRoot);
                        baseChildInfo.release();
                    }
                    if (!skip) {
                        SvnChecksum checksum = childInfo.get(NodeInfo.checksum);
                        boolean conflicted = childInfo.is(NodeInfo.conflicted);
                        copyVersionedFile(wcContext, childSrcPath, childDstPath, dstOpRoot, tmpDir, checksum, metadataOnly, conflicted, false);
                    }
                } else if (childKind == SVNWCDbKind.Dir) {
                    copyVersionedDirectory(wcContext, childSrcPath, childDstPath, dstOpRoot, tmpDir, metadataOnly, false);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "cannot handle node kind for ''{0}''", childSrcPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else if (childStatus == SVNWCDbStatus.Deleted ||
                    childStatus == SVNWCDbStatus.NotPresent ||
                    childStatus == SVNWCDbStatus.Excluded) {
                wcContext.getDb().opCopy(childSrcPath, childDstPath, null);                
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, 
                        "Cannot copy ''{0}'' excluded by server", childSrcPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            childInfo.release();
            if (diskChildren != null && 
                    (childStatus == SVNWCDbStatus.Normal  || childStatus == SVNWCDbStatus.Added)) {
                diskChildren.remove(childName);
            }
        }
        
        if (diskChildren != null && !diskChildren.isEmpty()) {
            // TODO get and skip conflict markers.
            for (String childName : diskChildren) {
                checkCancelled();
                
                File childSrcPath = SVNFileUtil.createFilePath(source, childName);
                File childDstPath = SVNFileUtil.createFilePath(dst, childName);
                File tmp = copyToTmpDir(childSrcPath, tmpDir, true);
                if (tmp != null) {
                    SVNSkel moveItem = wcContext.wqBuildFileMove(SVNFileUtil.getParentFile(dst), tmp, childDstPath);
                    getWcContext().getDb().addWorkQueue(dst, moveItem);
                }
            }
            getWcContext().wqRun(dst);
        }
    }

    private void copyVersionedFile(SVNWCContext wcContext, File source, File dst, File dstOpRoot, File tmpDir, SvnChecksum srcChecksum, boolean metadataOnly, boolean conflicted, boolean notify) throws SVNException {
        if (srcChecksum != null) {
            if (!wcContext.getDb().checkPristine(dst, srcChecksum)) {
                SvnChecksum md5 = wcContext.getDb().getPristineMD5(source, srcChecksum);
                PristineContentsInfo pristine = wcContext.getPristineContents(source, false, true);
                File tempFile = SVNFileUtil.createUniqueFile(tmpDir, dst.getName(), ".tmp", false);
                SVNFileUtil.copyFile(pristine.path, tempFile, false);
                wcContext.getDb().installPristine(tempFile, srcChecksum, md5);
            }
        }
        
        SVNSkel workItems = null;
        File toCopy = source;
        if (!metadataOnly) {
            if (conflicted) {
                File conflictWorking = null;
                List<SVNConflictDescription> conflicts = wcContext.getDb().readConflicts(source);
                
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
            File tmpDst = copyToTmpDir(toCopy, tmpDir, true);
            if (tmpDst != null) {
                boolean needsLock = wcContext.getProperty(source, SVNProperty.NEEDS_LOCK) != null;
                if (needsLock) {
                    SVNFileUtil.setReadonly(tmpDst, false);
                }
                SVNSkel workItem = wcContext.wqBuildFileMove(tmpDst, dst);
                workItems = wcContext.wqMerge(workItems, workItem);
                
                SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(tmpDst));
                if (kind == SVNNodeKind.FILE) {
                    if (!wcContext.isTextModified(source, false)) {
                        SVNSkel workItem2 = wcContext.wqBuildRecordFileinfo(dst, null);
                        workItems = wcContext.wqMerge(workItems, workItem2);
                    }
                }
            }
        }
        
        wcContext.getDb().opCopy(source, dst, workItems);
        wcContext.wqRun(SVNFileUtil.getParentFile(dst));
        if (notify) {
            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, -1, SVNEventAction.ADD, SVNEventAction.ADD, null, null, 1, 1);
            handleEvent(event, -1);
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
            SVNFileUtil.copyFile(source, dstPath, false, false);
        } else {
            SVNFileUtil.copy(source, dstPath, false, true);
        }
        return dstPath;
    }
    
    private static class SvnCopyPair {
        File source;
        File dst;
        File dstParent;
        
        String baseName;
    }
}
