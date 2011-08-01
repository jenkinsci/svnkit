package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnNgOperationRunner<V, T extends SvnOperation<V>> extends SvnLocalOperationRunner<V, T> {
    
    private SvnNgRepositoryAccess repositoryAccess;
    
    protected V run() throws SVNException {
        return run(getWcContext());
    }
    
    protected boolean matchesChangelist(File target) {
        return getWcContext().matchesChangelist(target, getOperation().getApplicableChangelists());
    }
    
    protected SvnNgRepositoryAccess getRepositoryAccess() throws SVNException {
        if (repositoryAccess == null) {
            repositoryAccess = new SvnNgRepositoryAccess(getOperation());
        }
        return repositoryAccess;
    }
    
    protected abstract V run(SVNWCContext context) throws SVNException;

    @Override
    public void reset() {
        super.reset();
        repositoryAccess = null;
    }

    public SvnWcGeneration getWcGeneration() {
        return SvnWcGeneration.V17;
    }
    
    

}
