package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;

import org.tmatesoft.svn.core.wc.admin.SVNAdminPath;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryGetHistory extends SvnReceivingOperation<SVNAdminPath> {
    
    private File repositoryRoot;
    private String path;
    private boolean includeIDs;
    private long limit;
    
               
    public SvnRepositoryGetHistory(SvnOperationFactory factory) {
        super(factory);
    }

	public File getRepositoryRoot() {
		return repositoryRoot;
	}

	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isIncludeIDs() {
		return includeIDs;
	}

	public void setIncludeIDs(boolean includeIDs) {
		this.includeIDs = includeIDs;
	}

	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	

	

		
	

	

	    
}
