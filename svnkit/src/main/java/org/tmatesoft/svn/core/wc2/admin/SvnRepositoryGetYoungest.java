package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositoryGetYoungest extends SvnOperation<Long> {
    
    private File repositoryRoot;
        
    public SvnRepositoryGetYoungest(SvnOperationFactory factory) {
        super(factory);
    }

	public File getRepositoryRoot() {
		return repositoryRoot;
	}

	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}
	
	

	

	    
}
