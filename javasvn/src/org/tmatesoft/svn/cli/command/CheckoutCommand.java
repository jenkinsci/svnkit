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
public class CheckoutCommand extends SVNCommand {

	public void run(final PrintStream out, final PrintStream err) throws SVNException {
		final String url = getCommandLine().getURL(0);
		final SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);

		final String path;
		if (getCommandLine().getPathCount() == 1) {
			path = getCommandLine().getPathAt(0);
		}
		else {
			final SVNRepository repository = createRepository(url);
			repository.testConnection();
			final String root = repository.getRepositoryRoot();
			final String locationPath = location.getPath();
			SVNAssert.assertTrue(locationPath.startsWith(root));
			path = locationPath.substring(root.length());
		}

		DebugLog.log("checkout url: " + url);
		DebugLog.log("checkout path: " + path);

		final ISVNWorkspace workspace = createWorkspace(path, false);

		long revision = parseRevision(getCommandLine(), null, null);
		if (SVNRepositoryLocation.equals(workspace.getLocation(), location)) {
			workspace.update(revision);
			return;
		}

		final String homePath = path;
		workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
			public void updated(String updatedPath, int contentsStatus, int propertiesStatus, long rev) {
				if ("".equals(updatedPath)) {
					return;
				}
				try {
					updatedPath = convertPath(homePath, workspace, updatedPath);
				}
				catch (IOException e) {
				}
				println(out, "A  " + updatedPath);
			}
		});
		revision = workspace.checkout(location, revision, false, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
		out.println("Checked out revision " + revision + ".");
	}
}
