package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeOriginInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.LocationsInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RevisionsPair;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgReposToWcCopy extends SvnNgOperationRunner<Long, SvnCopy> {

    @Override
    public boolean isApplicable(SvnCopy operation, SvnWcGeneration wcGeneration) throws SVNException {
        return areAllSourcesRemote(operation) && operation.getFirstTarget().isLocal();
    }
    
    private boolean areAllSourcesRemote(SvnCopy operation) {
        for(SvnCopySource source : operation.getSources()) {
            if (source.isLocal()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        if (getOperation().isMove()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Moves between the working copy and the repository are not supported");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        Collection<SvnCopySource> sources = getOperation().getSources();
        Collection<SvnCopyPair> copyPairs = new ArrayList<SvnNgReposToWcCopy.SvnCopyPair>();
        boolean srcsAreUrls = sources.iterator().next().getSource().isURL();
        if (sources.size() > 1) {
            for (SvnCopySource copySource : sources) {
                SvnCopyPair copyPair = new SvnCopyPair();
                String baseName;
                if (copySource.getSource().isFile()) {
                    copyPair.sourceFile = copySource.getSource().getFile();
                    baseName = copyPair.sourceFile.getName();
                    if (srcsAreUrls) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                                "Cannot mix repository and working copy sources");
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                } else {
                    copyPair.source = copySource.getSource().getURL();
                    baseName = SVNPathUtil.tail(copyPair.source.getPath());
                }
                copyPair.sourcePegRevision = copySource.getSource().getPegRevision();
                copyPair.sourceRevision = copySource.getRevision();
                copyPair.dst = new File(getFirstTarget(), baseName);
                copyPairs.add(copyPair);
            }
        } else if (sources.size() == 1) {
            SvnCopyPair copyPair = new SvnCopyPair();
            SvnCopySource source = sources.iterator().next(); 
            if (source.getSource().isFile()) {
                copyPair.sourceFile = source.getSource().getFile();
            } else {
                copyPair.source = source.getSource().getURL();
            }
            copyPair.sourcePegRevision = source.getSource().getPegRevision();
            copyPair.sourceRevision = source.getRevision();
            copyPair.dst = getFirstTarget();
            
            copyPairs.add(copyPair);
        }
        if (!srcsAreUrls) {
            for (SvnCopyPair pair : copyPairs) {
                File src = pair.sourceFile;
                File dst = pair.dst;
                if (SVNWCUtils.isChild(src, dst)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot copy path ''{0}'' into its own child ''{1}''",
                        src, dst);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                Structure<NodeOriginInfo> no = getWcContext().getNodeOrigin(src, true, NodeOriginInfo.reposRelpath, NodeOriginInfo.reposRootUrl, NodeOriginInfo.revision);
                if (no.get(NodeOriginInfo.reposRelpath) != null) {
                    pair.source = no.<SVNURL>get(NodeOriginInfo.reposRootUrl).appendPath(no.text(NodeOriginInfo.reposRelpath), false);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' does not have an URL associated with it", src);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (pair.sourcePegRevision == SVNRevision.BASE) {
                    pair.sourcePegRevision = SVNRevision.create(no.lng(NodeOriginInfo.revision));
                }
                if (pair.sourceRevision == SVNRevision.BASE) {
                    pair.sourceRevision = SVNRevision.create(no.lng(NodeOriginInfo.revision));
                }
            }
        }
        return copy(copyPairs, getOperation().isMakeParents(), getOperation().isIgnoreExternals());
    }
    
    private long copy(Collection<SvnCopyPair> copyPairs, boolean makeParents, boolean ignoreExternals) throws SVNException {
        for (SvnCopyPair pair : copyPairs) {
            Structure<LocationsInfo> locations = getRepositoryAccess().getLocations(null, SvnTarget.fromURL(pair.source), 
                    pair.sourcePegRevision, pair.sourceRevision, SVNRevision.UNDEFINED);
            pair.sourceOriginal = pair.source;
            pair.source = locations.get(LocationsInfo.startUrl);
            locations.release();
        }
        
        SVNURL topSrcUrl = getCommonCopyAncestor(copyPairs);
        File topDst = getCommonCopyDst(copyPairs);
        if (copyPairs.size() == 1) {
            topSrcUrl = topSrcUrl.removePathTail();
            topDst = SVNFileUtil.getParentFile(topDst);
        }
        
        SVNRepository repository = getRepositoryAccess().createRepository(topSrcUrl, null);
        Structure<RevisionsPair> revisionPair = null;
        for (SvnCopyPair pair : copyPairs) {
            revisionPair = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromURL(pair.source), pair.sourceRevision, revisionPair);
            pair.revNum = revisionPair.lng(RevisionsPair.revNumber);
        }
        
        for (SvnCopyPair pair : copyPairs) {
            String relativePath = SVNURLUtil.getRelativeURL(topSrcUrl, pair.source);
            SVNNodeKind sourceKind = repository.checkPath(relativePath, pair.revNum);
            if (sourceKind == SVNNodeKind.NONE) {
                if (pair.revNum >= 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, 
                            "Path ''{0}'' not found in revision ''{1}''", pair.source, pair.revNum);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, 
                            "Path ''{0}'' not found in HEAD revision", pair.source);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            pair.srcKind = sourceKind;
            SVNFileType dstType = SVNFileType.getType(pair.dst);
            if (dstType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "Path ''{0}'' already exists", pair.dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            File dstParent = SVNFileUtil.getParentFile(pair.dst);
            dstType = SVNFileType.getType(dstParent);
            if (getOperation().isMakeParents() && dstType == SVNFileType.NONE) {
                SVNFileUtil.ensureDirectoryExists(dstParent);
                
                SvnScheduleForAddition add = getOperation().getOperationFactory().createScheduleForAddition();
                add.setSingleTarget(SvnTarget.fromFile(dstParent));
                add.setDepth(SVNDepth.INFINITY);
                add.setIncludeIgnored(true);
                add.setForce(false);
                add.setAddParents(true);
                add.setSleepForTimestamp(false);
                
                try {
                    add.run();
                } catch (SVNException e) {
                    SVNFileUtil.deleteAll(dstParent, true);
                    throw e;
                }
            } else if (dstType != SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                        "Path ''{0}'' in not a directory", dstParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        // now do real copy
        File locked = getWcContext().acquireWriteLock(topDst, false, true);
        try {
            return copy(copyPairs, topDst, ignoreExternals, repository);
        } finally {
            getWcContext().releaseWriteLock(locked);
        }
    }
    
    private long copy(Collection<SvnCopyPair> copyPairs, File topDst, boolean ignoreExternals, SVNRepository repository) throws SVNException {
        for (SvnCopyPair pair : copyPairs) {
            SVNNodeKind dstKind  = getWcContext().readKind(pair.dst, false);
            if (dstKind == SVNNodeKind.NONE) {
                continue;
            }
            Structure<NodeInfo> nodeInfo = getWcContext().getDb().readInfo(pair.dst, NodeInfo.status);
            ISVNWCDb.SVNWCDbStatus status = nodeInfo.get(NodeInfo.status);
            nodeInfo.release();
            if (status == SVNWCDbStatus.Excluded) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control",
                        pair.dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (status == SVNWCDbStatus.ServerExcluded) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control",
                        pair.dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (dstKind != SVNNodeKind.DIR) {
                if (status == SVNWCDbStatus.Deleted || status == SVNWCDbStatus.NotPresent) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Entry for ''{0}'' exists (though the working file is missing)",
                            pair.dst);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            
        }
        // TODO test if dst and src are of the same repositories
        long rev = -1;
        for (SvnCopyPair pair : copyPairs) {
            rev = copy(pair, true, ignoreExternals, repository);
        }
        sleepForTimestamp();
        
        return rev;
    }

    private long copy(SvnCopyPair pair, boolean sameRepositories, boolean ignoreExternals, SVNRepository repository) throws SVNException {
        long rev = -1;
        if (pair.srcKind == SVNNodeKind.DIR) {
            File dstParent = SVNFileUtil.getParentFile(pair.dst);
            File dstPath = SVNFileUtil.createUniqueFile(dstParent, pair.dst.getName(), ".tmp", false);
            SVNFileUtil.deleteFile(dstPath);
            SVNFileUtil.ensureDirectoryExists(dstPath);
            try {
                
                SvnCheckout co = getOperation().getOperationFactory().createCheckout();
                co.setSingleTarget(SvnTarget.fromFile(dstPath));
                co.setSource(SvnTarget.fromURL(pair.sourceOriginal, pair.sourcePegRevision));
                co.setRevision(pair.sourceRevision);
                co.setIgnoreExternals(ignoreExternals);
                co.setDepth(SVNDepth.INFINITY);
                co.setAllowUnversionedObstructions(false);
                co.setSleepForTimestamp(false);
                rev = co.run();
    
                new SvnNgWcToWcCopy().copy(getWcContext(), dstPath, pair.dst, true);
                File dstLock = getWcContext().acquireWriteLock(dstPath, false, true);
                try {
                    getWcContext().removeFromRevisionControl(dstPath, false, false);
                } finally {
                    try {
                        getWcContext().releaseWriteLock(dstLock);
                    } catch (SVNException e) {}
                }
                SVNFileUtil.rename(dstPath, pair.dst);
            } finally {
                SVNFileUtil.deleteAll(dstPath, true);
            }
        } else {
            // TODO single file.
        }
        return rev;
    }

    private SVNURL getCommonCopyAncestor(Collection<SvnCopyPair> copyPairs) {
        SVNURL ancestor = null;
        for (SvnCopyPair svnCopyPair : copyPairs) {
            if (ancestor == null) {
                ancestor = svnCopyPair.source;
                continue;
            }
            ancestor = SVNURLUtil.getCommonURLAncestor(ancestor, svnCopyPair.source);
        }
        return ancestor;
    }

    private File getCommonCopyDst(Collection<SvnCopyPair> copyPairs) {
        String ancestor = null;
        for (SvnCopyPair svnCopyPair : copyPairs) {
            String path = svnCopyPair.dst.getAbsolutePath().replace(File.separatorChar, '/');
            if (ancestor == null) {
                ancestor = path;
                continue;
            }
            ancestor = SVNPathUtil.getCommonPathAncestor(ancestor, path);
        }
        return new File(ancestor);
    }

    private static class SvnCopyPair {
        SVNNodeKind srcKind;
        long revNum;
        SVNURL sourceOriginal;
        File sourceFile;
        SVNURL source;
        SVNRevision sourceRevision;
        SVNRevision sourcePegRevision;
        
        File dst;
        
//        File dstParent;        
//        String baseName;
    }
}
