package org.tmatesoft.svn.core.internal.wc2.old;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNDiffClient16;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldDiff extends SvnOldRunner<Void, SvnDiff> {

    @Override
    public boolean isApplicable(SvnDiff operation, SvnWcGeneration wcGeneration) throws SVNException {
        if (wcGeneration != SvnWcGeneration.V16) {
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
    protected Void run() throws SVNException {
        final SVNDiffClient16 diffClient = new SVNDiffClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        diffClient.setDiffGenerator(getDiffGenerator());
        diffClient.setMergeOptions(getOperation().getDiffOptions());

        final SVNRevision startRevision = getOperation().getStartRevision() == null ? SVNRevision.UNDEFINED : getOperation().getStartRevision();
        final SVNRevision endRevision = getOperation().getEndRevision() == null ? SVNRevision.UNDEFINED : getOperation().getEndRevision();

        final SvnTarget firstTarget = getOperation().getFirstTarget();
        final SvnTarget secondTarget = getOperation().getSecondTarget();

        final boolean peggedDiff = secondTarget == null;
        if (peggedDiff) {
            diffClient.doDiff(firstTarget.getFile(), firstTarget.getResolvedPegRevision(), startRevision, endRevision,
                    getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
        } else {
            if (firstTarget.isURL() && secondTarget.isFile()) {
                diffClient.doDiff(firstTarget.getURL(), startRevision, secondTarget.getFile(), endRevision,
                        getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else if (firstTarget.isFile() && secondTarget.isURL()) {
                diffClient.doDiff(firstTarget.getFile(), startRevision, secondTarget.getURL(), endRevision, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else if (firstTarget.isFile() && secondTarget.isFile()) {
                diffClient.doDiff(firstTarget.getFile(), startRevision, secondTarget.getFile(), endRevision, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else {
                throw new UnsupportedOperationException("URL-URL diff is not supported");
            }
        }

        return null;
    }

    private ISVNDiffGenerator getDiffGenerator() {
        return getOperation().getDiffGenerator() != null ? getOperation().getDiffGenerator() : new DefaultSVNDiffGenerator();
    }
}
