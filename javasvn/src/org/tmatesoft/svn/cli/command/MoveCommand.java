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
			runRemote(out);
		}
		else {
			runLocally(out);
		}
	}

	private void runRemote(PrintStream out) throws SVNException {
		String srcPath = getCommandLine().getURL(0);
		String destPath = getCommandLine().getURL(1);
		String message = (String)getCommandLine().getArgumentValue(SVNArgument.MESSAGE);

		String root = PathUtil.getCommonRoot(new String[]{destPath, srcPath});
		SVNRepository repository = createRepository(root);
		long revNumber = -1;
		String revStr = (String)getCommandLine().getArgumentValue(SVNArgument.REVISION);
		if (revStr != null) {
			try {
				revNumber = Long.parseLong(revStr);
			}
			catch (NumberFormatException e) {
				revNumber = -1;
			}
		}
		if (revNumber < 0) {
			revNumber = repository.getLatestRevision();
		}
		String deletePath = srcPath.substring(root.length());
		destPath = destPath.substring(root.length());
		deletePath = PathUtil.removeLeadingSlash(deletePath);
		destPath = PathUtil.removeLeadingSlash(destPath);
		destPath = PathUtil.decode(destPath);
		deletePath = PathUtil.decode(deletePath);

		ISVNEditor editor = repository.getCommitEditor(message, null);
		try {
			editor.openRoot(-1);
			DebugLog.log("adding: " + destPath + " from " + deletePath);

			editor.addDir(destPath, deletePath, revNumber);
			editor.closeDir();
			DebugLog.log("deleting: " + deletePath + " at " + revNumber);
			editor.deleteEntry(deletePath, revNumber);

			editor.closeDir();
			SVNCommitInfo info = editor.closeEdit();

			out.println();
			out.println("Committed revision " + info.getNewRevision() + ".");
		}
		catch (SVNException e) {
			if (editor != null) {
				try {
					editor.abortEdit();
				}
				catch (SVNException inner) {
				}
			}
			throw e;
		}
	}

	private void runLocally(final PrintStream out) throws SVNException {
		if (getCommandLine().getPathCount() != 2) {
			throw new SVNException("Please enter SRC and DST path");
		}

		final String absoluteSrcPath = getCommandLine().getPathAt(0);
		final String absoluteDstPath = getCommandLine().getPathAt(1);
		final ISVNWorkspace workspace = createWorkspace(absoluteSrcPath);
		final String srcPath = SVNUtil.getWorkspacePath(workspace, absoluteSrcPath);
		final String dstTempPath = SVNUtil.getWorkspacePath(workspace, absoluteDstPath);
		final SVNStatus status = workspace.status(dstTempPath, false);
		final String dstPath = status != null && status.isDirectory() ? PathUtil.append(dstTempPath, PathUtil.tail(srcPath)) : dstTempPath;

		workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
			public void modified(String path, int kind) {
				try {
					if (path.equals(srcPath)) {
						path = convertPath(absoluteSrcPath, workspace, path);
					}
					else {
						path = convertPath(absoluteDstPath, workspace, path);
					}
				}
				catch (IOException e) {
				}

				final String kindString = (kind == SVNStatus.ADDED ? "A" : "D");
				println(out, kindString + "  " + path);
			}
		});

		workspace.copy(srcPath, dstPath, true);
	}
}
