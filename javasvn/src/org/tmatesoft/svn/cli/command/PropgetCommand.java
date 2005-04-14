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
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class PropgetCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final String propertyName = getCommandLine().getPathAt(0);
        DebugLog.error("property name: " + propertyName);
        final String absolutePath = getCommandLine().getPathAt(1);
        DebugLog.error("path: " + absolutePath);
        final ISVNWorkspace workspace = createWorkspace(absolutePath, false);
        final String relativePath = SVNUtil.getWorkspacePath(workspace, new File(absolutePath).getAbsolutePath());
        try {
            String value = workspace.getPropertyValue(relativePath, propertyName);
            out.println(value);
            DebugLog.log("property get: " + value);
        } catch (SVNException e) {
            DebugLog.error(e);
        }
    }
}
