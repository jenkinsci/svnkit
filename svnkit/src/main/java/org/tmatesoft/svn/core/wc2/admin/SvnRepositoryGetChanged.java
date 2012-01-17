package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;

import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryGetChanged extends SvnReceivingOperation<SVNChangeEntry> {
    
    private File repositoryRoot;
    private String transactionName;
    private boolean includeCopyInfo;
            
    public SvnRepositoryGetChanged(SvnOperationFactory factory) {
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

	public boolean isIncludeCopyInfo() {
		return includeCopyInfo;
	}

	public void setIncludeCopyInfo(boolean includeCopyInfo) {
		this.includeCopyInfo = includeCopyInfo;
	}

		
	

	

	    
}
