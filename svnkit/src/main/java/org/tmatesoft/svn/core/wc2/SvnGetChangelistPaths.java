package org.tmatesoft.svn.core.wc2;

/**
 * Gets paths belonging to the specified <code>changelists</code> discovered under the
 * specified <code>targets</code>.
 * 
 * <p/>
 * Note: this method does not require repository access.
 * 
 * {@link #run()} method returns first path belonging to changelist.
 * Use <code>receivedObjects</code> or {@link ISvnObjectReceiver} to access
 * all paths. 
 * 
 * @author TMate Software Ltd.
 * @version 1.7
 */
public class SvnGetChangelistPaths extends SvnReceivingOperation<String> {

	protected SvnGetChangelistPaths(SvnOperationFactory factory) {
        super(factory);
    }
    
    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

    
    
}
