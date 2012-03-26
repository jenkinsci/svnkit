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
 * Represents checkout operation. Checks out a working copy of {@link #getSource()} <code>target</code> at revision,
 * looked up at {@link SvnTarget#getPegRevision()}, using operation targets as the
 * root directory of the newly checked out working copy.
 * <p/>
 * If source {@link SvnTarget#getPegRevision()}> is {@link SVNRevision#UNDEFINED} or invalid, then it
 * defaults to {@link SVNRevision#HEAD}.
 * <p/>
 * If {@link #getDepth()} is {@link SVNDepth#INFINITY}, checks out fully
 * recursively. Else if it is {@link SVNDepth#IMMEDIATES}, checks out
 * source <code>target</code> and its immediate entries (subdirectories will be
 * present, but will be at depth {@link SVNDepth#EMPTY} themselves); else
 * {@link SVNDepth#FILES}, checks out source <code>target</code> and its file entries,
 * but no subdirectories; else if {@link SVNDepth#EMPTY}, checks out
 * source <code>target</code> as an empty directory at that depth, with no entries
 * present.
 * <p/>
 * If {@link #getDepth()} is {@link SVNDepth#UNKNOWN}, then behave as if for
 * {@link SVNDepth#INFINITY}, except in the case of resuming a previous
 * checkout of operation targets (i.e., updating), in which case uses the
 * depth of the existing working copy.
 * <p/>
 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process
 * externals definitions as part of this operation.
 * <p/>
 * If  {@link #isAllowUnversionedObstructions()} is <span
 * class="javakeyword">true</span> then the checkout tolerates existing
 * unversioned items that obstruct added paths from source target. Only
 * obstructions of the same type (file or directory) as the added item are
 * tolerated. The text of obstructing files is left as-is, effectively
 * treating it as a user modification after the checkout. Working properties
 * of obstructing items are set equal to the base properties. If
 * {@link #isAllowUnversionedObstructions()} is <span
 * class="javakeyword">false</span> then the checkout will abort if there
 * are any unversioned obstructing items.
 * <p/>
 * If the caller's {@link ISVNEventHandler} is non-<span
 * class="javakeyword">null</span>, it is invoked as the checkout processes.
 * Also {@link ISVNEventHandler#checkCancelled()} will be used at various
 * places during the checkout to check whether the caller wants to stop the
 * checkout.
 * <p/>
 * This operation requires repository access (in case the repository is not
 * on the same machine, network connection is established).
 *
 * <p/>
 * {@link #run()} method returns value of the revision actually checked out from the repository.
 * 
 * @author TMate Software Ltd.
 * @version 1.7
 */
public class SvnCheckout extends AbstractSvnUpdate<Long> {
    
    private SvnTarget source;

    protected SvnCheckout(SvnOperationFactory factory) {
        super(factory);
    }

    /**
    * Gets a repository location from where a working copy will be checked out.
    * 
    * @return source of repository
    */
    public SvnTarget getSource() {
        return source;
    }

    /**
     * Sets a repository location from where a working copy will be checked out.
     * 
     * @param source source of repository
     */
    public void setSource(SvnTarget source) {
        this.source = source;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getRevision() == null) {
            setRevision(SVNRevision.UNDEFINED);
        }
        
        if (getSource() == null || !getSource().isURL() || getSource().getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (!getRevision().isValid() && getFirstTarget() != null) {
            setRevision(getSource().getResolvedPegRevision());            
        }
        if (!getRevision().isValid()) {
            setRevision(SVNRevision.HEAD);
        }
        
        if (getFirstTarget() == null || getFirstTarget().getFile() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (getRevision().getNumber() < 0 && getRevision().getDate() == null && getRevision() != SVNRevision.HEAD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    

}
