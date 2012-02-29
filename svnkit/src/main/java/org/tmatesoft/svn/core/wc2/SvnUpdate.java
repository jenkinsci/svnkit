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
 * This class encapsulates update operation. It's {@link #run()} method updates working copy to <code>revision</code>.
 * If no revision is given, it brings working copy up-to-date with {@link SVNRevision#HEAD} revision.
   
 * Unversioned paths that are direct children of a versioned path will cause
 * an update that attempts to add that path, other unversioned paths are
 * skipped.
 *  
 * <p/>
 * The <code>targets</code> can be from multiple working copies from
 * multiple repositories, but even if they all come from the same repository
 * there is no guarantee that revision represented by
 * {@link SVNRevision#HEAD} will remain the same as each path is updated.
 * 
 * <p/>
 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process
 * externals definitions as part of this operation.
 * 
 * <p/>
 * If <code>depth</code> is {@link SVNDepth#INFINITY}, updates fully
 * recursively. Else if it is {@link SVNDepth#IMMEDIATES} or
 * {@link SVNDepth#FILES}, updates each target and its file entries, but not
 * its subdirectories. Else if {@link SVNDepth#EMPTY}, updates exactly each
 * target, nonrecursively (essentially, updates the target's properties).
 * 
 * <p/>
 * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, takes the working
 * depth from <code>paths</code> and then behaves as described above.
 * 
 * <p/>
 * If <code>depthIsSticky</code> is set and <code>depth</code> is not
 * {@link SVNDepth#UNKNOWN}, then in addition to updating <code>paths</code>
 * , also sets their sticky ambient depth value to <code>depth</code>.
 * 
 * <p/>
 * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the update tolerates existing
 * unversioned items that obstruct added paths. Only obstructions of the
 * same type (file or dir) as the added item are tolerated. The text of
 * obstructing files is left as-is, effectively treating it as a user
 * modification after the update. Working properties of obstructing items
 * are set equal to the base properties. If
 * <code>allowUnversionedObstructions</code> is <span class="javakeyword">false</span> then the update will abort if there are
 * any unversioned obstructing items.
 * 
 * <p/>
 * If the operation's {@link ISVNEventHandler} is non-<span
 * class="javakeyword">null</span>, it is invoked for each item handled by
 * the update, and also for files restored from text-base. Also
 * {@link ISVNEventHandler#checkCancelled()} will be used at various places
 * during the update to check whether the caller wants to stop the update.
 * 
 * <p/>
 * This operation requires repository access (in case the repository is not
 * on the same machine, network connection is established).
 * 
* <p/>
 * {@link #run()} method returns an array of <code>long</code> revisions with each element set to
 *         the revision to which <code>revision</code> was resolved.
 * 
 * @author TMate Software Ltd.
 */
public class SvnUpdate extends AbstractSvnUpdate<long[]> {
    
    private boolean depthIsSticky;
    private boolean makeParents;
    private boolean treatAddsAsModifications;

    protected SvnUpdate(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        for (SvnTarget target : getTargets()) {
            if (target.isURL()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is not a local path", target.getURL());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (getDepth() == null) {
            setDepth(SVNDepth.UNKNOWN);
        }
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepthIsSticky(false);
        }
        super.ensureArgumentsAreValid();
    }
    
    /**
     * Get flag that controls whether the requested depth should be written to the working copy.
     */
    public boolean isDepthIsSticky() {
        return depthIsSticky;
    }

    /**
     * Get flag that controls whether to make intermediate directories.
     */
    public boolean isMakeParents() {
        return makeParents;
    }

    /**
     * Get flag that controls whether to treat adds as modifications.
     */
    public boolean isTreatAddsAsModifications() {
        return treatAddsAsModifications;
    }

    /**
     * Set flag that controls whether the requested depth should be written to the working copy.
     * @param depthIsSticky
     */
    public void setDepthIsSticky(boolean depthIsSticky) {
        this.depthIsSticky = depthIsSticky;
    }

    /**
     * Set flag that controls whether to make intermediate directories.
     * @param makeParents
     */
    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }
    
    /**
     * Set flag that controls whether to treat adds as modifications.
     * @param treatAddsAsModifications
     */
    public void setTreatAddsAsModifications(boolean treatAddsAsModifications) {
        this.treatAddsAsModifications = treatAddsAsModifications;
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
    
    
}
