package org.tmatesoft.svn.core.internal.wc2.remote;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnRemoteOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnRemoteDiff extends SvnRemoteOperationRunner<Void, SvnDiff> {

    @Override
    public boolean isApplicable(SvnDiff operation, SvnWcGeneration wcGeneration) throws SVNException {
        return !operation.hasLocalTargets();
    }

    @Override
    protected Void run() throws SVNException {
        assert getOperation().getTargets().size() == 1 || getOperation().getTargets().size() == 2;

        final SVNDiffClient diffClient = new SVNDiffClient(getOperation().getRepositoryPool(), getOperation().getOptions());
        diffClient.setDiffGenerator(getOperation().getDiffGenerator());
        diffClient.setMergeOptions(getOperation().getDiffOptions());

        final Collection<SvnTarget> targets = getOperation().getTargets();
        if (targets.size() == 1) {
            diffClient.doDiff(getOperation().getFirstTarget().getURL(),
                    getOperation().getFirstTarget().getResolvedPegRevision(),
                    getOperation().getStartRevision(),
                    getOperation().getEndRevision(),
                    getOperation().getDepth(),
                    !getOperation().isIgnoreAncestry(),
                    getOperation().getOutput());
        } else if (targets.size() == 2) {
            diffClient.doDiff(getOperation().getFirstTarget().getURL(), getOperation().getFirstTarget().getPegRevision(),
                    getOperation().getSecondTarget().getURL(), getOperation().getSecondTarget().getPegRevision(),
                    getOperation().getDepth(),
                    !getOperation().isIgnoreAncestry(),
                    getOperation().getOutput());
        }

        return null;
    }
}
