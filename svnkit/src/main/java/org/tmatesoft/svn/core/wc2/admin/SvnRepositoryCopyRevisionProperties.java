package org.tmatesoft.svn.core.wc2.admin;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryCopyRevisionProperties extends SvnReceivingOperation<SVNAdminEvent> {
    
    private SVNURL toURL;
    private Long startRevision;
    private Long endRevision;
    
    public SvnRepositoryCopyRevisionProperties(SvnOperationFactory factory) {
        super(factory);
    }	

	public SVNURL getToURL() {
		return toURL;
	}

	public void setToURL(SVNURL toURL) {
		this.toURL = toURL;
	}
	
	public Long getStartRevision() {
        return startRevision;
    }

    public void setStartRevision(Long startRevision) {
        this.startRevision = startRevision;
    }

    public Long getEndRevision() {
        return endRevision;
    }

    public void setEndRevision(Long endRevision) {
        this.endRevision = endRevision;
    }

	

	    
}
