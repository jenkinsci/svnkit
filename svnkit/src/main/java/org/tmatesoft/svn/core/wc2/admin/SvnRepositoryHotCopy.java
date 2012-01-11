package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositoryHotCopy extends SvnOperation<Long> {
    
    private File srcRepositoryRoot;
    private File newRepositoryRoot;

    public SvnRepositoryHotCopy(SvnOperationFactory factory) {
        super(factory);
    }

	public File getSrcRepositoryRoot() {
		return srcRepositoryRoot;
	}

	public void setSrcRepositoryRoot(File srcRepositoryRoot) {
		this.srcRepositoryRoot = srcRepositoryRoot;
	}

	public File getNewRepositoryRoot() {
		return newRepositoryRoot;
	}

	public void setNewRepositoryRoot(File newRepositoryRoot) {
		this.newRepositoryRoot = newRepositoryRoot;
	}

    
}
