package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc2.hooks.ISvnExternalsHandler;

public abstract class AbstractSvnUpdate<V> extends SvnOperation<V> {
    
    private boolean ignoreExternals;
    private boolean updateLocksOnDemand;
    private boolean allowUnversionedObstructions;
    private ISvnExternalsHandler externalsHandler;

    protected AbstractSvnUpdate(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }

    public boolean isUpdateLocksOnDemand() {
        return updateLocksOnDemand;
    }

    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }

    /**
     * Only relevant for 1.6 working copies. 
     */
    public void setUpdateLocksOnDemand(boolean updateLocksOnDemand) {
        this.updateLocksOnDemand = updateLocksOnDemand;
    }

    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    public ISvnExternalsHandler getExternalsHandler() {
        return externalsHandler;
    }

    public void setExternalsHandler(ISvnExternalsHandler externalsHandler) {
        this.externalsHandler = externalsHandler;
    }

}
