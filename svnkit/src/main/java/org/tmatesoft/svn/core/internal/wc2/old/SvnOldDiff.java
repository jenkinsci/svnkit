package org.tmatesoft.svn.core.internal.wc2.old;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldDiff extends SvnOldRunner<Void, SvnDiff> {

    @Override
    public boolean isApplicable(SvnDiff operation, SvnWcGeneration wcGeneration) throws SVNException {
        final Collection<SvnTarget> targets = operation.getTargets();
        for (SvnTarget target : targets) {
            if (!target.isFile()) {
                return false;
            }
        }
        return wcGeneration == SvnWcGeneration.V16;
    }

    @Override
    protected Void run() throws SVNException {
        final SVNDiffClient diffClient = new SVNDiffClient(getOperation().getRepositoryPool(), getOperation().getOptions());
        diffClient.setDiffGenerator(getOperation().getDiffGenerator());
        diffClient.setMergeOptions(getOperation().getDiffOptions());

        final Collection<SvnTarget> targets = getOperation().getTargets();
        for (SvnTarget target : targets) {
            final SVNRevision startRevision = getOperation().getStartRevision() == null ? SVNRevision.UNDEFINED : getOperation().getStartRevision();
            final SVNRevision endRevision = getOperation().getEndRevision() == null ? SVNRevision.UNDEFINED : getOperation().getEndRevision();
            diffClient.doDiff(target.getFile(), target.getResolvedPegRevision(), startRevision, endRevision,
                    getOperation().getDepth(), !getOperation().isIgnoreAncestry(), getOperation().getOutput(), getOperation().getApplicableChangelists());
        }
        return null;
    }
}
