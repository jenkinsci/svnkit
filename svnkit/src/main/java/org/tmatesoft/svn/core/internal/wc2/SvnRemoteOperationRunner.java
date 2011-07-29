package org.tmatesoft.svn.core.internal.wc2;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.old.SvnOldRepositoryAccess;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnRemoteOperationRunner<V, T extends SvnOperation<V>> extends SvnOperationRunner<V, T> {
    
    private SvnRepositoryAccess repositoryAccess;
    
    public void reset() {
        super.reset();
        repositoryAccess = null;
    }

    public boolean isApplicable(SvnOperation operation, SvnWcGeneration wcGeneration) throws SVNException {
        return operation.hasRemoteTargets();
    }
    
    protected SvnRepositoryAccess getRepositoryAccess() throws SVNException {
        if (repositoryAccess == null) {
            if (getWcGeneration() == SvnWcGeneration.V16) {
                repositoryAccess = new SvnOldRepositoryAccess(getOperation());
            } else {
                repositoryAccess = new SvnNgRepositoryAccess(getOperation());
            }            
        }
        return repositoryAccess;
    }
    
    protected boolean isRevisionLocalToWc(SVNRevision revision) {
        return revision == SVNRevision.BASE || revision == SVNRevision.WORKING || revision == SVNRevision.COMMITTED;
    }
}
