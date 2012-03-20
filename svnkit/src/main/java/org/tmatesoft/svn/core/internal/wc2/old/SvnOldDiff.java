package org.tmatesoft.svn.core.internal.wc2.old;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNDiffClient16;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNewDiffGenerator;
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

        final boolean peggedDiff = getOperation().getSource() != null;
        if (peggedDiff) {
            final SVNRevision startRevision = getOperation().getStartRevision() == null ? SVNRevision.UNDEFINED : getOperation().getStartRevision();
            final SVNRevision endRevision = getOperation().getEndRevision() == null ? SVNRevision.UNDEFINED : getOperation().getEndRevision();

            diffClient.doDiff(getOperation().getSource().getFile(), getOperation().getSource().getResolvedPegRevision(),
                    startRevision, endRevision,
                    getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
        } else {
            final SVNRevision startRevision = getOperation().getFirstSource().getPegRevision() == null ? SVNRevision.UNDEFINED : getOperation().getFirstSource().getPegRevision();
            final SVNRevision endRevision = getOperation().getSecondSource().getPegRevision() == null ? SVNRevision.UNDEFINED : getOperation().getSecondSource().getPegRevision();

            if (getOperation().getFirstSource().isURL() && getOperation().getSecondSource().isFile()) {
                diffClient.doDiff(getOperation().getFirstSource().getURL(), startRevision, getOperation().getSecondSource().getFile(), endRevision,
                        getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else if (getOperation().getFirstSource().isFile() && getOperation().getSecondSource().isURL()) {
                diffClient.doDiff(getOperation().getFirstSource().getFile(), startRevision, getOperation().getSecondSource().getURL(), endRevision, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else if (getOperation().getFirstSource().isFile() && getOperation().getSecondSource().isFile()) {
                diffClient.doDiff(getOperation().getFirstSource().getFile(), startRevision, getOperation().getSecondSource().getFile(), endRevision, getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
            } else {
                throw new UnsupportedOperationException("URL-URL diff is not supported");
            }
        }

        return null;
    }

    private ISVNDiffGenerator getDiffGenerator() {
        return getOperation().getDiffGenerator() != null ? new SvnNewDiffGenerator(getOperation().getDiffGenerator()) : new DefaultSVNDiffGenerator();
    }
}
