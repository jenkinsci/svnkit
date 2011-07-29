package org.tmatesoft.svn.core.wc2;

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
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnOperation<V> {
    
    private SVNDepth depth;
    private Collection<SvnTarget> targets;
    private SVNRevision revision;
    private SVNRevision pegRevision;
    private Collection<String> changelists;
    private SvnOperationFactory operationFactory;
    
    protected SvnOperation(SvnOperationFactory factory) {
        this.operationFactory = factory;
    }

    public ISVNEventHandler getEventHandler() {
        return getOperationFactory().getEventHandler();
    }

    public ISVNOptions getOptions() {
        return getOperationFactory().getOptions();
    }
    
    public void initDefaults() {
        this.depth = SVNDepth.UNKNOWN;        
        this.targets = new ArrayList<SvnTarget>();
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public void setSingleTarget(SvnTarget target) {
        this.targets = new ArrayList<SvnTarget>();
        if (target != null) {
            this.targets.add(target);
        }
    }
    
    public void addTarget(SvnTarget target) {
        this.targets.add(target);
    }
    
    public Collection<SvnTarget> getTargets() {
        return Collections.unmodifiableCollection(targets);
    }
    
    public SvnTarget getFirstTarget() {
        return targets != null && !targets.isEmpty() ? targets.iterator().next() : null;
    }
    
    public SVNDepth getDepth() {
        return depth;
    }
    
    public void setRevision(SVNRevision revision) {
        this.revision = revision;
    }
    
    public void setPegRevision(SVNRevision revision) {
        this.pegRevision = revision;
    }
    
    public SVNRevision getPegRevision() {
        return pegRevision;
    }
    
    public SVNRevision getRevision() {
        return revision;
    }
    
    public void setApplicalbeChangelists(Collection<String> changelists) {
        this.changelists = changelists;
    }
    
    public Collection<String> getApplicableChangelists() {
        if (this.changelists == null || this.changelists.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableCollection(this.changelists);
    }
    
    public SvnOperationFactory getOperationFactory() {
        return this.operationFactory;
    }
    
    public boolean hasLocalTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isFile()) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasRemoteTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isURL()) {
                return true;
            }
        }
        return false;
    }
    
    @SuppressWarnings("unchecked")
    public V run() throws SVNException {
        ensureArgumentsAreValid();
        return (V) getOperationFactory().run(this);
    }
    
    protected void ensureArgumentsAreValid() throws SVNException {
        ensureHomohenousTargets();
    }
    
    protected boolean needsHomohenousTargets() {
        return false;
    }
    
    protected void ensureHomohenousTargets() throws SVNException {
        if (!needsHomohenousTargets()) {
            return;
        }
        if (hasLocalTargets() && hasRemoteTargets()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot mix repository and working copy targets"), SVNLogType.WC);
        }
    }

    public ISVNRepositoryPool getRepositoryPool() {
        return getOperationFactory().getRepositoryPool();
    }

    public ISVNAuthenticationManager getAuthenticationManager() {
        return getOperationFactory().getAuthenticationManager();
    }

    public ISVNCanceller getCanceller() {
        return getOperationFactory().getCanceller();
    }
}
