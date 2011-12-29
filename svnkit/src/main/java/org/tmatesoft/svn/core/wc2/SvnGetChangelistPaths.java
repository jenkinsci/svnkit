package org.tmatesoft.svn.core.wc2;

public class SvnGetChangelistPaths extends SvnReceivingOperation<String> {

	protected SvnGetChangelistPaths(SvnOperationFactory factory) {
        super(factory);
    }
    
    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

    
    
}
