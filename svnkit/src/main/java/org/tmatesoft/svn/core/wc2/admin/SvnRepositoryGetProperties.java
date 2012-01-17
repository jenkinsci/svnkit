package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositoryGetProperties extends SvnOperation<SVNProperties> {
    
    private File repositoryRoot;
    private String transactionName;
    private String path;
    
    public SvnRepositoryGetProperties(SvnOperationFactory factory) {
        super(factory);
    }

	public File getRepositoryRoot() {
		return repositoryRoot;
	}

	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}
	
	public String getTransactionName() {
		return transactionName;
	}

	public void setTransactionName(String transactionName) {
		this.transactionName = transactionName;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	

	    
}
