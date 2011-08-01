package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNUpdateClient16;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnCheckout;

public class SvnOldCheckout extends SvnLocalOperationRunner<Long, SvnCheckout> {

    @Override
    protected Long run() throws SVNException {        
        SVNUpdateClient16 client = new SVNUpdateClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        client.setIgnoreExternals(getOperation().isIgnoreExternals());
        client.setUpdateLocksOnDemand(getOperation().isUpdateLocksOnDemand());
        client.setEventHandler(getOperation().getEventHandler());
        return client.doCheckout(getOperation().getUrl(), 
                getFirstTarget(), 
                getOperation().getPegRevision(), 
                getOperation().getRevision(), 
                getOperation().getDepth(), 
                getOperation().isAllowUnversionedObstructions());
    }
}
