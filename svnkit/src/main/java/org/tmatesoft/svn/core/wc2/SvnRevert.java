package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

public class SvnRevert extends SvnOperation<Void> {
    
    protected SvnRevert(SvnOperationFactory factory) {
        super(factory);
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
