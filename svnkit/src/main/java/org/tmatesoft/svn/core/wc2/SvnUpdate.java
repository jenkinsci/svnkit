package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnUpdate extends AbstractSvnUpdate<long[]> {
    
    private boolean depthIsSticky;
    private boolean makeParents;
    private boolean allowUnversionedObstructions;
    private boolean treatAddsAsModifications;

    protected SvnUpdate(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        for (SvnTarget target : getTargets()) {
            if (target.isURL()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is not a local path", target.getURL());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (getDepth() == null) {
            setDepth(SVNDepth.UNKNOWN);
        }
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepthIsSticky(false);
        }
        super.ensureArgumentsAreValid();
    }

    public boolean isDepthIsSticky() {
        return depthIsSticky;
    }

    public boolean isMakeParents() {
        return makeParents;
    }

    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    public boolean isTreatAddsAsModifications() {
        return treatAddsAsModifications;
    }

    public void setDepthIsSticky(boolean depthIsSticky) {
        this.depthIsSticky = depthIsSticky;
    }

    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    public void setTreatAddsAsModifications(boolean treatAddsAsModifications) {
        this.treatAddsAsModifications = treatAddsAsModifications;
    }
}
