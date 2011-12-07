package org.tmatesoft.svn.core.wc2;

public class SvnRemoteDelete extends AbstractSvnCommit {

    protected SvnRemoteDelete(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
    
    

}
