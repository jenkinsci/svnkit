package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

public class SvnUnlock extends SvnReceivingOperation<SVNLock> {

    protected SvnUnlock(SvnOperationFactory factory) {
        super(factory);
    }

}
