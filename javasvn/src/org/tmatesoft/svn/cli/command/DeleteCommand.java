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

package org.tmatesoft.svn.cli.command;

import java.io.IOException;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class DeleteCommand extends SVNCommand {

	public void run(PrintStream out, PrintStream err) throws SVNException {
		if (getCommandLine().hasURLs()) {
			runRemote(out);
		}
		else {
			runLocally(out);
		}
	}

	private void runRemote(PrintStream out) throws SVNException {
		final String entryUrl = getCommandLine().getURL(0);
		final String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
		final String entry = PathUtil.tail(entryUrl);
		final String url = entryUrl.substring(0, entryUrl.length() - entry.length());
		final SVNRepository repository = createRepository(url);
		ISVNEditor editor = repository.getCommitEditor(commitMessage != null ? commitMessage : "", null);
		try {
			editor.openRoot(-1);
			editor.deleteEntry(entry, -1);
			editor.closeDir();
			SVNCommitInfo info = editor.closeEdit();
            
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
		}
		catch (SVNException ex) {
			editor.abortEdit();
			throw ex;
		}
	}

	private void runLocally(final PrintStream out) throws SVNException {
		for (int i = 0; i < getCommandLine().getPathCount(); i++) {
			final String absolutePath = getCommandLine().getPathAt(i);
			final String workspacePath = absolutePath;
			final ISVNWorkspace workspace = createWorkspace(absolutePath);
            boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
			workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
				public void modified(String path, int kind) {
					try {
						path = convertPath(workspacePath, workspace, path);
					}
					catch (IOException e) {
					}

					DebugLog.log("D  " + path);
					out.println("D  " + path);
				}
			});

			final String relativePath = SVNUtil.getWorkspacePath(workspace, absolutePath);
			DebugLog.log("Workspace/path is '" + workspace.getID() + "'/'" + relativePath + "'");
			workspace.delete(relativePath, force);
		}
	}
}
