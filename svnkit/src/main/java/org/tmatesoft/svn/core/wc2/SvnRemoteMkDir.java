package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/**
 * Creates directory(s) in a repository. 
 * 
 * <p/>
 * All operation's targets should be URLs, representing repository locations to be created. 
 * URLs can be from multiple repositories.
 * 
 * <p/>
 * If non-<code>null</code>, <code>revisionProperties</code> holds additional, custom revision
 * properties (<code>String</code> names mapped to {@link SVNPropertyValue}
 * values) to be set on the new revision. This table cannot contain any
 * standard Subversion properties.
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
 * {@link #run()} method returns {@link org.tmatesoft.svn.core.SVNCommitInfo} information on a new revision as the result of the commit.
 * This method throws SVNException if URL does not exist.
 * 
 * @author TMate Software Ltd.
 * @version 1.7
 */
public class SvnRemoteMkDir extends AbstractSvnCommit {

    private boolean makeParents;
    
    /**
     * Returns whether to create all non-existent parent directories
     * @return <code>true</code> if the non-existent parent directories should be created, otherwise <code>false</code>
     */
    public boolean isMakeParents() {
        return makeParents;
    }

    /**
     * Sets whether to create all non-existent parent directories
     * @param makeParents <code>true</code> if the non-existent parent directories should be created, otherwise <code>false</code>
     */
    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    protected SvnRemoteMkDir(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
    
    

}
