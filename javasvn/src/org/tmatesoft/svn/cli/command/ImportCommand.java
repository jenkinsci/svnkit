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
public class ImportCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        final String path = getCommandLine().getPathAt(0);
        final String url = getCommandLine().getURL(0);

        DebugLog.log("import url: " + url);
        DebugLog.log("import path: " + path);

        final ISVNWorkspace workspace = createWorkspace(path);
        final String homePath = path;
        DebugLog.log("import root: " + workspace.getID());
        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void committed(String committedPath, int kind) {
                try {
                    committedPath = convertPath(homePath, workspace, committedPath);
                } catch (IOException e) {}
                println(out, "Adding " + committedPath);
            }
        });
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        if (message == null) {
            DebugLog.log("NO MESSAGE!");
            message = "";
        }
        long revision = workspace.commit(location, message);
        out.println("Imported revision " + revision + ".");
    }

}
