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
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutPath = getCommandLine().getPathAt(i);
            final ISVNWorkspace workspace = createWorkspace(absolutPath, true);
            final String homePath = absolutPath;
            final boolean[] changesReceived = new boolean[] { false };
            workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
                public void updated(String updatedPath, int contentsStatus, int propertiesStatus, long rev) {
                    DebugLog.log("updated path: " + updatedPath);
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
                    } else if (contentsStatus == SVNStatus.CORRUPTED) {
                        contents = 'U';
                    }

                    if (propertiesStatus == SVNStatus.UPDATED) {
                        properties = 'U';
                    } else if (propertiesStatus == SVNStatus.CONFLICTED) {
                        properties = 'C';
                    }
                    if (contents == ' ' && properties == ' ') {
                        return;
                    }
                    changesReceived[0] = true;
                    DebugLog.log(contents + "" + properties + ' ' + updatedPath);
                    out.println(contents + "" + properties + ' ' + updatedPath);
                    if (contentsStatus == SVNStatus.CORRUPTED) {
                        err.println("svn: Checksum error: base version of file '" + updatedPath + "' is corrupted and was not updated.");
                        DebugLog.log("svn: Checksum error: base version of file '" + updatedPath + "' is corrupted and was not updated.");
                    }
                }

                public void modified(String path, int kind) {
                    try {
                        path = convertPath(homePath, workspace, path);
                    } catch (IOException e) {
                    }
                    DebugLog.log("Restored '" + path + "'");
                    out.println("Restored '" + path + "'");
                }
                
            });

	        final String path = SVNUtil.getWorkspacePath(workspace, absolutPath);
	        long revision = parseRevision(getCommandLine(), workspace, path);
	        revision = workspace.update(path, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            if (!changesReceived[0]) {
                println(out, "At revision " + revision + ".");
            } else {
                println(out, "Updated to revision " + revision + ".");
            }
        }
    }
}
