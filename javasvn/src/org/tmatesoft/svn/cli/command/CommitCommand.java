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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CommitCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        List pathsList = new ArrayList();
        String[] paths;
        try {
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                String path = getCommandLine().getPathAt(i);
                pathsList.add(new File(path).getCanonicalPath().replace(File.separatorChar, '/'));
            }
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return;
        }
        DebugLog.log("commit paths: " + pathsList);
        paths = (String[]) pathsList.toArray(new String[pathsList.size()]);
        // only if path is not a single directory!
        String rootPath;
        final String homePath = getCommandLine().getPathAt(0);
        if (getCommandLine().getPathCount() == 1 && new File(getCommandLine().getPathAt(0)).isDirectory()) {
            rootPath = (String) pathsList.get(0);
        } else {
            rootPath = PathUtil.getCommonRoot(paths);
            // check if original paths should start with '/';
            if (rootPath.indexOf(":") != 1) {
                rootPath = "/" + rootPath;
            }
        }
        DebugLog.log("commit root: " + rootPath);

        final ISVNWorkspace workspace = createWorkspace(rootPath);
        for(int i = 0; i < paths.length; i++) {
            paths[i] = SVNUtil.getWorkspacePath(workspace, paths[i]);
        }
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void committed(String committedPath, int kind) {
                DebugLog.log("commit path: " + committedPath);
                DebugLog.log("home path: " + homePath);
                try {
                    committedPath = convertPath(homePath, workspace, committedPath);
                } catch (IOException e) {}
                String verb = "Sending ";
                if (kind == SVNStatus.ADDED) {
                    verb = "Adding ";
                }
                DebugLog.log(verb + committedPath);
                out.println(verb + committedPath);
            }
        });
        long revision = workspace.commit(paths, message, recursive);
        if (revision >= 0) {
            out.println("Committed revision " + revision + ".");
        }
    }
}
