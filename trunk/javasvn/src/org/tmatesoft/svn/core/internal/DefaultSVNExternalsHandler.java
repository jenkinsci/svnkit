/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal;

import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class DefaultSVNExternalsHandler implements ISVNExternalsHandler {

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
		SVNWorkspaceAdapter listener = new UpdateListener(path, (SVNWorkspace) parent);
		external.addWorkspaceListener(listener);
        external.checkout(location, revision, export);
		external.removeWorkspaceListener(listener);
    }

    public void handleUpdate(final ISVNWorkspace parent, final String path, ISVNWorkspace external, long revision) throws SVNException {
		SVNWorkspaceAdapter listener = new UpdateListener(path, (SVNWorkspace) parent);
		external.addWorkspaceListener(listener);
        external.update("", revision, true);
		external.removeWorkspaceListener(listener);
    }

    private static final class UpdateListener extends SVNWorkspaceAdapter {

		private final String myPath;
		private final SVNWorkspace myParentWorkspace;

		private UpdateListener(String path, SVNWorkspace parent) {
			myPath = path;
			myParentWorkspace = parent;
		}

		public void updated(String externalPath, int contentsStatus, int propertiesStatus, long rev) {
			externalPath = PathUtil.append(myPath, externalPath);
			try {
				ISVNEntry entry = myParentWorkspace.locateEntry(externalPath);
				myParentWorkspace.fireEntryUpdated(entry, contentsStatus, propertiesStatus, rev);
			} catch (SVNException e) {
			} 
		}
	}
}