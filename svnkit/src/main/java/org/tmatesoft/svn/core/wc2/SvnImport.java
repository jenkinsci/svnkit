package org.tmatesoft.svn.core.wc2;

public class SvnImport extends AbstractSvnCommit {

    private boolean applyAutoProperties;
    private boolean force;
    private boolean includeIgnored;
    
    public boolean isApplyAutoProperties() {
        return applyAutoProperties;
    }

    public void setApplyAutoProperties(boolean applyAutoProperties) {
        this.applyAutoProperties = applyAutoProperties;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isIncludeIgnored() {
        return includeIgnored;
    }

    public void setIncludeIgnored(boolean includeIgnored) {
        this.includeIgnored = includeIgnored;
    }

    protected SvnImport(SvnOperationFactory factory) {
        super(factory);
    }

}
