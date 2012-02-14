package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc17.SVNAmbientDepthFilterEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNDiffEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgDiff extends SvnNgOperationRunner<Void, SvnDiff> {

    @Override
    public boolean isApplicable(SvnDiff operation, SvnWcGeneration wcGeneration) throws SVNException {
        if (wcGeneration != SvnWcGeneration.V17) {
            return false;
        }
        Collection<SvnTarget> targets = operation.getTargets();
        for (SvnTarget target : targets) {
            if (target.isFile()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Void run(SVNWCContext context) throws SVNException {
        if (isPeggedDiff()) {
            doPeggedDiff();
        } else {
            doTwoSourcesDiff();
        }

        return null;
    }

    private void doPeggedDiff() throws SVNException {
        final SvnTarget target = getOperation().getFirstTarget();
        final SVNRevision pegRevision = target.getPegRevision();
        if (!pegRevision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Peg revision is not specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        final SVNRevision startRevision = getOperation().getStartRevision();
        final SVNRevision endRevision = getOperation().getEndRevision();

        if (startRevision == null || endRevision == null || !startRevision.isValid() || !endRevision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        final boolean startRevisionIsLocal = startRevision == SVNRevision.BASE || endRevision == SVNRevision.WORKING;
        final boolean endRevisionIsLocal = startRevision == SVNRevision.BASE || endRevision == SVNRevision.WORKING;

        if (startRevisionIsLocal && endRevisionIsLocal) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for a pegged diff");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        final File targetFile = target.getFile().getAbsoluteFile();
        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();
        generator.init(targetFile.getAbsolutePath(), targetFile.getAbsolutePath());

        if (!(endRevision == SVNRevision.BASE || endRevision == SVNRevision.WORKING || endRevision == SVNRevision.COMMITTED)) {
            if ((startRevision == SVNRevision.BASE || startRevision == SVNRevision.WORKING || startRevision == SVNRevision.COMMITTED)) {
                doDiffURLWC(targetFile, endRevision, pegRevision, targetFile, startRevision, true, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else {
                doDiffURLURL(null, targetFile, startRevision, null, targetFile, endRevision, pegRevision);
            }
        } else {
            doDiffURLWC(targetFile, startRevision, pegRevision, targetFile, endRevision, false, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
        }
    }

    private void doTwoSourcesDiff() throws SVNException {
        final SvnTarget firstTarget = getOperation().getFirstTarget();
        final SvnTarget secondTarget = getOperation().getSecondTarget();

        if (firstTarget.isURL() && secondTarget.isFile()) {
            doDiffURLWC(firstTarget, secondTarget);
        } else if (firstTarget.isFile() && secondTarget.isURL()) {
            doDiffWCURL(firstTarget, secondTarget);
        } else if (firstTarget.isFile() && secondTarget.isFile()) {
            doDiffWCWC(firstTarget, secondTarget);
        } else {
            throw new UnsupportedOperationException("URL-URL diff is not supported");
        }
    }

    private void doDiffWCURL(SvnTarget firstTarget, SvnTarget secondTarget) throws SVNException {
        final SVNRevision startRevision = getOperation().getStartRevision();
        final SVNRevision endRevision = getOperation().getEndRevision();

        if (startRevision == null || endRevision == null || !startRevision.isValid() || !endRevision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();
        generator.init(firstTarget.getFile().getAbsolutePath(), secondTarget.getURL().toString());
        if (startRevision == SVNRevision.BASE || endRevision == SVNRevision.WORKING) {
            doDiffURLWC(secondTarget.getURL(), endRevision, SVNRevision.UNDEFINED, firstTarget.getFile(), startRevision, true);
        } else {
            doDiffURLURL(null, firstTarget.getFile(), startRevision, secondTarget.getURL(), null, endRevision, SVNRevision.UNDEFINED);
        }

    }

    private void doDiffURLWC(SvnTarget firstTarget, SvnTarget secondTarget) throws SVNException {
        final SVNRevision startRevision = getOperation().getStartRevision();
        final SVNRevision endRevision = getOperation().getEndRevision();

        if (startRevision == null || endRevision == null || !startRevision.isValid() || !endRevision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();
        generator.init(firstTarget.getURL().toString(), secondTarget.getFile().getAbsolutePath());
        if (startRevision == SVNRevision.BASE || endRevision == SVNRevision.WORKING) {
            doDiffURLWC(firstTarget.getURL(), startRevision, SVNRevision.UNDEFINED, secondTarget.getFile(), endRevision, false);
        } else {
            doDiffURLURL(firstTarget.getURL(), null, startRevision, null, secondTarget.getFile(), endRevision, SVNRevision.UNDEFINED);
        }
    }

    private void doDiffWCWC(SvnTarget firstTarget, SvnTarget secondTarget) throws SVNException {
        final SVNRevision startRevision = getOperation().getStartRevision();
        final SVNRevision endRevision = getOperation().getEndRevision();

        if (startRevision == null || endRevision == null || !startRevision.isValid() || !endRevision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        boolean startRevisionIsLocal = startRevision == SVNRevision.WORKING || startRevision == SVNRevision.BASE;
        boolean endRevisionIsLocal = endRevision == SVNRevision.WORKING || endRevision == SVNRevision.BASE;
        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();
        generator.init(firstTarget.getFile().getAbsolutePath(), secondTarget.getURL().toString());
        if (startRevisionIsLocal && endRevisionIsLocal) {
            doDiffWCWC(firstTarget.getFile(), startRevision, secondTarget.getFile(), endRevision);
        } else if (startRevisionIsLocal) {
            doDiffURLWC(secondTarget.getFile(), endRevision, SVNRevision.UNDEFINED, firstTarget.getFile(), startRevision, true);
        } else if (endRevisionIsLocal) {
            doDiffURLWC(firstTarget.getFile(), startRevision, SVNRevision.UNDEFINED, secondTarget.getFile(), endRevision, false);
        } else {
            doDiffURLURL(null, firstTarget.getFile(), startRevision, null, secondTarget.getFile(), endRevision, SVNRevision.UNDEFINED);
        }
    }

    private boolean isPeggedDiff() {
        return getOperation().getTargets().size() == 1;
    }

    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, boolean reverse) throws SVNException {

        final ISVNDiffGenerator diffGenerator = new DefaultSVNDiffGenerator();

        boolean isRoot = getWcContext().getDb().isWCRoot(path2);
        final String target = isRoot ? null : SVNFileUtil.getFileName(path2);
        final File pathForUrl = isRoot ? path2 : SVNFileUtil.getParentFile(path2);

        final SVNURL anchorUrl = getRepositoryAccess().getTargetURL(SvnTarget.fromFile(pathForUrl));

        if (anchorUrl == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", pathForUrl);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (pegRevision.isValid()) {
            final Structure<SvnRepositoryAccess.LocationsInfo> locations = getRepositoryAccess().getLocations(null, SvnTarget.fromURL(url1), pegRevision, revision1, revision2);
            final SVNURL startUrl = locations.get(SvnRepositoryAccess.LocationsInfo.startUrl);
            final SVNURL endUrl = locations.get(SvnRepositoryAccess.LocationsInfo.endUrl);
            final long startRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.startRevision);
            final long endRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.endRevision);

            url1 = startUrl;
            String anchorPath2 = SVNPathUtil.append(anchorUrl.toString(), target == null ? "" : target);
            if (!reverse) {
                diffGenerator.init(url1.toString(), anchorPath2);
            } else {
                diffGenerator.init(anchorPath2, url1.toString());
            }
        }
        SVNRepository repository = getRepositoryAccess().createRepository(anchorUrl, null, true);
        long revNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromURL(url1), revision1, null).lng(SvnRepositoryAccess.RevisionsPair.youngestRevision);
        ISvnDiffCallback callback = new SvnDiffCallback(diffGenerator, reverse ? -1 : revNumber, reverse ? revNumber : -1, getOperation().getOutput());
        SVNDiffEditor17 editor = new SVNDiffEditor17(getWcContext(), pathForUrl, getOperation().getDepth(), revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED,
                reverse, callback, !getOperation().isIgnoreAncestry(), getOperation().getApplicableChangelists(), false, getOperation().isShowCopiesAsAdds());
        boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
        final SVNReporter17 reporter17 = new SVNReporter17(path2, getWcContext(), false, !serverSupportsDepth, getOperation().getDepth(), false, false, true, false, SVNDebugLog.getDefaultLog());
        long pegRevisionNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromFile(path2), revision2, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        try {
            repository.diff(url1, revNumber, pegRevisionNumber, target, getOperation().isIgnoreAncestry(), getOperation().getDepth(), true, reporter17, SVNCancellableEditor.newInstance(editor, this, SVNDebugLog.getDefaultLog()));
        } finally {
            editor.cleanup();
        }
    }


    private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, boolean reverse, SVNDepth depth, boolean useAncestry, OutputStream result,
                             Collection changeLists) throws SVNException {

        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();

        boolean isRoot = getWcContext().getDb().isWCRoot(path2);
        final String target = isRoot ? null : SVNFileUtil.getFileName(path2);
        final File pathForUrl = isRoot ? path2 : SVNFileUtil.getParentFile(path2);

        final SVNURL anchorUrl = getRepositoryAccess().getTargetURL(SvnTarget.fromFile(pathForUrl));

        if (anchorUrl == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", pathForUrl);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        final SVNURL url1;
        if (pegRevision.isValid()) {
            final Structure<SvnRepositoryAccess.LocationsInfo> locations = getRepositoryAccess().getLocations(null, SvnTarget.fromFile(path1), pegRevision, revision1, revision2);
            final SVNURL startUrl = locations.get(SvnRepositoryAccess.LocationsInfo.startUrl);
            final SVNURL endUrl = locations.get(SvnRepositoryAccess.LocationsInfo.endUrl);
            final long startRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.startRevision);
            final long endRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.endRevision);

            url1 = startUrl;
            String anchorPath2 = SVNPathUtil.append(anchorUrl.toString(), target == null ? "" : target);
            generator.init(url1.toString(), anchorPath2);
        } else {
            url1 = getRepositoryAccess().getTargetURL(SvnTarget.fromFile(path1));
        }

        SVNRepository repository = getRepositoryAccess().createRepository(anchorUrl, null, true);
        long revNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromFile(path1), revision1, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);

        ISvnDiffCallback callback = new SvnDiffCallback(generator, reverse ? -1 : revNumber, reverse ? revNumber : -1, getOperation().getOutput());
        SVNDiffEditor17 editor = new SVNDiffEditor17(getWcContext(), pathForUrl, getOperation().getDepth(), revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED,
                reverse, callback, !getOperation().isIgnoreAncestry(), getOperation().getApplicableChangelists(), false, getOperation().isShowCopiesAsAdds());
        boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
        final SVNReporter17 reporter17 = new SVNReporter17(path2, getWcContext(), false, !serverSupportsDepth, getOperation().getDepth(), false, false, true, false, SVNDebugLog.getDefaultLog());
        long pegRevisionNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromFile(path2), revision2, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);

        SVNAmbientDepthFilterEditor17.wrap(getWcContext(), pathForUrl, target, editor, false);

        try {
            repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, depth, true, reporter17, SVNCancellableEditor.newInstance(editor, this, SVNDebugLog.getDefaultLog()));
        } finally {
            editor.cleanup();
        }
    }


    private void doDiffWCWC(File path1, SVNRevision revision1, File path2, SVNRevision revision2) throws SVNException {
        if (!path1.equals(path2) || !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only diffs between a path's text-base and its working files are supported at this time (-rBASE:WORKING)");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        final ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();

        boolean isRoot = getWcContext().getDb().isWCRoot(path2);
        final String target = isRoot ? null : SVNFileUtil.getFileName(path2);
        final File pathForUrl = isRoot ? path2 : SVNFileUtil.getParentFile(path2);

        long revNumber = getRepositoryAccess().getRevisionNumber(null, SvnTarget.fromFile(path1), revision1, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        ISvnDiffCallback callback = new SvnDiffCallback(generator, revNumber, -1, getOperation().getOutput());
        SVNDiffEditor17 editor = new SVNDiffEditor17(getWcContext(), pathForUrl, getOperation().getDepth(), revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED,
                false, callback, !getOperation().isIgnoreAncestry(), getOperation().getApplicableChangelists(), false, getOperation().isShowCopiesAsAdds());

        try {
            editor.closeEdit();
        } finally {
            editor.cleanup();
        }
    }


    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision) throws SVNException {
        ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();

        final SvnTarget target1 = url1 != null ? SvnTarget.fromURL(url1) : SvnTarget.fromFile(path1);
        final SvnTarget target2 = url2 != null ? SvnTarget.fromURL(url2) : SvnTarget.fromFile(path2);

        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            Structure<SvnRepositoryAccess.LocationsInfo> locations = getRepositoryAccess().getLocations(null, target2, pegRevision, revision1, revision2);
            url1 = locations.get(SvnRepositoryAccess.LocationsInfo.startUrl);
            url2 = locations.get(SvnRepositoryAccess.LocationsInfo.endUrl);
            generator.init(url1.toString(), url2.toString());
        } else {
            url1 = getRepositoryAccess().getTargetURL(target1);
            url2 = getRepositoryAccess().getTargetURL(target2);
        }
        SVNRepository repository1 = getRepositoryAccess().createRepository(url1, null, true);
        SVNRepository repository2 = getRepositoryAccess().createRepository(url2, null, false);
        final long rev1 = getRepositoryAccess().getRevisionNumber(repository1, target1, revision1, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        long rev2 = -1;
        String target1String = null;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;
        try {
            rev2 = getRepositoryAccess().getRevisionNumber(repository2, target2, revision2, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);
            if (kind1 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}", new Object[]{
                        url1, new Long(rev1)
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (kind2 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}", new Object[]{
                        url2, new Long(rev2)
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {
            repository2.closeSession();
        }
        if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
            target1String = SVNPathUtil.tail(url1.getPath());
            if (basePath != null) {
                basePath = basePath.getParentFile();
            }
            url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
            repository1 = getRepositoryAccess().createRepository(url1, null, true);
        }
        repository2 = getRepositoryAccess().createRepository(url1, null, false);
        SVNRemoteDiffEditor editor = null;
        try {
            SVNDiffCallback callback = new SVNDiffCallback(null, generator, rev1, rev2, getOperation().getOutput());
            callback.setBasePath(basePath);
            editor = new SVNRemoteDiffEditor(null, null, callback, repository2, rev1, rev2, false, null, this);
            editor.setUseGlobalTmp(true);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {

                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, rev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1String, getOperation().isIgnoreAncestry(), getOperation().getDepth(), true, reporter, SVNCancellableEditor.newInstance(editor, this, SVNDebugLog.getDefaultLog()));
        } finally {
            if (editor != null) {
                editor.cleanup();
            }
            repository2.closeSession();
        }
    }

        private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, boolean reverse) throws SVNException {
            ISVNDiffGenerator generator = new DefaultSVNDiffGenerator();

            boolean isRoot = getWcContext().getDb().isWCRoot(path2);
            final String target = isRoot ? null : SVNFileUtil.getFileName(path2);
            final File pathForUrl = isRoot ? path2 : SVNFileUtil.getParentFile(path2);

            final SVNURL anchorUrl = getRepositoryAccess().getTargetURL(SvnTarget.fromFile(pathForUrl));

            if (anchorUrl == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", pathForUrl);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            final SVNURL url1;
            if (pegRevision.isValid()) {
                final Structure<SvnRepositoryAccess.LocationsInfo> locations = getRepositoryAccess().getLocations(null, SvnTarget.fromFile(path1), pegRevision, revision1, revision2);
                final SVNURL startUrl = locations.get(SvnRepositoryAccess.LocationsInfo.startUrl);
                final SVNURL endUrl = locations.get(SvnRepositoryAccess.LocationsInfo.endUrl);
                final long startRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.startRevision);
                final long endRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.endRevision);

                url1 = startUrl;
                String anchorPath2 = SVNPathUtil.append(anchorUrl.toString(), target == null ? "" : target);
                if (!reverse) {
                    generator.init(url1.toString(), anchorPath2);
                } else {
                    generator.init(anchorPath2, url1.toString());
                }

            } else {
                url1 = getRepositoryAccess().getTargetURL(SvnTarget.fromFile(path1));
            }


        SVNRepository repository = getRepositoryAccess().createRepository(anchorUrl, null, true);
        long revNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromURL(url1), revision1, null).lng(SvnRepositoryAccess.RevisionsPair.youngestRevision);
        ISvnDiffCallback callback = new SvnDiffCallback(generator, reverse ? -1 : revNumber, reverse ? revNumber : -1, getOperation().getOutput());
        SVNDiffEditor17 editor = new SVNDiffEditor17(getWcContext(), pathForUrl, getOperation().getDepth(), revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED,
                reverse, callback, !getOperation().isIgnoreAncestry(), getOperation().getApplicableChangelists(), false, getOperation().isShowCopiesAsAdds());
        boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
        final SVNReporter17 reporter17 = new SVNReporter17(path2, getWcContext(), false, !serverSupportsDepth, getOperation().getDepth(), false, false, true, false, SVNDebugLog.getDefaultLog());
        long pegRevisionNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromFile(path2), revision2, null).lng(SvnRepositoryAccess.RevisionsPair.revNumber);
        try {
            repository.diff(url1, revNumber, pegRevisionNumber, target, getOperation().isIgnoreAncestry(), getOperation().getDepth(), true, reporter17, SVNCancellableEditor.newInstance(editor, this, SVNDebugLog.getDefaultLog()));
        } finally {
            editor.cleanup();
        }
    }
}
