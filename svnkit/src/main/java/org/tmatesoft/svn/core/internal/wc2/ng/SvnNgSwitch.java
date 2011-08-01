package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.HashMap;
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
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.ISVNDirFetcher;
import org.tmatesoft.svn.core.internal.wc17.SVNExternalsStore;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNUpdateEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnSwitch;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgSwitch extends SvnNgAbstractUpdate<Long, SvnSwitch> {

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        return doSwitch(getFirstTarget(), getOperation().getSwitchUrl(), getOperation().getRevision(), getOperation().getPegRevision(),
                getOperation().getDepth(), getOperation().isDepthIsSticky(), getOperation().isIgnoreExternals(),
                getOperation().isAllowUnversionedObstructions(), getOperation().isIgnoreAncestry(), getOperation().isSleepForTimestamp());
    }

    private long doSwitch(File localAbsPath, SVNURL switchUrl, SVNRevision revision, SVNRevision pegRevision, SVNDepth depth, boolean depthIsSticky, boolean ignoreExternals, 
            boolean allowUnversionedObstructions, boolean ignoreAncestry, boolean sleepForTimestamp) throws SVNException {
        File anchor = null;
        boolean releaseLock = false;
        try {
            try {
                anchor = getWcContext().obtainAnchorPath(localAbsPath, true, true);
                getWcContext().getDb().obtainWCLock(anchor, -1, false);
                releaseLock = true;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LOCKED) {
                    throw e;
                }
                releaseLock = false;
            }
            return switchInternal(localAbsPath, anchor, switchUrl, revision, pegRevision, depth, depthIsSticky, ignoreExternals, allowUnversionedObstructions, ignoreAncestry, sleepForTimestamp);
        } finally {
            if (anchor != null && releaseLock) {
                getWcContext().releaseWriteLock(anchor);
            }
        }
    }

    private long switchInternal(File localAbsPath, File anchor, SVNURL switchUrl, SVNRevision revision, SVNRevision pegRevision, SVNDepth depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnversionedObstructions,
            boolean ignoreAncestry, boolean sleepForTimestamp) throws SVNException {
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        Structure<NodeInfo> nodeInfo = getWcContext().getDb().readInfo(localAbsPath, NodeInfo.haveWork);
        try {
            if (nodeInfo.is(NodeInfo.haveWork)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Cannot switch ''{0}'' because it is not in repository yet", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {
            nodeInfo.release();
        }
        
        String[] preservedExts = getOperation().getOptions().getPreservedConflictFileExtensions();
        boolean useCommitTimes = getOperation().getOptions().isUseCommitTimes();
        String target;
        
        if (!localAbsPath.equals(anchor)) {
            target = SVNFileUtil.getFileName(localAbsPath);
        } else {
            target = "";
        }
        final SVNURL anchorUrl = getWcContext().getNodeUrl(anchor);
        if (anchorUrl == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                    "Directory ''{0}'' has no URL", anchor);
            SVNErrorManager.error(err, SVNLogType.WC);            
        }
        
        if (depthIsSticky && depth.compareTo(SVNDepth.INFINITY) < 0) {
            if (depth == SVNDepth.EXCLUDE) {
                getWcContext().exclude(localAbsPath);
                return SVNWCContext.INVALID_REVNUM;
            }
            final SVNNodeKind targetKind = getWcContext().readKind(localAbsPath, true);
            if (targetKind == SVNNodeKind.DIR) {
                getWcContext().cropTree(localAbsPath, depth);
            }
        }
        
        Structure<RepositoryInfo> repositoryInfo = getRepositoryAccess().createRepositoryFor(SvnTarget.fromURL(switchUrl), getOperation().getRevision(), getOperation().getPegRevision(), anchor);
        SVNRepository repository = repositoryInfo.<SVNRepository>get(RepositoryInfo.repository);
        final long revnum = repositoryInfo.lng(RepositoryInfo.revision);
        SVNURL switchRevUrl = repositoryInfo.<SVNURL>get(RepositoryInfo.url); 
        repositoryInfo.release();

        SVNURL switchRootUrl = repository.getRepositoryRoot(true);
        
        if (!anchorUrl.toString().startsWith(switchRootUrl.toString() + "/")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, 
                    "''{0}'' is not the same repository as ''{1}''", anchor, switchRootUrl);
            SVNErrorManager.error(err, SVNLogType.WC);            
        }
        if (!getOperation().isIgnoreAncestry()) {
            SVNURL targetUrl = getWcContext().getNodeUrl(localAbsPath);
            long targetRev = getWcContext().getNodeBaseRev(localAbsPath);
            
            // TODO find common ancestor of switchRevUrl@revnum and targetUrl@targetRev
        }
        
        repository.setLocation(anchorUrl, false);
        boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
        SVNExternalsStore externalsStore = new SVNExternalsStore();

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
                repos2[0].getDir("", revnum, null, new ISVNDirEntryHandler() {
                    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                        if (dirEntry.getName() != null && !"".equals(dirEntry.getName())) {
                            entries.put(dirEntry.getName(), dirEntry);
                        }
                    }
                });
                return entries;
            }
        };
        final SVNReporter17 reporter = new SVNReporter17(localAbsPath, getWcContext(), true, !serverSupportsDepth, depth, 
                getOperation().isUpdateLocksOnDemand(), false, !depthIsSticky, useCommitTimes, null);
        ISVNUpdateEditor editor = SVNUpdateEditor17.createUpdateEditor(getWcContext(), 
                revnum, anchor, target, useCommitTimes, switchRevUrl, depth, depthIsSticky, allowUnversionedObstructions, 
                false, serverSupportsDepth, false, dirFetcher, externalsStore, preservedExts);
        
        try {
            repository.update(switchRevUrl, revnum, target, depth, reporter, editor);
        } catch (SVNException e) {
            sleepForTimestamp();
            throw e;
        }
        if (depth.isRecursive() && !getOperation().isIgnoreExternals()) {
            handleExternals(externalsStore.getNewExternals(), externalsStore.getDepths(), anchorUrl, localAbsPath, switchRootUrl, depth, true);
        }
        if (sleepForTimestamp) {
            sleepForTimestamp();
        }
        handleEvent(SVNEventFactory.createSVNEvent(localAbsPath, SVNNodeKind.NONE, null, revnum, SVNEventAction.UPDATE_COMPLETED, null, null, null, reporter.getReportedFilesCount(),
                reporter.getTotalFilesCount()));
        
        return editor.getTargetRevision();
    }


}
