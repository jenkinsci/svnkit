package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnRemoteSetProperty extends AbstractSvnCommit {

    private boolean force;
    private String propertyName;
    private SVNPropertyValue propertyValue;
    private SVNRevision baseRevision;
    private ISvnObjectReceiver<SVNPropertyData> propertyReceiver;
    private SVNPropertyValue originalPropertyValue;

    protected SvnRemoteSetProperty(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
    
    public SVNPropertyValue getPropertyValue() {
        return propertyValue;
    }
    
    public void setPropertyValue(SVNPropertyValue propertyValue) {
        this.propertyValue = propertyValue;
    }

    public SVNRevision getBaseRevision() {
        return baseRevision;
    }

    public void setBaseRevision(SVNRevision baseRevision) {
        this.baseRevision = baseRevision;
    }

    public ISvnObjectReceiver<SVNPropertyData> getPropertyReceiver() {
        return propertyReceiver;
    }

    public void setPropertyReceiver(ISvnObjectReceiver<SVNPropertyData> propertyReceiver) {
        this.propertyReceiver = propertyReceiver;
    }

    public SVNPropertyValue getOriginalPropertyValue() {
        return originalPropertyValue;
    }

    public void setOriginalPropertyValue(SVNPropertyValue originalPropertyValue) {
        this.originalPropertyValue = originalPropertyValue;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getBaseRevision() == null) {
            setBaseRevision(SVNRevision.HEAD);
        }
    }
    
    
}
