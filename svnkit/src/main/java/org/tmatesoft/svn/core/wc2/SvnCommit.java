package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/**
* Commit operation. Commits files or directories into repository.
* 
* <p/>
* If <code>targets</code> has zero elements, then do nothing and return
* immediately without error.
* 
* <p/>
* If non-<span class="javakeyword">null</span>, <code>revisionProperties</code>
* holds additional, custom revision properties (<code>String</code> names
* mapped to {@link SVNPropertyValue} values) to be set on the new revision.
* This table cannot contain any standard Subversion properties.
* 
* <p/>
* If the caller's {@link ISVNEventHandler event handler} is not <span
* class="javakeyword">null</span> it will be called as the commit
* progresses with any of the following actions:
* {@link SVNEventAction#COMMIT_MODIFIED},
* {@link SVNEventAction#COMMIT_ADDED},
* {@link SVNEventAction#COMMIT_DELETED},
* {@link SVNEventAction#COMMIT_REPLACED}. If the commit succeeds, the
* handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event
* action.
* 
* <p/>
* If <code>depth</code> is {@link SVNDepth#INFINITY}, commits all changes
* to and below named targets. If <code>depth</code> is
* {@link SVNDepth#EMPTY}, commits only named targets (that is, only
* property changes on named directory targets, and property and content
* changes for named file targets). If <code>depth</code> is
* {@link SVNDepth#FILES}, behaves as above for named file targets, and for
* named directory targets, commits property changes on a named directory
* and all changes to files directly inside that directory. If
* {@link SVNDepth#IMMEDIATES}, behaves as for {@link SVNDepth#FILES}, and
* for subdirectories of any named directory target commits as though for
* {@link SVNDepth#EMPTY}.
* 
* <p/>
* Unlocks paths in the repository, unless <code>keepLocks</code> is <span
* class="javakeyword">true</span>.
* 
* <p/>
* {@link #getApplicableChangelists()} used as a restrictive filter on items that are committed; that is,
* doesn't commit anything unless it's a member of one of those changelists.
* After the commit completes successfully, removes changelist associations
* from the targets, unless <code>keepChangelist</code> is set. If
* <code>changelists</code> is empty (or altogether <span
* class="javakeyword">null</span>), no changelist filtering occurs.
* 
* <p/>
* If no exception is thrown and {@link SVNCommitInfo#getNewRevision()} is
* invalid (<code>&lt;0</code>), then the commit was a no-op; nothing needed
* to be committed.
*
* @return information about the new committed revision
* {@link #run()} returns first {@link SVNCommitInfo}, containing information 
* about new commited revision.
* 
* @author TMate Software Ltd.
* */
public class SvnCommit extends AbstractSvnCommit {
    
    private boolean keepChangelists;
    private boolean keepLocks;
    private ISvnCommitParameters commitParameters;
    
    private SvnCommitPacket packet;
    private boolean force;

    protected SvnCommit(SvnOperationFactory factory) {
        super(factory);
    }
    
    /**
     * Gets whether or not <code>changelists</code> should be removed.
     * 
     * @return <code>true</code> if <code>changelists</code> should be removed, otherwise <code>false</code>
     */
    public boolean isKeepChangelists() {
        return keepChangelists;
    }
    
    /**
     * Sets whether or not <code>changelists</code> should be removed.
     * 
     * @param keepChangelists <code>true</code> if <code>changelists</code> should be removed, otherwise <code>false</code>
     */
    public void setKeepChangelists(boolean keepChangelists) {
        this.keepChangelists = keepChangelists;
    }
     
    /**
     * Gets whether or not to unlock files in the repository.
     * 
     * @return <code>true</code> if files should not be unlocked in the repository, otherwise <code>false</code>
     */
    public boolean isKeepLocks() {
        return keepLocks;
    }

    /**
     * Sets whether or not to unlock files in the repository.
     * 
     * @param keepLocks <code>true</code> if files should not be unlocked in the repository, otherwise <code>false</code>
     */
    public void setKeepLocks(boolean keepLocks) {
        this.keepLocks = keepLocks;
    }

    /**
     * Gets parameters of the commit. See {@link ISvnCommitParameters} 
     * 
     * @return commit parameters
     */
    public ISvnCommitParameters getCommitParameters() {
        return commitParameters;
    }

    /**
     * Sets parameters of the commit. See {@link ISvnCommitParameters} 
     * 
     * @param commitParameters commit parameters
     */
    public void setCommitParameters(ISvnCommitParameters commitParameters) {
        this.commitParameters = commitParameters;
    }
    
    /**
     * Gets whether or not the requested depth should be written to the working copy.
     * 
     * @return <code>true</code> if the requested depth should be written to the working copy, otherwise <code>false</code>
     */
    public SvnCommitPacket collectCommitItems() throws SVNException {
        ensureArgumentsAreValid();        
        if (packet != null) {
            return packet;
        }
        packet = getOperationFactory().collectCommitItems(this);
        return packet;
    }
    
    public SVNCommitInfo run() throws SVNException {
        if (packet == null) {
            packet = collectCommitItems();
        }
        return super.run();
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.INFINITY);
        }
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * Gets whether or not to force a non-recursive commit; if <code>depth</code> 
     * is {@link SVNDepth#INFINITY} the <code>force</code> flag is ignored.
     * 
     * @return <code>true</code> if non-recursive commit should be forced, otherwise <code>false</code>
     */
    public void setForce(boolean force) {
        this.force = force;
    }
    
    /**
     * Sets whether or not to force a non-recursive commit; if <code>depth</code> 
     * is {@link SVNDepth#INFINITY} the <code>force</code> flag is ignored.
     * 
     * @return <code>true</code> if non-recursive commit should be forced, otherwise <code>false</code>
     */
    public boolean isForce() {
        return this.force;
    }
}
