package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.wc2.SvnCat;

public class SvnOldCat extends SvnOldRunner<Long, SvnCat> {
    @Override
    protected Long run() throws SVNException {
        
        SVNWCClient16 client = new SVNWCClient16(getOperation().getRepositoryPool(), getOperation().getOptions());
        
        if (getOperation().hasRemoteTargets()) {
        	client.doGetFileContents(
            		getOperation().getFirstTarget().getURL(),
        			getOperation().getFirstTarget().getPegRevision(), 
        			getOperation().getRevision(),
        			getOperation().isExpandKeywords(),
        			getOperation().getOutput());
        }
        else {
        	client.doGetFileContents(
            		getOperation().getFirstTarget().getFile(),
        			getOperation().getFirstTarget().getPegRevision(), 
        			getOperation().getRevision(),
        			getOperation().isExpandKeywords(),
        			getOperation().getOutput());
        }
        
        return Long.decode("1");
    }

}
