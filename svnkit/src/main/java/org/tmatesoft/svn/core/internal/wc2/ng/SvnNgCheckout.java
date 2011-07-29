package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.ISVNDirFetcher;
import org.tmatesoft.svn.core.internal.wc17.SVNAmbientDepthFilterEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNExternalsStore;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNUpdateEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgCheckout extends SvnNgOperationRunner<Long, SvnCheckout>{

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        File dstPath = getFirstTarget();
        
        SVNURL url = getOperation().getUrl();
        Structure<RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(
                SvnTarget.fromURL(url), 
                getOperation().getRevision(), 
                getOperation().getPegRevision(), 
                null);
        
        url = repositoryInfo.<SVNURL>get(RepositoryInfo.url);
        SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        long revnum = repositoryInfo.lng(RepositoryInfo.revision);
        
        repositoryInfo.release();
        
        SVNURL rootUrl = repository.getRepositoryRoot(true);
        String uuid = repository.getRepositoryUUID(true);
        SVNNodeKind kind = repository.checkPath("", revnum);
        SVNDepth depth = getOperation().getDepth();
        
        if (kind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "URL ''{0}'' refers to a file, not a directory", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn''t exist", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        long result = SVNWCContext.INVALID_REVNUM;
        SVNFileType fileKind = SVNFileType.getType(dstPath);
        
        if (fileKind == SVNFileType.NONE) {
            SVNFileUtil.ensureDirectoryExists(dstPath);
            context.initializeWC(dstPath, url, rootUrl, uuid, revnum, depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth);
            result = update(context, dstPath, getOperation().getRevision(), depth, getOperation().isAllowUnversionedObstructions(), true, false, false);
        } else if (fileKind == SVNFileType.DIRECTORY) {
            int formatVersion = context.checkWC(dstPath);
            if (formatVersion != 0) {
                SVNURL entryUrl = context.getNodeUrl(dstPath);
                if (entryUrl != null && url.equals(entryUrl)) {
                    result = update(context, dstPath, getOperation().getRevision(), depth, getOperation().isAllowUnversionedObstructions(), true, false, false);
                } else {
                    String message = "''{0}'' is already a working copy for a different URL";
                    message += "; perform update to complete it";
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, message, dstPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
                context.initializeWC(dstPath, url, rootUrl, uuid, revnum, depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth);
                result = update(context, dstPath, getOperation().getRevision(), depth, getOperation().isAllowUnversionedObstructions(), true, false, false);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, "''{0}'' already exists and is not a directory", dstPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return result;
    }
    

    private long update(SVNWCContext wcContext, File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky, boolean sendCopyFrom, boolean innerUpdate)
            throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        path = path.getAbsoluteFile();
        final File anchor = wcContext.acquireWriteLock(path, !innerUpdate, true);
        try {
            return updateInternal(wcContext, path, anchor, revision, depth, depthIsSticky, allowUnversionedObstructions, sendCopyFrom, innerUpdate, true);
        } finally {
            wcContext.releaseWriteLock(anchor);
        }
    }
    
    private long updateInternal(SVNWCContext wcContext, File localAbspath, File anchorAbspath, SVNRevision revision, SVNDepth depth, boolean depthIsSticky, boolean allowUnversionedObstructions,
            boolean sendCopyFrom, boolean innerUpdate, boolean notifySummary) throws SVNException {

        String target;
        if (!localAbspath.equals(anchorAbspath)) {
            target = SVNFileUtil.getFileName(localAbspath);
        } else {
            target = "";
        }
        final SVNURL anchorUrl = wcContext.getNodeUrl(anchorAbspath);

        if (anchorUrl == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "'{0}' has no URL", anchorAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return SVNWCContext.INVALID_REVNUM;
        }
        
        long baseRevision = wcContext.getNodeBaseRev(anchorAbspath);
        SVNWCContext.ConflictInfo conflictInfo;
        boolean treeConflict = false;
        try {
            conflictInfo = wcContext.getConflicted(localAbspath, false, false, true);
            treeConflict = conflictInfo != null && conflictInfo.treeConflicted;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            treeConflict = false;
        }
        if (baseRevision == SVNWCContext.INVALID_REVNUM || treeConflict) {
            if (wcContext.getEventHandler() != null) {
                handleEvent(SVNEventFactory.createSVNEvent(localAbspath, SVNNodeKind.NONE, null, -1, 
                        treeConflict ? SVNEventAction.SKIP_CONFLICTED : SVNEventAction.UPDATE_SKIP_WORKING_ONLY, null, null, null, 0, 0));
                
            }
            return SVNWCContext.INVALID_REVNUM;
        }
        try {
            /* We may need to crop the tree if the depth is sticky */
            if (depthIsSticky && depth.compareTo(SVNDepth.INFINITY) < 0) {
                if (depth == SVNDepth.EXCLUDE) {
                    wcContext.exclude(localAbspath);
                    /* Target excluded, we are done now */
                    return SVNWCContext.INVALID_REVNUM;
                }
                final SVNNodeKind targetKind = wcContext.readKind(localAbspath, true);
                if (targetKind == SVNNodeKind.DIR) {
                    wcContext.cropTree(localAbspath, depth);
                }
            }

            String[] preservedExts = getOperation().getOptions().getPreservedConflictFileExtensions();
            boolean useCommitTimes = getOperation().getOptions().isUseCommitTimes();

            if (notifySummary) {
                handleEvent(SVNEventFactory.createSVNEvent(localAbspath, SVNNodeKind.NONE, null, -1, SVNEventAction.UPDATE_STARTED, null, null, null, 0, 0));
            }

            SVNRepository repos = getRepositoryAccess().createRepository(anchorUrl, anchorAbspath);
            boolean serverSupportsDepth = repos.hasCapability(SVNCapability.DEPTH);
            final SVNReporter17 reporter = new SVNReporter17(localAbspath, wcContext, true, !serverSupportsDepth, depth, 
                    getOperation().isUpdateLocksOnDemand(), false, !depthIsSticky, useCommitTimes, null);
            final long revNumber = getWcContext().getRevisionNumber(revision, null, repos, localAbspath);
            final SVNURL reposRoot = repos.getRepositoryRoot(true);

            final SVNRepository[] repos2 = new SVNRepository[1];
            ISVNFileFetcher fileFetcher = new ISVNFileFetcher() {

                public long fetchFile(String path, long revision, OutputStream os, SVNProperties properties) throws SVNException {
                    SVNURL url = reposRoot.appendPath(SVNPathUtil.removeTail(path), false);
                    if (repos2[0] == null) {
                        repos2[0] = getRepositoryAccess().createRepository(url, null, false);
                    } else {
                        repos2[0].setLocation(url, false);
                    }
                    return repos2[0].getFile(SVNPathUtil.tail(path), revision, properties, os);
                }
            };
            ISVNDirFetcher dirFetcher = new ISVNDirFetcher() {
                public Map<String, SVNDirEntry> fetchEntries(SVNURL reposRoot, File path) throws SVNException {
                    SVNURL url = SVNWCUtils.join(reposRoot, path);
                    if (repos2[0] == null) {
                        repos2[0] = getRepositoryAccess().createRepository(url, null, false);
                    } else {
                        repos2[0].setLocation(url, false);
                    }
                    
                    final Map<String, SVNDirEntry> entries = new HashMap<String, SVNDirEntry>();
                    repos2[0].getDir("", revNumber, null, new ISVNDirEntryHandler() {
                        public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                            if (dirEntry.getName() != null && !"".equals(dirEntry.getName())) {
                                entries.put(dirEntry.getName(), dirEntry);
                            }
                        }
                    });
                    return entries;
                }
            };

            SVNExternalsStore externalsStore = new SVNExternalsStore();
            ISVNUpdateEditor editor = createUpdateEditor(wcContext, anchorAbspath, target, reposRoot, externalsStore, allowUnversionedObstructions, depthIsSticky, depth, preservedExts, fileFetcher,
                    dirFetcher, getOperation().isUpdateLocksOnDemand());
            ISVNEditor filterEditor = SVNAmbientDepthFilterEditor17.wrap(wcContext, anchorAbspath, target, editor, depthIsSticky);
            try {
                repos.update(revNumber, target, depth, sendCopyFrom, reporter, SVNCancellableEditor.newInstance(filterEditor, this, null));
            } finally {
                if (repos2[0] != null) {
                    repos2[0].closeSession();
                }
            }

            long targetRevision = editor.getTargetRevision();
            if (targetRevision >= 0) {
                if ((depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN) && !getOperation().isIgnoreExternals()) {
                    handleExternals(externalsStore.getOldExternals(), externalsStore.getNewExternals(), externalsStore.getDepths(), anchorUrl, anchorAbspath, reposRoot, depth);
                }
                handleEvent(SVNEventFactory.createSVNEvent(localAbspath, SVNNodeKind.NONE, null, targetRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null, reporter.getReportedFilesCount(),
                        reporter.getTotalFilesCount()));
            }
            wcContext.cleanup();
            return targetRevision;
        } finally {
            sleepForTimestamp();
        }
    }
    
    private ISVNUpdateEditor createUpdateEditor(SVNWCContext wcContext, File anchorAbspath, String target, SVNURL reposRoot, SVNExternalsStore externalsStore, boolean allowUnversionedObstructions,
            boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, ISVNDirFetcher dirFetcher, boolean updateLocksOnDemand) throws SVNException {
        return SVNUpdateEditor17.createUpdateEditor(wcContext, anchorAbspath, target, reposRoot, null, externalsStore, allowUnversionedObstructions, depthIsSticky, depth, preservedExts, 
                fileFetcher, dirFetcher, 
                updateLocksOnDemand);
    }


    private void handleExternals(Map oldExternals, Map newExternals, Map depths, SVNURL anchorUrl, File anchorAbspath, SVNURL reposRoot, SVNDepth depth) {
        // TODO
    }

}
