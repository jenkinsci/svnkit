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
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class AddCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            String path = getCommandLine().getPathAt(i);
            final String homePath = path;
            final ISVNWorkspace workspace = createWorkspace(path);
            try {
                workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
                    public void modified(String committedPath, int kind) {
                        try {
                            committedPath = convertPath(homePath, workspace, committedPath);
                        } catch (IOException e) {}
                        DebugLog.log("A  " + committedPath);
                        out.println("A  " + committedPath);
                    }
                });
                workspace.add(SVNUtil.getWorkspacePath(workspace, path), false, recursive);
            } catch (SVNException e) {
                err.println("error: " + e.getMessage());
            }
        }
    }
}
