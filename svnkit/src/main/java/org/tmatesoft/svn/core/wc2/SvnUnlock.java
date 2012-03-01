package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

/**
 * Operation for unlocking files. It unlocks file items in a working copy as well as in a repository.
 * 
 * <p/>
 * {@link #run()} method returns {@link SVNLock} object that represents information of lock.
 * 
 * @author TMate Software Ltd.
 */
public class SvnUnlock extends SvnReceivingOperation<SVNLock> {

	private boolean breakLock;
	
    protected SvnUnlock(SvnOperationFactory factory) {
        super(factory);
    }
    
   /**
   * Gets whether or not the locks belonging to different users should be also unlocked ("broken")
   * 
   * @return <code>true</code> if other users locks should be "broken", otherwise <code>false</code>
   */
    public boolean isBreakLock() {
        return breakLock;
    }
    
    /**
     * Sets whether or not the locks belonging to different users should be also unlocked ("broken")
     * 
     * @param breakLock <code>true</code> if other users locks should be "broken", otherwise <code>false</code>
     */
    public void setBreakLock(boolean breakLock) {
        this.breakLock = breakLock;
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

}
