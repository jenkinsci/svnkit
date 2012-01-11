package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;
import java.io.InputStream;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositoryLoad extends SvnOperation<Long> {
    
    private File repositoryRoot;
    private InputStream dumpStream;
    private boolean usePreCommitHook;
    private boolean usePostCommitHook;
    private SVNUUIDAction uuidAction = SVNUUIDAction.DEFAULT;
    private String parentDir;
    

    public SvnRepositoryLoad(SvnOperationFactory factory) {
        super(factory);
    }


	public File getRepositoryRoot() {
		return repositoryRoot;
	}


	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}


	public InputStream getDumpStream() {
		return dumpStream;
	}


	public void setDumpStream(InputStream dumpStream) {
		this.dumpStream = dumpStream;
	}


	public boolean isUsePreCommitHook() {
		return usePreCommitHook;
	}


	public void setUsePreCommitHook(boolean usePreCommitHook) {
		this.usePreCommitHook = usePreCommitHook;
	}


	public boolean isUsePostCommitHook() {
		return usePostCommitHook;
	}


	public void setUsePostCommitHook(boolean usePostCommitHook) {
		this.usePostCommitHook = usePostCommitHook;
	}


	public SVNUUIDAction getUuidAction() {
		return uuidAction;
	}


	public void setUuidAction(SVNUUIDAction uuidAction) {
		this.uuidAction = uuidAction;
	}


	public String getParentDir() {
		return parentDir;
	}


	public void setParentDir(String parentDir) {
		this.parentDir = parentDir;
	}
    
   

    
}
