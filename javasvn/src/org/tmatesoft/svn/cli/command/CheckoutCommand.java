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
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class CheckoutCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        final String path = getCommandLine().getPathAt(0);
        final String url = getCommandLine().getURL(0);

        DebugLog.log("checkout url: " + url);
        DebugLog.log("checkout path: " + path);

        final ISVNWorkspace workspace = createWorkspace(path, false);
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);

        long revision = parseRevision(getCommandLine());
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
                } catch (IOException e) {}
                println(out, "A  " + updatedPath);
            }
        });
        revision = workspace.checkout(location, revision, false, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        out.println("Checked out revision " + revision + ".");
    }
}
