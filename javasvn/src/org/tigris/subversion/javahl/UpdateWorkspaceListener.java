/*
 * Created on Feb 18, 2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;


class UpdateWorkspaceListener extends SVNWorkspaceAdapter {
	
	private ISVNWorkspace myWorkspace;
	private Notify myNotify;
	private String myRootPath;

	public UpdateWorkspaceListener(Notify notify, ISVNWorkspace ws) {
		this(notify, ws, null);
	}
	public UpdateWorkspaceListener(Notify notify, ISVNWorkspace ws, String rootPath) {
		myWorkspace = ws;
		myNotify = notify;
		myRootPath = rootPath;
	}

	public void updated(String p, int contentsStatus, int propertiesStatus, long updated) {
		if (myNotify == null) {
			return;
		}
		int updateKind = 0;
		int contents = 0;
		switch (contentsStatus) {
		case SVNStatus.ADDED:
			updateKind = NotifyAction.update_add;
			break;
		case SVNStatus.MERGED:
			contents = contents != 0 ? contents : NotifyStatus.merged;
		case SVNStatus.CONFLICTED:
			contents = contents != 0 ? contents : NotifyStatus.merged;
		case SVNStatus.REPLACED:
		case SVNStatus.UPDATED:
			updateKind = NotifyAction.update_update;
			contents = contents != 0 ? contents : NotifyStatus.changed;
			contents = contents != 0 ? contents : NotifyStatus.changed;
			break;
		case SVNStatus.DELETED:
			updateKind = NotifyAction.update_delete;
		}
		int props = NotifyStatus.unchanged;
		switch (propertiesStatus) {
		case SVNStatus.UPDATED:
			props = NotifyStatus.changed;
			break;
		case SVNStatus.CONFLICTED:
			props = NotifyStatus.conflicted;
			break;
		case SVNStatus.MERGED:
			props = NotifyStatus.merged;
		}
		contents = contents == 0 ? NotifyStatus.unchanged : contents;
		try {
			String mimeType = myWorkspace.getPropertyValue(p, SVNProperty.MIME_TYPE);
			String nodeKindStr = myWorkspace.getPropertyValue(p, SVNProperty.KIND);
			int nodeKind = NodeKind.unknown;
			if (SVNProperty.KIND_DIR.equals(nodeKindStr)) {
				nodeKind = NodeKind.dir;
			} else if (SVNProperty.KIND_FILE.equals(nodeKindStr)) {
				nodeKind = NodeKind.file;
			}
			if (myRootPath != null) {
				p = PathUtil.append(myRootPath, p);
			}
			myNotify.onNotify(p, updateKind, nodeKind, mimeType,
					contents, props, updated);
		} catch (SVNException e) {
		} 
	}
}