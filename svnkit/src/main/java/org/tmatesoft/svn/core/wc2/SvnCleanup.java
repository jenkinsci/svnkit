package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc2.SvnOperation;

public class SvnCleanup extends SvnOperation<Void> {
	
	private boolean deleteWCProperties;

    protected SvnCleanup(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isDeleteWCProperties() {
        return deleteWCProperties;
    }

    public void setDeleteWCProperties(boolean deleteWCProperties) {
        this.deleteWCProperties = deleteWCProperties;
    }

}
