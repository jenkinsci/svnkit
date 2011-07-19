package org.tmatesoft.svn.core.wc2;

import java.util.Collection;
import java.util.Collections;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnOperation {
    
    private SVNDepth depth;
    private SvnTarget target;
    private SVNRevision revision;
    private SVNRevision pegRevision;
    private Collection<String> changelists;
    private SvnOperationFactory operationFactory;
    
    protected SvnOperation(SvnOperationFactory factory) {
        this.operationFactory = factory;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public void setTarget(SvnTarget target) {
        this.target = target;
    }
    
    public SvnTarget getTarget() {
        return target;
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
        if (this.changelists == null) {
            return null;
        }
        return Collections.unmodifiableCollection(this.changelists);
    }
    
    public SvnOperationFactory getOperationFactory() {
        return this.operationFactory;
    }
    
    public void run() throws SVNException {
        ISvnOperationRunner implementation = getOperationFactory().getImplementation(this);
        if (implementation != null) {
            implementation.run(this);
        }
    }
}
