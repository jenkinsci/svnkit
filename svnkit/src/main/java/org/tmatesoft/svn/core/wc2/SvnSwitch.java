package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * Represents switch operation.
 * Switches working tree of <code>target</code> to <code>switchTarget</code>\
 * <code>switchTarget</code>'s <code>pegRevision</code> at <code>revision</code>.
 * 
 * <p/>
 * Summary of purpose: this is normally used to switch a working directory
 * over to another line of development, such as a branch or a tag. Switching
 * an existing working directory is more efficient than checking out
 * <code>switchTarget</code> from scratch.
 * 
 * <p/>
 * <code>revision</code> must represent a valid revision number (
 * {@link SVNRevision#getNumber()} >= 0), or date (
 * {@link SVNRevision#getDate()} != <span class="javakeyword">true</span>),
 * or be equal to {@link SVNRevision#HEAD}. If <code>revision</code> does
 * not meet these requirements, an exception with the error code
 * {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
 * 
 * <p/>
 * If <code>depth</code> is {@link SVNDepth#INFINITY}, switches fully
 * recursively. Else if it is {@link SVNDepth#IMMEDIATES}, switches
 * <code>target</code> and its file children (if any), and switches
 * subdirectories but does not update them. Else if {@link SVNDepth#FILES},
 * switches just file children, ignoring subdirectories completely. Else if
 * {@link SVNDepth#EMPTY}, switches just <code>target</code> and touches
 * nothing underneath it.
 * 
 * <p/>
 * If <code>depthIsSticky</code> is set and <code>depth</code> is not
 * {@link SVNDepth#UNKNOWN}, then in addition to switching <code>path</code>
 * , also sets its sticky ambient depth value to <code>depth</code>.
 * 
 * <p/>
 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process
 * externals definitions as part of this operation.
 * 
 * <p/>
 * If <code>allowUnversionedObstructions</code> is <span
 * class="javakeyword">true</span> then the switch tolerates existing
 * unversioned items that obstruct added paths. Only obstructions of the
 * same type (file or dir) as the added item are tolerated. The text of
 * obstructing files is left as-is, effectively treating it as a user
 * modification after the switch. Working properties of obstructing items
 * are set equal to the base properties. If
 * <code>allowUnversionedObstructions</code> is <span
 * class="javakeyword">false</span> then the switch will abort if there are
 * any unversioned obstructing items.
 * 
 * <p/>
 * If the caller's {@link ISVNEventHandler} is non-<span
 * class="javakeyword">null</span>, it is invoked for paths affected by the
 * switch, and also for files restored from text-base. Also
 * {@link ISVNEventHandler#checkCancelled()} will be used at various places
 * during the switch to check whether the caller wants to stop the switch.
 * 
 * <p/>
 * This operation requires repository access (in case the repository is not
 * on the same machine, network connection is established).
 * 
 * <p/>
 * {@link #run()} method returns value of the revision to which the working copy was actually switched.
 * 
 * @author TMate Software Ltd.
 * @version 1.7
 */
public class SvnSwitch extends AbstractSvnUpdate<Long> {

    private boolean depthIsSticky;
    private boolean ignoreAncestry;
    
    private SvnTarget switchTarget;

    protected SvnSwitch(SvnOperationFactory factory) {
        super(factory);
    }

    /**
     * Gets whether or not the requested depth should be written to the working copy.
     * 
     * @return <code>true</code> if the requested depth should be written to the working copy, otherwise <code>false</code>
     */
    public boolean isDepthIsSticky() {
        return depthIsSticky;
    }

    /**
     * Returns whether to ignore ancestry when calculating merges.
     * 
     * @return <code>true</code> if ancestry should be ignored, otherwise <code>false</code>
     * @since 1.7, Subversion 1.7
     */
    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    /**
     * Returns the repository location as a target against which the item will be switched.
     * 
     * @return switch target
     */
    public SvnTarget getSwitchTarget() {
        return switchTarget;
    }

    /**
     * Sets whether or not the requested depth should be written to the working copy.
     *
     * @param depthIsSticky <code>true</code> if the requested depth should be written to the working copy, otherwise <code>false</code>
     */
    public void setDepthIsSticky(boolean depthIsSticky) {
        this.depthIsSticky = depthIsSticky;
    }

    /**
     * Sets whether to ignore ancestry when calculating merges.
     * 
     * @param ignoreAncestry <code>true</code> if ancestry should be ignored, otherwise <code>false</code>
     * @since 1.7, Subversion 1.7
     */
    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    /**
     * Sets the repository location as a target against which the item will be switched.
     * 
     * @param switchTarget switch target
     */
    public void setSwitchTarget(SvnTarget switchTarget) {
        this.switchTarget = switchTarget;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.EXCLUDE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot both exclude and switch a path");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    
}
