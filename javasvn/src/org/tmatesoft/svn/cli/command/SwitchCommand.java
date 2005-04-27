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
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class SwitchCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String url = getCommandLine().getURL(0);
        String absolutePath = getCommandLine().getPathAt(0);

        final ISVNWorkspace workspace = createWorkspace(absolutePath, true);
        final String homePath = absolutePath;
        final String path = SVNUtil.getWorkspacePath(workspace, absolutePath);
        final boolean[] changesReceived = new boolean[] { false };
        
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        long revision = -1;
        if (revStr != null) {
            try {
                revision = Long.parseLong(revStr);
            } catch (NumberFormatException nfe) {}
        }
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
                } else if (contentsStatus == SVNStatus.CORRUPTED) {
                    contents = 'U';
                }

                if (propertiesStatus == SVNStatus.UPDATED) {
                    properties = 'U';
                } else if (propertiesStatus == SVNStatus.CONFLICTED) {
                    properties = 'C';
                }
                DebugLog.log(contents + "" + properties + ' ' + updatedPath);
                if (contents == ' ' && properties == ' ') {
                    return;
                }
                changesReceived[0] = true;
                out.println(contents + "" + properties + "  " + updatedPath);
                if (contentsStatus == SVNStatus.CORRUPTED) {
                    err.println("svn: Checksum error: base version of file '" + updatedPath + "' is corrupted and was not updated.");
                    DebugLog.log("svn: Checksum error: base version of file '" + updatedPath + "' is corrupted and was not updated.");
                }
            }
        });

        workspace.update(SVNRepositoryLocation.parseURL(url), path, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        if (!changesReceived[0]) {
            println(out, "At revision " + revision + ".");
        } else {
            println(out, "Updated to revision " + revision + ".");
        }
    }
}
