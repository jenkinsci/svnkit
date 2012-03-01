package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

/**
 * Operation for locking files. It locks file items in a working copy as well as in a repository so that 
 * no other user can commit changes to them.
 * 
* <p/>
 * {@link #run()} method returns {@link SVNLock} object that represents information of lock.
 * 
 * @author TMate Software Ltd.
 */
public class SvnSetLock extends SvnReceivingOperation<SVNLock> {

	private boolean stealLock;
	private String lockMessage;
	 
    protected SvnSetLock(SvnOperationFactory factory) {
        super(factory);
    }
    
    /**
     * Gets whether or not all existing locks on the specified targets 
     * will be "stolen" from another user or working copy.
     * 
     * @return <code>true</code> if locks should be "stolen", otherwise <code>false</code>
     */
    public boolean isStealLock() {
        return stealLock;
    }
    
    /**
     * Sets whether or not all existing locks on the specified targets 
     * will be "stolen" from another user or working copy.
     * 
     * @param stealLock <code>true</code> if locks should be "stolen", otherwise <code>false</code>
     */
    public void setStealLock(boolean stealLock) {
        this.stealLock = stealLock;
    }
    
    /**
     * Gets the optional comment for the lock.
     * 
     * @return comment for the lock
     */
    public String getLockMessage() {
        return lockMessage;
    }
    
    /**
     * Sets the optional comment for the lock.
     * 
     * @param lockMessage comment for the lock
     */
    public void setLockMessage(String lockMessage) {
    	this.lockMessage = lockMessage;
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
}
