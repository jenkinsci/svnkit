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
import java.util.Iterator;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class ProplistCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final boolean verbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        if (recursive) {
            throw new SVNException("Recursive currently not supported!");
        }

        final String absolutePath = getCommandLine().getPathAt(0);
        final ISVNWorkspace workspace = createWorkspace(absolutePath, false);
        final String relativePath = SVNUtil.getWorkspacePath(workspace, new File(absolutePath).getAbsolutePath());

        println(out, "Properties on '" + absolutePath + "':");
        for (Iterator it = workspace.propertyNames(relativePath); it.hasNext();) {
            final String propertyName = (String) it.next();
            if (propertyName.startsWith("svn:entry:")) {
                continue;
            }

            final StringBuffer line = new StringBuffer();
            line.append("  ");
            line.append(propertyName);
            if (verbose) {
                line.append(" : ");
                line.append(workspace.getPropertyValue(relativePath, propertyName));
            }

            println(out, line.toString());
        }
    }
}
