package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNPropertyValue;

public class SvnRemoteSetProperty extends AbstractSvnCommit {

    private boolean force;
    private String propertyName;
    private SVNPropertyValue propertyValue;

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
}
