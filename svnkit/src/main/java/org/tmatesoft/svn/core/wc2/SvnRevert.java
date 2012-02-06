package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

public class SvnRevert extends SvnOperation<Void> {

    private boolean revertMissingDirectories;

    protected SvnRevert(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isRevertMissingDirectories() {
        return revertMissingDirectories;
    }

    public void setRevertMissingDirectories(boolean revertMissingDirectories) {
        this.revertMissingDirectories = revertMissingDirectories;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.EMPTY);
        }
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
    
    

}
