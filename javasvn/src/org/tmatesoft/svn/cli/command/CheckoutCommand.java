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
        String path = ".";
        String url = getCommandLine().getPathAt(0);
        if (url.equals(".")) {
            throw new SVNException("checkout command needs URL");
        }        
        if (getCommandLine().getPathCount() > 1) {
            path = getCommandLine().getPathAt(1);
        }

        final ISVNWorkspace workspace = createWorkspace(path);
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
        long revision = -1;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            revision = Long.parseLong(revStr);
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
                DebugLog.log("A  " + updatedPath);
                out.println("A  " + updatedPath);
            }
        });
        revision = workspace.checkout(location, revision, false, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        out.println("Checked out revision " + revision + ".");
    }

}
