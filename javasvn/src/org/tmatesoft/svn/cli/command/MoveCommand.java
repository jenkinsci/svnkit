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

import java.io.*;
import org.tmatesoft.svn.cli.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.util.*;

/**
 * @author TMate Software Ltd.
 */
public class MoveCommand extends SVNCommand {

	public void run(PrintStream out, PrintStream err) throws SVNException {
		if (getCommandLine().hasURLs()) {
//			runRemote();
		}
		else {
			runLocally(out);
		}
	}

//	private void runRemote() throws SVNException {
//		final String entryUrl = getCommandLine().getURL(0);
//		final String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
//		final String entry = PathUtil.tail(entryUrl);
//		final String url = entryUrl.substring(0, entryUrl.length() - entry.length());
//		final SVNRepository repository = createRepository(url);
//		ISVNEditor editor = repository.getCommitEditor(commitMessage != null ? commitMessage : "", null);
//		try {
//			editor.openRoot(-1);
//			editor.deleteEntry(entry, -1);
//			editor.closeDir();
//			editor.closeEdit();
//		}
//		catch (SVNException ex) {
//			editor.abortEdit();
//			throw ex;
//		}
//	}

	private void runLocally(final PrintStream out) throws SVNException {
//		if (1 == 1) throw new SVNException("");

		if (getCommandLine().getPathCount() != 2) {
			throw new SVNException("Please enter SRC and DST path");
		}

		final String absolutePath = getCommandLine().getPathAt(0);
		final ISVNWorkspace workspace = createWorkspace(absolutePath);
		workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
			public void modified(String path, int kind) {
				try {
					path = convertPath(absolutePath, workspace, path);
				}
				catch (IOException e) {
				}

				final String kindString = (kind == SVNStatus.ADDED ? "A" : "D");

				DebugLog.log(kindString + "  " + path);
				out.println(kindString + "  " + path);
			}
		});

		final String srcPath = SVNUtil.getWorkspacePath(workspace, getCommandLine().getPathAt(0));
		final String dstTempPath = SVNUtil.getWorkspacePath(workspace, getCommandLine().getPathAt(1));
		final SVNStatus status = workspace.status(dstTempPath, false);
		final String dstPath = status != null && status.isDirectory() ? PathUtil.append(dstTempPath, PathUtil.tail(srcPath)) : dstTempPath;
		workspace.copy(srcPath, dstPath, true);
	}
}
