/*
 * Created on Feb 18, 2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.SVNUtil;


class CommitWorkspaceListener extends SVNWorkspaceAdapter {
	
	private ISVNWorkspace myWorkspace;
	private Notify myNotify;

	public CommitWorkspaceListener(Notify notify, ISVNWorkspace ws) {
		myWorkspace = ws;
		myNotify = notify;
	}

    public void committed(String path, int kind) {
		if (myNotify == null) {
			return;
		}
		int updateKind = 0;
		switch (kind) {
		case SVNStatus.MODIFIED:
			updateKind = NotifyAction.commit_modified;
			break;
		case SVNStatus.ADDED:
			updateKind = NotifyAction.commit_added;
			break;
		case SVNStatus.DELETED:
			updateKind = NotifyAction.commit_deleted;
			break;
		case SVNStatus.REPLACED:
			updateKind = NotifyAction.commit_replaced;
		}
		try {
			String mimeType = myWorkspace.getPropertyValue(path, SVNProperty.MIME_TYPE);
			String nodeKindStr = myWorkspace.getPropertyValue(path, SVNProperty.KIND);
			int nodeKind = NodeKind.unknown;
			if (SVNProperty.KIND_DIR.equals(nodeKindStr)) {
				nodeKind = NodeKind.dir;
			} else if (SVNProperty.KIND_FILE.equals(nodeKindStr)) {
				nodeKind = NodeKind.file;
			}
            path = SVNUtil.getAbsolutePath(myWorkspace, path);
			myNotify.onNotify(path, updateKind, nodeKind, mimeType,
					0, 0, 0);
		} catch (SVNException e) {
		} 
	}
}