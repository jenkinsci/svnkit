package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNDiffEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
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
        return wcGeneration == SvnWcGeneration.V17;
    }

    @Override
    protected Void run(SVNWCContext context) throws SVNException {
        final SvnTarget firstTarget = getOperation().getFirstTarget();
        final SvnTarget secondTarget = getOperation().getSecondTarget();

        //TODO
        doDiffURLWC(secondTarget.getURL(), secondTarget.getPegRevision(), SVNRevision.UNDEFINED, firstTarget.getFile(), firstTarget.getPegRevision(), true);

        return null;
    }

    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, boolean reverse) throws SVNException {

        final ISVNDiffGenerator diffGenerator = new DefaultSVNDiffGenerator();

        final File workingCopyRoot = getWcContext().getDb().getWCRoot(path2);
        final String target = SVNPathUtil.getRelativePath(workingCopyRoot.getAbsolutePath().replace(File.separatorChar, '/'),
                path2.getAbsolutePath().replace(File.separatorChar, '/'));

        final ISVNWCDb.WCDbBaseInfo baseInfo = getWcContext().getDb().getBaseInfo(path2, ISVNWCDb.WCDbBaseInfo.BaseInfoField.reposRootUrl, ISVNWCDb.WCDbBaseInfo.BaseInfoField.reposUuid);
        final SVNURL reposRootUrl = baseInfo.reposRootUrl;
        final String reposUuid = baseInfo.reposUuid;

        if (reposRootUrl == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", workingCopyRoot);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (pegRevision.isValid()) {
            final Structure<SvnRepositoryAccess.LocationsInfo> locations = getRepositoryAccess().getLocations(null, SvnTarget.fromURL(url1), pegRevision, revision1, revision2);
            final SVNURL startUrl = locations.get(SvnRepositoryAccess.LocationsInfo.startUrl);
            final SVNURL endUrl = locations.get(SvnRepositoryAccess.LocationsInfo.endUrl);
            final long startRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.startRevision);
            final long endRevision = locations.lng(SvnRepositoryAccess.LocationsInfo.endRevision);

            url1 = startUrl;
            diffGenerator.init(url1.toString(), path2.getAbsolutePath());
        }
        SVNRepository repository = getRepositoryAccess().createRepository(reposRootUrl, reposUuid, true);
        long revNumber = getRepositoryAccess().getRevisionNumber(repository, SvnTarget.fromURL(url1), revision1, null).lng(SvnRepositoryAccess.RevisionsPair.youngestRevision);
        ISvnDiffCallback callback = new SvnDiffCallback(diffGenerator, reverse ? -1 : revNumber, reverse ? revNumber : -1, getOperation().getOutput());
        SVNDiffEditor17 editor = new SVNDiffEditor17(getWcContext(), workingCopyRoot, getOperation().getDepth(), revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED,
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
