/*
 * Created on Feb 18, 2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;


class LocalWorkspaceListener extends SVNWorkspaceAdapter {
	
	private ISVNWorkspace myWorkspace;
	private Notify myNotify;

	public LocalWorkspaceListener(Notify notify, ISVNWorkspace ws) {
		myWorkspace = ws;
		myNotify = notify;
	}

	public void modified(String p, int kind) {
		if (myNotify == null) {
			return;
		}
		int updateKind = 0;
		switch (kind) {
		case SVNStatus.REVERTED:
			updateKind = NotifyAction.revert;
			break;
		case SVNStatus.NOT_REVERTED:
			updateKind = NotifyAction.failed_revert;
			break;
		case SVNStatus.ADDED:
			updateKind = NotifyAction.add;
			break;
		case SVNStatus.DELETED:
			updateKind = NotifyAction.delete;
			break;
		case SVNStatus.RESOLVED:
			updateKind = NotifyAction.resolved;
			break;
		case SVNStatus.RESTORED:
			updateKind = NotifyAction.restore;
		}
		try {
			String mimeType = myWorkspace.getPropertyValue(p, SVNProperty.MIME_TYPE);
			String nodeKindStr = myWorkspace.getPropertyValue(p, SVNProperty.KIND);
			int nodeKind = NodeKind.unknown;
			if (SVNProperty.KIND_DIR.equals(nodeKindStr)) {
				nodeKind = NodeKind.dir;
			} else if (SVNProperty.KIND_FILE.equals(nodeKindStr)) {
				nodeKind = NodeKind.file;
			}
			myNotify.onNotify(p, updateKind, nodeKind, mimeType,
					0, 0, 0);
		} catch (SVNException e) {
		} 
	}
}