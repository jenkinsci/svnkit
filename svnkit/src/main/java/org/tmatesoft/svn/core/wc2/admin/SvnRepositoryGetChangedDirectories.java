package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryGetChangedDirectories extends SvnReceivingOperation<String> {
    
    private File repositoryRoot;
    private String transactionName;
               
    public SvnRepositoryGetChangedDirectories(SvnOperationFactory factory) {
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

	

		
	

	

	    
}
