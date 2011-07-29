package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNURL;

public class SvnSwitch extends AbstractSvnUpdate<Long> {

    private boolean depthIsSticky;
    private boolean allowUnversionedObstructions;
    private boolean ignoreAncestry;
    
    private SVNURL switchUrl;

    protected SvnSwitch(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isDepthIsSticky() {
        return depthIsSticky;
    }

    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    public SVNURL getSwitchUrl() {
        return switchUrl;
    }

    public void setDepthIsSticky(boolean depthIsSticky) {
        this.depthIsSticky = depthIsSticky;
    }

    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    public void setSwitchUrl(SVNURL switchUrl) {
        this.switchUrl = switchUrl;
    }
}
