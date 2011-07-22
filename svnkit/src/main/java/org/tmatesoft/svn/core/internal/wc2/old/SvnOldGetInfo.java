package org.tmatesoft.svn.core.internal.wc2.old;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnOldGetInfo extends SvnLocalOperationRunner<SvnGetInfo> implements ISVNInfoHandler {

    @Override
    public void run(SvnGetInfo operation) throws SVNException {
        super.run(operation);
        
        // TODO get it from the of?
        SVNClientManager manager = SVNClientManager.newInstance();
        
        SVNWCClient16 client = new SVNWCClient16(manager, null);
        client.doInfo(getFirstTarget(), getOperation().getPegRevision(), getOperation().getRevision(), getOperation().getDepth(), getOperation().getApplicableChangelists(), this);
    }

    public void handleInfo(SVNInfo info) throws SVNException {
        SvnInfo info2 = convert(info);
        getOperation().getReceiver().receive(SvnTarget.fromFile(info.getFile()), info2);
    }

    private SvnInfo convert(SVNInfo info) {
        return null;
    }

}
