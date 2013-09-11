package org.tmatesoft.svn.core.internal.wc2.remote;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.internal.wc2.ng.*;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnDiffStatus;
import org.tmatesoft.svn.core.wc2.SvnDiffSummarize;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnRemoteDiffSummarize extends SvnRemoteOperationRunner<SvnDiffStatus, SvnDiffSummarize> {

    @Override
    protected SvnDiffStatus run() throws SVNException {
        final SvnTarget source = getOperation().getSource();
        final SvnTarget firstSource = getOperation().getFirstSource();
        final SvnTarget secondSource = getOperation().getSecondSource();

        final ISVNDiffStatusHandler handler = createHandlerForReceiver(getOperation());
        final SVNDepth depth = getOperation().getDepth();
        final boolean useAncestry = !getOperation().isIgnoreAncestry();

        if (source != null) {
            doDiffURL(source.getURL(), source.getFile(), getOperation().getStartRevision(), getOperation().getEndRevision(),
                    source.getPegRevision(), depth, useAncestry, handler);
        } else {
            doDiffURLURL(firstSource.getURL(), firstSource.getFile(), firstSource.getResolvedPegRevision(),
                    secondSource.getURL(),  secondSource.getFile(), secondSource.getResolvedPegRevision(),
                    SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }

        return null;
    }

    private void doDiffURL(SVNURL url, File path, SVNRevision startRevision, SVNRevision endRevision, SVNRevision pegRevision,
                          SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        doDiffURLURL(url, path, startRevision,
                     url, path, endRevision,
                     pegRevision, depth, useAncestry, handler);
    }

    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1,
                              SVNURL url2, File path2, SVNRevision revision2,
                              SVNRevision pegRevision, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {

        if (revision1 == SVNRevision.UNDEFINED || revision2 == SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all revisions are specified");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        boolean isLocalRev1 = revision1 == SVNRevision.BASE || revision1 == SVNRevision.WORKING;
        boolean isLocalRev2 = revision2 == SVNRevision.BASE || revision2 == SVNRevision.WORKING;
        boolean isRepos1;
        boolean isRepos2;

        if (pegRevision != SVNRevision.UNDEFINED) {
            if (isLocalRev1 && isLocalRev2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for a pegged diff");
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            isRepos1 = !isLocalRev1;
            isRepos2 = !isLocalRev2;
        } else {
            isRepos1 = !isLocalRev1 || url1 != null;
            isRepos2 = !isLocalRev2 || url2 != null;
        }

        if (!isRepos1 || !isRepos2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            url2 = resolvePeggedDiffTargetUrl(url2, path2, pegRevision, revision2);
            url1 = resolvePeggedDiffTargetUrl(url1, path1, pegRevision, revision1);

            if (url2 != null && url1 == null) {
                url1 = url2;
            }
            if (url1 != null && url2 == null) {
                url2 = url1;
            }

        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, null, true);
        SVNRepository repository2 = createRepository(url2, null, false);
        long rev1 = getRevisionNumber(revision1, repository1, url1);
        long rev2 = -1;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;

        SVNURL anchor1 = url1;
        SVNURL anchor2 = url2;
        String target1 = "";
        String target2 = "";

        try {
            rev2 = getRevisionNumber(revision2, repository2, url2);
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);

            if (kind1 == SVNNodeKind.NONE && kind2 == SVNNodeKind.NONE) {
                if (url1.equals(url2)) {
                    SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revisions ''{1}'' and ''{2}''", url1, rev1, rev2);
                    SVNErrorManager.error(errorMessage, SVNLogType.WC);
                } else {
                    SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff targets ''{0}'' and ''{1}'' were not found in the repository at revisions ''{2}'' and ''{3}''");
                    SVNErrorManager.error(errorMessage, SVNLogType.WC);
                }
            } else if (kind1 == SVNNodeKind.NONE) {
                checkDiffTargetExists(url1, rev2, rev1, repository1);
            } else if (kind2 == SVNNodeKind.NONE) {
                checkDiffTargetExists(url2, rev1, rev2, repository2);
            }

            SVNURL repositoryRoot = repository1.getRepositoryRoot(true);
            if (!url1.equals(repositoryRoot) && !url2.equals(repositoryRoot)) {
                anchor1 = url1.removePathTail();
                anchor2 = url2.removePathTail();

                target1 = SVNPathUtil.tail(url1.toDecodedString());
                target2 = SVNPathUtil.tail(url2.toDecodedString());
            }
        } finally {
            repository2.closeSession();
        }

        boolean nonDir = kind1 != SVNNodeKind.DIR || kind2 != SVNNodeKind.DIR;

        ISvnDiffCallback oldCallback = new SvnDiffSummarizeCallback(SVNFileUtil.createFilePath(basePath.getParentFile(), target1), false, handler);
        ISvnDiffCallback2 callback = new SvnDiffCallbackWrapper(oldCallback, true, nonDir ? basePath.getParentFile() : basePath);

        if (kind2 == SVNNodeKind.NONE) {
            SVNURL tmpUrl;

            tmpUrl = url1;
            url1 = url2;
            url2 = tmpUrl;

            long tmpRev;
            tmpRev = rev1;
            rev1 = rev2;
            rev2 = tmpRev;

            tmpUrl = anchor1;
            anchor1 = anchor2;
            anchor2 = tmpUrl;

            String tmpTarget;
            tmpTarget = target1;
            target1 = target2;
            target2 = tmpTarget;

            callback = new SvnReverseOrderDiffCallback(callback, null);
        }

        repository1.setLocation(anchor1, true);
        repository2.setLocation(anchor2, true);
        SvnNgRemoteDiffEditor2 editor = null;
        try {
            editor = new SvnNgRemoteDiffEditor2(rev1, false, repository2, callback);
            final long finalRev1 = rev1;
            ISVNReporterBaton reporter = new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, finalRev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, false, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            repository2.closeSession();
            if (editor != null) {
                editor.cleanup();
            }
        }
    }

    private SVNURL resolvePeggedDiffTargetUrl(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        try {
            final Structure<SvnRepositoryAccess.LocationsInfo> locationsInfo = getRepositoryAccess().getLocations(null,
                    url == null ? SvnTarget.fromFile(path) : SvnTarget.fromURL(url),
                    pegRevision, revision, SVNRevision.UNDEFINED);
            return locationsInfo.get(SvnRepositoryAccess.LocationsInfo.startUrl);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_UNRELATED_RESOURCES ||
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private void checkDiffTargetExists(SVNURL url, long revision, long otherRevision, SVNRepository repository) throws SVNException {
        SVNURL sessionUrl = repository.getLocation();
        boolean equal = sessionUrl.equals(url);
        if (!equal) {
            repository.setLocation(url, true);
        }
        SVNNodeKind kind = repository.checkPath("", revision);
        if (kind == SVNNodeKind.NONE) {
            if (revision == otherRevision) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revision ''{1}''", url, revision);
                SVNErrorManager.error(errorMessage, SVNLogType.WC);
            } else {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Diff target ''{0}'' was not found in the repository at revision ''{1}'' or ''{2}''", url, revision, otherRevision);
                SVNErrorManager.error(errorMessage, SVNLogType.WC);
            }
        }
        if (!equal) {
            repository.setLocation(url, true);
        }
    }

    private ISVNDebugLog getDebugLog() {
        return SVNDebugLog.getDefaultLog();
    }

    private long getRevisionNumber(SVNRevision revision1, SVNRepository repository1, SVNURL url1) throws SVNException {
            final Structure<SvnRepositoryAccess.RevisionsPair> revisionNumber = getRepositoryAccess().getRevisionNumber(repository1, SvnTarget.fromURL(url1, revision1), revision1, null);
        return revisionNumber.lng(SvnRepositoryAccess.RevisionsPair.revNumber);
    }

    private SVNURL getURL(File path1) throws SVNException {
        return getRepositoryAccess().getURLFromPath(SvnTarget.fromFile(path1), SVNRevision.UNDEFINED, null).<SVNURL>get(SvnRepositoryAccess.UrlInfo.url);
    }

    protected SVNRepository createRepository(SVNURL url, File path, boolean mayReuse) throws SVNException {
        return getRepositoryAccess().createRepository(url, null, mayReuse);
    }

    private static ISVNDiffStatusHandler createHandlerForReceiver(final ISvnObjectReceiver<SvnDiffStatus> receiver) {
        return new ISVNDiffStatusHandler() {
            public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
                if (receiver != null) {
                    receiver.receive(null, SvnCodec.diffStatus(diffStatus));
                }
            }
        };
    }
}
