package org.tmatesoft.svn.core.internal.wc2.admin;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryLoad;

public class SvnRepositoryLoadImpl extends SvnRepositoryOperationRunner<Long, SvnRepositoryLoad> {

    @Override
    protected Long run() throws SVNException {
        SVNAdminClient ac = new SVNAdminClient(getOperation().getAuthenticationManager(), getOperation().getOptions());
        ac.setEventHandler(this);
                
        ac.doLoad(
        		getOperation().getRepositoryRoot(), 
        		getOperation().getDumpStream(), 
        		getOperation().isUsePreCommitHook(),
        		getOperation().isUsePostCommitHook(),
        		getOperation().getUuidAction(),
        		getOperation().getParentDir());
        
        return 1l;
    }

    
}
