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
public class PropsetCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final String propertyName = getCommandLine().getPathAt(0);
        final String propertyValue = getCommandLine().getPathAt(1);
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);

        for (int i = 2; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            final ISVNWorkspace workspace = createWorkspace(absolutePath, false);
            workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
                public void modified(String path, int kind) {
                    try {
                        path = convertPath(absolutePath, workspace, path);
                    } catch (IOException e) {}

                    println(out, "property '" + propertyName + "' set on '" + path + "'");
                }
            });

            final String relativePath = SVNUtil.getWorkspacePath(workspace, new File(absolutePath).getAbsolutePath());
            try {
                workspace.setPropertyValue(relativePath, propertyName, propertyValue, recursive);
                DebugLog.log("property set");
            } catch (SVNException e) {
                DebugLog.error(e);
            }
        }
    }
}
