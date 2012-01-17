package org.tmatesoft.svn.core.wc2.admin;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositorySynchronize extends SvnReceivingOperation<SVNAdminEvent> {
    
    private SVNURL toURL;
    
    public SvnRepositorySynchronize(SvnOperationFactory factory) {
        super(factory);
    }

	public SVNURL getToURL() {
		return toURL;
	}

	public void setToURL(SVNURL toURL) {
		this.toURL = toURL;
	}

	

	    
}
