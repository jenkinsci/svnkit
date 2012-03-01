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
     * Gets whether or not externals definitions should be ignored.
     * 
     * @return <code>true</code> if externals definitions should be ignored, otherwise false
     */
    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }
    
    /**
     * Sets whether or not externals definitions should be ignored.
     * 
     * @param ignoreExternals code>true</code> if externals definitions should be ignored, otherwise false
     */
    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }

    /**
     * Gets whether or not locks should be updated on demand.
     * Only relevant for 1.6 working copies. 
     * 
     * @return <code>true</code> if locks should be updated on demand, otherwise false
     */
    public boolean isUpdateLocksOnDemand() {
        return updateLocksOnDemand;
    }

    /**
     * Sets whether or not locks should be updated on demand.
     * Only relevant for 1.6 working copies.
     * 
     * @param updateLocksOnDemand <code>true</code> if locks should be updated on demand, otherwise false
     */
    public void setUpdateLocksOnDemand(boolean updateLocksOnDemand) {
        this.updateLocksOnDemand = updateLocksOnDemand;
    }

    /**
     * Gets whether or not to allow tollerating unversioned items during update.
     * 
     * @return <code>true</code> if allow tollerating unversioned items during update, otherwise false
     */
    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    /**
     * Set whether or not to allow tollerating unversioned items during update.
     * 
     * @param allowUnversionedObstructions <code>true</code> if allow tollerating unversioned items during update, otherwise false
     */
    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    /**
     * Gets externals handler.
     * 
     * @return externals handler
     */
    public ISvnExternalsHandler getExternalsHandler() {
        return externalsHandler;
    }

    /**
     * Sets externals handler.
     * @param externalsHandler object to handle the externals
     */
    public void setExternalsHandler(ISvnExternalsHandler externalsHandler) {
        this.externalsHandler = externalsHandler;
    }

}
