package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.ISVNAddParameters;

public class SvnScheduleForAddition extends SvnOperation<Void> {
    
    private boolean force;
    private boolean includeIgnored;
    private boolean applyAutoProperties;
    private boolean addParents;
    private boolean mkDir;
    private ISVNAddParameters addParameters;

    protected SvnScheduleForAddition(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected void initDefaults() {
        setApplyAutoProperties(true);
        super.initDefaults();
    }

    public boolean isForce() {
        return force;
    }

    public boolean isIncludeIgnored() {
        return includeIgnored;
    }

    public boolean isApplyAutoProperties() {
        return applyAutoProperties;
    }

    public boolean isAddParents() {
        return addParents;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public void setIncludeIgnored(boolean includeIgnored) {
        this.includeIgnored = includeIgnored;
    }

    public void setApplyAutoProperties(boolean applyAutoProperties) {
        this.applyAutoProperties = applyAutoProperties;
    }

    public void setAddParents(boolean addParents) {
        this.addParents = addParents;
    }

    public boolean isMkDir() {
        return mkDir;
    }

    public void setMkDir(boolean mkDir) {
        this.mkDir = mkDir;
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isUseParentWcFormat() {
        return true;
    }

    public ISVNAddParameters getAddParameters() {
        return addParameters;
    }

    public void setAddParameters(ISVNAddParameters addParameters) {
        this.addParameters = addParameters;
    }
}
