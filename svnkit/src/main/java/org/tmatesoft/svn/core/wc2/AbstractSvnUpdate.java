package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc2.hooks.ISvnExternalsHandler;

/**
 * This class encapsulates methods for checkout, export, switch and update operations.
 * @author TMate Software Ltd.
 */
public abstract class AbstractSvnUpdate<V> extends SvnOperation<V> {
    
    private boolean ignoreExternals;
    private boolean updateLocksOnDemand;
    private boolean allowUnversionedObstructions;
    private ISvnExternalsHandler externalsHandler;

    protected AbstractSvnUpdate(SvnOperationFactory factory) {
        super(factory);
    }

    /**
     * Get flag that controls whether ignore externals definitions.
     */
    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }

    /**
     * Get flag that controls whether update locks on demand.
     */
    public boolean isUpdateLocksOnDemand() {
        return updateLocksOnDemand;
    }

    /**
     * Set flag that controls whether ignore externals definitions.
     * @param ignoreExternals
     */
    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }

    /**
     * Only relevant for 1.6 working copies. 
     * Set flag that controls whether update locks on demand.
     * @param updateLocksOnDemand
     */
    public void setUpdateLocksOnDemand(boolean updateLocksOnDemand) {
        this.updateLocksOnDemand = updateLocksOnDemand;
    }

    /**
     * Get flag that allows tollerating unversioned items during update.
     */
    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    /**
     * Set flag that allows tollerating unversioned items during update.
     * @param allowUnversionedObstructions
     */
    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    /**
     * Get externals handler.
     */
    public ISvnExternalsHandler getExternalsHandler() {
        return externalsHandler;
    }

    /**
     * Get externals handler.
     * @param externalsHandler
     */
    public void setExternalsHandler(ISvnExternalsHandler externalsHandler) {
        this.externalsHandler = externalsHandler;
    }

}
