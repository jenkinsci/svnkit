package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.wc2.SvnCleanup;

public class SvnOldCleanup extends SvnOldRunner<Long, SvnCleanup> {
    @Override
    protected Long run() throws SVNException {
        
        SVNWCClient16 client = new SVNWCClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setEventHandler(getOperation().getEventHandler());
        
        client.doCleanup(getOperation().getFirstTarget().getFile(), getOperation().isDeleteWCProperties());
        
        return Long.decode("-1");
    }

}
