package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/**
 * Represents delete operation. Deletes items from a repository.
 * 
 * <p/>
 * All operation's targets should be URLs, representing repository locations to be removed. 
 * URLs can be from mupltiple repositories.
 * 
 * <p/>
 * {@link #getCommitHandler() Commit handler} will be asked for a commit log
 * message.
 * 
 * <p/>
 * If the caller's {@link ISVNEventHandler event handler} is not <span
 * class="javakeyword">null</span> and if the commit succeeds, the handler
 * will be called with {@link SVNEventAction#COMMIT_COMPLETED} event action.
 *
 * <p/>
 * {@link #run()} method returns {@link SVNCommitInfo} information on a new revision as the result of the commit.
 * This method throws SVNException if URL does not exist.
 * 
 * @author TMate Software Ltd.
 * @version 1.7
 */
public class SvnRemoteDelete extends AbstractSvnCommit {

    protected SvnRemoteDelete(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
    
    

}
