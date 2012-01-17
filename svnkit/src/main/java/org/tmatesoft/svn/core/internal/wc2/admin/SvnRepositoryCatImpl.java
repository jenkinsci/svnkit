package org.tmatesoft.svn.core.internal.wc2.admin;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryCat;


public class SvnRepositoryCatImpl extends SvnRepositoryOperationRunner<Long, SvnRepositoryCat> {

    @Override
    protected Long run() throws SVNException {
        SVNLookClient lc = new SVNLookClient(getOperation().getAuthenticationManager(), getOperation().getOptions());
        lc.setEventHandler(this);
        
        lc.doCat(getOperation().getRepositoryRoot(), getOperation().getPath(), getOperation().getTransactionName(), getOperation().getOutputStream());
        
        return 1l;
    }
}
