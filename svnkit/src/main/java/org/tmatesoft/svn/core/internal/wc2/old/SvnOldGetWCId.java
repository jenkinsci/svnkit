package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnGetWCId;

public class SvnOldGetWCId extends SvnOldRunner<String, SvnGetWCId> {

    @Override
    protected String run() throws SVNException {
        final SVNWCClient svnWcClient = new SVNWCClient(getOperation().getRepositoryPool(), getOperation().getOptions());

        return svnWcClient.doGetWorkingCopyID(
                getOperation().getFirstTarget().getFile(),
                getOperation().getTrailUrl(),
                getOperation().isCommitted());
    }
}
