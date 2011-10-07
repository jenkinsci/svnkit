package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLock;

public class SvnSetLock extends SvnReceivingOperation<SVNLock> {

    protected SvnSetLock(SvnOperationFactory factory) {
        super(factory);
    }

}
