/*
 * Created on Feb 18, 2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.ISVNWorkspaceListener;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author alex
 */
public class SVNClientExternalsHandler implements ISVNExternalsHandler {
	
	private Notify2 myNotify;

	public SVNClientExternalsHandler(Notify2 notify) {
		myNotify = notify;
	}

    public void handleStatus(ISVNWorkspace parent, final String path, ISVNWorkspace external, final ISVNStatusHandler statusHandler,
            boolean remote, boolean descend,
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException {
        external.status("", remote, new ISVNStatusHandler() {
            public void handleStatus(String p,SVNStatus status) {
                p = PathUtil.append(path, p);
                status.setPath(p);
                statusHandler.handleStatus(p, status);
            }
        }, descend, includeUnmodified, includeIgnored); 
    }

    public void handleCheckout(ISVNWorkspace parent, String path, ISVNWorkspace external, SVNRepositoryLocation location, long revision, boolean export, boolean recurse) throws SVNException {
        String absolutePath = SVNUtil.getAbsolutePath(parent, path);
    	if (myNotify != null) {
    		//myNotify.onNotify(absolutePath, NotifyAction.update_external, NodeKind.dir, null, 0, 0, 0);
    	}
    	long rev = 0;
    	ISVNWorkspaceListener listener = new UpdateWorkspaceListener(myNotify, external, path);
    	external.addWorkspaceListener(listener);
    	try {
    		rev = external.checkout(location, revision, export);
    	} finally {
        	external.removeWorkspaceListener(listener);
        	if (myNotify != null) {
        		//myNotify.onNotify(absolutePath, NotifyAction.update_completed, NodeKind.dir, null, 0, 0, rev);
        	}
    	}
    }

    public void handleUpdate(ISVNWorkspace parent, String path, ISVNWorkspace external, long revision) throws SVNException {
        String absolutePath = SVNUtil.getAbsolutePath(parent, path);
        if (myNotify != null) {
            //myNotify.onNotify(absolutePath, NotifyAction.update_external, NodeKind.dir, null, 0, 0, 0);
        }
    	long rev = 0;
    	ISVNWorkspaceListener listener = new UpdateWorkspaceListener(myNotify, external, path);
    	external.addWorkspaceListener(listener);
    	try {
    		rev = external.update("", revision, true);
    	} finally {
        	external.removeWorkspaceListener(listener);
        	if (myNotify != null) {
        		//myNotify.onNotify(absolutePath, NotifyAction.update_completed, NodeKind.dir, null, 0, 0, rev);
        	}
    	}
    }

}
