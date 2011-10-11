package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

public class SvnSetLock extends SvnReceivingOperation<SVNLock> {

	private boolean stealLock;
	private String lockMessage;
	 
    protected SvnSetLock(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isStealLock() {
        return stealLock;
    }
    
    public void setStealLock(boolean stealLock) {
        this.stealLock = stealLock;
    }
    
    public String getLockMessage() {
        return lockMessage;
    }
    
    public void setLockMessage(String lockMessage) {
    	this.lockMessage = lockMessage;
    }

    


}
