package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;

import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryVerify extends SvnReceivingOperation<SVNAdminEvent> {
    
    private File repositoryRoot;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    
    public SvnRepositoryVerify(SvnOperationFactory factory) {
        super(factory);
    }
    
    @Override
    protected void initDefaults() {
    	super.initDefaults();
    	startRevision = SVNRevision.create(0);
        endRevision = SVNRevision.HEAD;
    }

	public File getRepositoryRoot() {
		return repositoryRoot;
	}

	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}
	
	public SVNRevision getStartRevision() {
        return startRevision;
    }

    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }

    public SVNRevision getEndRevision() {
        return endRevision;
    }

    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }

	    
}
