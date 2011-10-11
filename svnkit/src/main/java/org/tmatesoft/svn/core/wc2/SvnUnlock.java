package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

public class SvnUnlock extends SvnReceivingOperation<SVNLock> {

	private boolean breakLock;
	
    protected SvnUnlock(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isBreakLock() {
        return breakLock;
    }
    
    public void setBreakLock(boolean breakLock) {
        this.breakLock = breakLock;
    }

}
