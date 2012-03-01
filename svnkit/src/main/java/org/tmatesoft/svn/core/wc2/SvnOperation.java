package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * Base class for all SVN operations.
 * @author TMate Software Ltd.
 */
public class SvnOperation<V> {
    
    private SVNDepth depth;
    private Collection<SvnTarget> targets;
    private SVNRevision revision;
    private Collection<String> changelists;
    private SvnOperationFactory operationFactory;
    private boolean isSleepForTimestamp;
    
    private volatile boolean isCancelled;
    
    protected SvnOperation(SvnOperationFactory factory) {
        this.operationFactory = factory;
        initDefaults();
    }

    /**
     * Gets the event handler for the operation. This event handler will be
     * dispatched {@link SVNEvent} objects to provide detailed information about
     * actions and progress state of version control operations performed by
     * <b>run()</b> method of <b>SVN*</b> operation classes.
     * 
     * @returns event handler
     */
    public ISVNEventHandler getEventHandler() {
        return getOperationFactory().getEventHandler();
    }

    /**
     * Gets operation options.
     * 
     * @return operation options
     */
    public ISVNOptions getOptions() {
        return getOperationFactory().getOptions();
    }
    
    protected void initDefaults() {
        setDepth(SVNDepth.UNKNOWN);
        setSleepForTimestamp(true);
        setRevision(SVNRevision.UNDEFINED);
        this.targets = new ArrayList<SvnTarget>();
    }
    
    /**
     * Sets one target to the operation.
     * 
     * @param target target to the operation
     */
    public void setSingleTarget(SvnTarget target) {
        this.targets = new ArrayList<SvnTarget>();
        if (target != null) {
            this.targets.add(target);
        }
    }

    /**
     * Sets two targets to the operation.
     * 
     * @param target1 first target to the operation
     * @param target2 second target to the operation
     */
    protected void setTwoTargets(SvnTarget target1, SvnTarget target2) {
        this.targets = new ArrayList<SvnTarget>();
        this.addTarget(target1);
        this.addTarget(target2);
    }

    /**
     * Adds the target to the operation.
     * 
     * @param target target to the operation
     */
    public void addTarget(SvnTarget target) {
        this.targets.add(target);
    }
    
    /**
     * Returns all targets of the operation.
     * 
     * @return targets of the operation
     */
    public Collection<SvnTarget> getTargets() {
        return Collections.unmodifiableCollection(targets);
    }
    
    /**
     * Returns first target of the operation.
     * 
     * @return first target of the operation
     */
    public SvnTarget getFirstTarget() {
        return targets != null && !targets.isEmpty() ? targets.iterator().next() : null;
    }
    
    /**
     * Sets the limit of the operation by depth.
     * 
     * @param depth the operation's limit by depth
     */
    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }
    
    /**
     * Gets the limit of the operation by depth.
     * 
     * @return limit of the operation by depth
     */
    public SVNDepth getDepth() {
        return depth;
    }
    
    /**
     * Sets revision to operate on.
     * 
     * @param revision revision to operate on
     */
    public void setRevision(SVNRevision revision) {
        this.revision = revision;
    }

    /**
     * Gets revision to operate on.
     * 
     * @return revision to operate on
     */
    public SVNRevision getRevision() {
        return revision;
    }
    
    /**
     * Sets changelists to operate only on members of.
     * 
     * @param changelists changelists to operate only on members of
     */
    public void setApplicalbeChangelists(Collection<String> changelists) {
        this.changelists = changelists;
    }
    
    /**
     * Gets changelists to operate only on members of.
     * 
     * @return changelists to operate only on members of
     */
    public Collection<String> getApplicableChangelists() {
        if (this.changelists == null || this.changelists.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableCollection(this.changelists);
    }
    
    /**
     * Gets the factory for creating the operations.
     * 
     * @return factory for creating the operations
     */
    public SvnOperationFactory getOperationFactory() {
        return this.operationFactory;
    }
    
    /**
     * Gets whether or not the operation has local targets.
     * 
     * @return <code>true</code> if the operation has local targets, otherwise false
     */
    public boolean hasLocalTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isLocal()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets whether or not the operation has remote targets.
     * 
     * @return <code>true</code> if the operation has remote targets, otherwise false
     */
    public boolean hasRemoteTargets() {
        for (SvnTarget target : getTargets()) {
            if (!target.isLocal()) {
                return true;
            }
        }
        return false;
    }
    
    protected void ensureEnoughTargets() throws SVNException {
        int targetsCount = getTargets().size();
        
        if (targetsCount < getMinimumTargetsCount()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET,
                    "Wrong number of targets has been specified ({0}), at least {1} is required.",
                    new Object[] {new Integer(targetsCount), new Integer(getMinimumTargetsCount())},
                    SVNErrorMessage.TYPE_ERROR);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (targetsCount > getMaximumTargetsCount()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET,
                    "Wrong number of targets has been specified ({0}), no more that {1} may be specified.",
                    new Object[] {new Integer(targetsCount),
                    new Integer(getMaximumTargetsCount())},
                    SVNErrorMessage.TYPE_ERROR);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    protected int getMinimumTargetsCount() {
        return 1;
    }

    protected int getMaximumTargetsCount() {
        return 1;
    }
    
    /**
     * Cancels the operation. Execution of operation will be stopped at the next point of checking <code>isCancelled</code> state
     * 
     * <p>
     * If {@link #getCanceller()} is not <code>null</code>, {@link ISVNCanceller#checkCancelled()} is called, 
     * otherwise {@link SVNCancelException} is raised.
     */
    public void cancel() {
        isCancelled = true;
    }
    
    /**
     * Gets whether or not the operation is cancelled.
     * 
     * @return <code>true</code> if the operation is cancelled, otherwise false
     */
    public boolean isCancelled() {
        return isCancelled;
    }
    
    /**
     * Runs the operation.
     * 
     * @return result depending on operation
     * @throws SVNException
     */
    @SuppressWarnings("unchecked")
    public V run() throws SVNException {
        ensureArgumentsAreValid();
        return (V) getOperationFactory().run(this);
    }
    
    protected void ensureArgumentsAreValid() throws SVNException {
        ensureEnoughTargets();
        ensureHomohenousTargets();
    }
    
    protected boolean needsHomohenousTargets() {
        return true;
    }
    
    protected void ensureHomohenousTargets() throws SVNException {
        if (getTargets().size() <= 1) {
            return;
        }
        if (!needsHomohenousTargets()) {
            return;
        }
        if (hasLocalTargets() && hasRemoteTargets()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot mix repository and working copy targets"), SVNLogType.WC);
        }
    }

    /**
     * Gets the pool of repositories.
     * 
     * @return pool of repositories
     */
    public ISVNRepositoryPool getRepositoryPool() {
        return getOperationFactory().getRepositoryPool();
    }

    /**
     * Gets the authentication manager of the operation.
     * 
     * @return authentication manager of the operation
     */
    public ISVNAuthenticationManager getAuthenticationManager() {
        return getOperationFactory().getAuthenticationManager();
    }

    /**
     * Gets the canceller handler of the operation. See {@link #cancel()}.
     * 
     * @return authentication manager of the operation
     */
    public ISVNCanceller getCanceller() {
        return getOperationFactory().getCanceller();
    }

    /**
     * Gets time for what thread should sleep after update operation fails.
     * 
     * @return sleep time (in milliseconds) or <code>0</code> if thread should not sleep
     */
    public boolean isSleepForTimestamp() {
        return isSleepForTimestamp;
    }

    /**
     * Sets time for what program should sleep after update operation fails.
     * 
     * @param isSleepForTimestamp sleep time (in milliseconds) or <code>0</code> if thread should not sleep
     */
    public void setSleepForTimestamp(boolean isSleepForTimestamp) {
        this.isSleepForTimestamp = isSleepForTimestamp;
    }

    /**
     * Gets whether or not operation has files as targets.
     * 
     * @return <code>true</code> if operation has files as targets
     */
    public boolean hasFileTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether or not to use parent working copy format.
     * 
     * @return <code>true</code> if parent working copy format should be used, otherwise false
     */
    public boolean isUseParentWcFormat() {
        return false;
    }
    
    protected File getOperationalWorkingCopy() {
        if (hasFileTargets()) {
            return getFirstTarget().getFile();
        }
        return null;
    }
}
