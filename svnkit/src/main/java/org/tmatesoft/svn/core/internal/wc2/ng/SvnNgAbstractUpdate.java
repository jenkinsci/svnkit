package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.ISVNDirFetcher;
import org.tmatesoft.svn.core.internal.wc17.SVNExternalsStore;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNUpdateEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.AbstractSvnUpdate;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public abstract class SvnNgAbstractUpdate<V, T extends AbstractSvnUpdate<V>> extends SvnNgOperationRunner<V, T> {

    protected long update(SVNWCContext wcContext, File localAbspath, SVNRevision revision, SVNDepth depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnversionedObstructions, boolean addsAsMoodifications, boolean makeParents, boolean innerUpdate, boolean sleepForTimestamp) throws SVNException {
        
        assert ! (innerUpdate && makeParents);
        
        File lockRootPath = null;
        File anchor;
        try {
            if (makeParents) {
                File parentPath = localAbspath;
                List<File> missingParents = new ArrayList<File>();
                while(true) {
                    try {
                        lockRootPath = getWcContext().acquireWriteLock(parentPath, !innerUpdate, true);
                        break;
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY || SVNFileUtil.getParentFile(parentPath) == null) {
                            throw e;
                        }
                    }
                    missingParents.add(0, parentPath);
                    parentPath = SVNFileUtil.getParentFile(parentPath);
                }
                anchor = lockRootPath;
                for (File missingParent : missingParents) {
                    long revnum = updateInternal(
                            wcContext,
                            missingParent,
                            anchor, 
                            revision, 
                            SVNDepth.EMPTY, 
                            false, 
                            ignoreExternals, 
                            allowUnversionedObstructions,
                            addsAsMoodifications, 
                            sleepForTimestamp, 
                            false);
                    anchor = missingParent;
                    revision = SVNRevision.create(revnum);
                }
            } else {
                anchor = wcContext.acquireWriteLock(localAbspath, !innerUpdate, true);
                lockRootPath = anchor;
            }
            
            return updateInternal(wcContext, localAbspath, anchor, revision, depth, depthIsSticky, ignoreExternals, allowUnversionedObstructions, addsAsMoodifications, sleepForTimestamp, true);
            
        } finally {
            if (lockRootPath != null) {
                wcContext.releaseWriteLock(lockRootPath);
            }
        }
    }

    protected long updateInternal(SVNWCContext wcContext, File localAbspath, File anchorAbspath, SVNRevision revision, SVNDepth depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnversionedObstructions, boolean addsAsMoodifications, boolean sleepForTimestamp, boolean notifySummary) throws SVNException {
        
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        
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
        if (depthIsSticky && depth.compareTo(SVNDepth.INFINITY) < 0) {
            if (depth == SVNDepth.EXCLUDE) {
                wcContext.exclude(localAbspath);
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
        boolean cleanCheckout = isEmptyWc(localAbspath);
        
        SVNRepository repos = getRepositoryAccess().createRepository(anchorUrl, anchorAbspath);
        boolean serverSupportsDepth = repos.hasCapability(SVNCapability.DEPTH);
        final SVNReporter17 reporter = new SVNReporter17(localAbspath, wcContext, true, !serverSupportsDepth, depth, 
                getOperation().isUpdateLocksOnDemand(), false, !depthIsSticky, useCommitTimes, null);
        final long revNumber = getWcContext().getRevisionNumber(revision, null, repos, localAbspath);
        final SVNURL reposRoot = repos.getRepositoryRoot(true);
        
    
        final SVNRepository[] repos2 = new SVNRepository[1];
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
        ISVNUpdateEditor editor = SVNUpdateEditor17.createUpdateEditor(wcContext, revNumber, 
                anchorAbspath, target, useCommitTimes,
                null,
                depth, 
                depthIsSticky,
                getOperation().isAllowUnversionedObstructions(),
                true,
                serverSupportsDepth,
                cleanCheckout,
                dirFetcher,
                externalsStore,
                preservedExts);
                
        try {
            repos.update(revNumber, target, depth, false, reporter, editor);
        } catch(SVNException e) {
            sleepForTimestamp();
            throw e;
        } finally {
            if (repos2[0] != null) {
                repos2[0].closeSession();
            }
        }
        
        long targetRevision = editor.getTargetRevision();
        
        if (targetRevision >= 0) {
            if ((depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN) && !getOperation().isIgnoreExternals()) {
                handleExternals(externalsStore.getNewExternals(), externalsStore.getDepths(), anchorUrl, anchorAbspath, reposRoot, depth, false);
            }
            if (sleepForTimestamp) {
                sleepForTimestamp();
            }
            if (notifySummary) {
                handleEvent(SVNEventFactory.createSVNEvent(localAbspath, SVNNodeKind.NONE, null, targetRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null, reporter.getReportedFilesCount(),
                        reporter.getTotalFilesCount()));
            }
        }
        return targetRevision;
    }

    protected void handleExternals(Map<File, String> newExternals, Map<File, SVNDepth> ambientDepths, SVNURL anchorUrl, File targetAbspath, SVNURL reposRoot, SVNDepth requestedDepth, boolean sleepForTimestamp) throws SVNException {
        Map<File, File> oldExternals = getWcContext().getDb().getExternalsDefinedBelow(targetAbspath);
        
        for (File externalPath : newExternals.keySet()) {
            String externalDefinition = newExternals.get(externalPath);
            SVNDepth ambientDepth = SVNDepth.INFINITY;
            if (ambientDepths != null) {
                ambientDepth = ambientDepths.get(externalPath);
            }
            handleExternalsChange(reposRoot, externalPath, externalDefinition, oldExternals, ambientDepth, requestedDepth);
        }
    }


    private void handleExternalsChange(SVNURL reposRoot, File externalPath, String externalDefinition, Map<File, File> oldExternals, SVNDepth ambientDepth, SVNDepth requestedDepth) throws SVNException {
        if ((requestedDepth.compareTo(SVNDepth.INFINITY) < 0 && requestedDepth != SVNDepth.UNKNOWN) ||
                ambientDepth.compareTo(SVNDepth.INFINITY) < 0 && requestedDepth.compareTo(SVNDepth.INFINITY) < 0) {
            return;
        }
        if (externalDefinition != null) {
            SVNExternal[] externals = SVNExternal.parseExternals(externalPath, externalDefinition);
            SVNURL url = getWcContext().getNodeUrl(externalPath);
            for (int i = 0; i < externals.length; i++) {
                File targetAbsPath = SVNFileUtil.createFilePath(externalPath, externals[i].getPath());
                File oldExternalDefiningPath = oldExternals.get(targetAbsPath);
                handleExternalItemChange(reposRoot, externalPath, url, targetAbsPath, oldExternalDefiningPath, externals[i]);
                if (oldExternalDefiningPath != null) {
                    oldExternals.remove(targetAbsPath);
                }
            }
        }
    }

    private void handleExternalItemChange(SVNURL rootUrl, File parentPath, SVNURL parentUrl, File localAbsPath, File oldDefiningPath, SVNExternal newItem) throws SVNException {
        SVNURL newUrl = newItem.resolveURL(rootUrl, parentUrl);
        
        Structure<RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(SvnTarget.fromURL(newUrl), newItem.getRevision(), newItem.getPegRevision(), null);
        SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        long externalRevnum = repositoryInfo.lng(RepositoryInfo.revision);
        repositoryInfo.release();
        
        String externalUuid = repository.getRepositoryUUID(true);
        SVNURL externalRootUrl = repository.getRepositoryRoot(true);
        SVNNodeKind externalKind = repository.checkPath("", externalRevnum);
        
        if (externalKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' at revision {1} doesn''t exist",
                    repository.getLocation(), externalRevnum);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (externalKind != SVNNodeKind.DIR && externalKind != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' at revision {1} is not a file or a directory",
                    repository.getLocation(), externalRevnum);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        handleEvent(SVNEventFactory.createSVNEvent(localAbsPath, externalKind, null, -1, SVNEventAction.UPDATE_EXTERNAL, null, null, null, 0, 0));
        if (oldDefiningPath == null) {
            // checkout or export.
            if (externalKind == SVNNodeKind.DIR) {                
                SVNFileUtil.ensureDirectoryExists(localAbsPath);
                switchDirExternal(localAbsPath, newUrl, newItem.getRevision(), newItem.getPegRevision(), parentPath);
            } else if (externalKind == SVNNodeKind.FILE) {
                
            }
        } else {
            // modification or update
        }
    }

    private void switchDirExternal(File localAbsPath, SVNURL newUrl, SVNRevision revision, SVNRevision pegRevision, File parentPath) {
    }

    protected static boolean isEmptyWc(File root) {
        File[] children = SVNFileListUtil.listFiles(root);
        if (children != null) {
            return children.length == 1 && SVNFileUtil.getAdminDirectoryName().equals(children[0].getName());
        }
        return true;
    
    }
}
