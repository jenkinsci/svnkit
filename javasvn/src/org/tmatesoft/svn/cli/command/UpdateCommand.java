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
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class UpdateCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String path = getCommandLine().getPathAt(0);
        final ISVNWorkspace workspace = createWorkspace(path, true);
        long revision = -1;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            revision = Long.parseLong(revStr);
        }
        final String homePath = path;
        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void updated(String updatedPath, int contentsStatus, int propertiesStatus, long rev) {
                try {
                    updatedPath = convertPath(homePath, workspace, updatedPath);
                } catch (IOException e) {
                    DebugLog.error(e);
                    
                }
                char contents = 'U';
                char properties = ' ';
                if (contentsStatus == SVNStatus.ADDED) {
                    contents = 'A';
                } else if (contentsStatus == SVNStatus.DELETED) {
                    contents = 'D';
                } else if (contentsStatus == SVNStatus.MERGED) {
                    contents = 'G';
                } else if (contentsStatus == SVNStatus.CONFLICTED) {
                    contents = 'C';
                } else if (contentsStatus == SVNStatus.NOT_MODIFIED) {
                    contents = ' ';
                }

                if (propertiesStatus == SVNStatus.MODIFIED) {
                    properties = 'U';
                } else if (propertiesStatus == SVNStatus.CONFLICTED) {
                    properties = 'C';
                }
                DebugLog.log(contents + "" + properties + ' ' + updatedPath);
                if (contents == ' ' && properties == ' ') {
                    return;
                }
                out.println(contents + "" + properties + ' ' + updatedPath);
            }
        });

        revision = workspace.update(SVNUtil.getWorkspacePath(workspace, path), revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        DebugLog.log("At revision " + revision + ".");
        out.println("At revision " + revision + ".");
    }
}
