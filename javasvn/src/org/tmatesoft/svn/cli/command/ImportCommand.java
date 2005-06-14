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
import java.io.File;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class ImportCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        String path = ".";
        if (getCommandLine().getPathCount() >= 1) {
            path = getCommandLine().getPathAt(0);
        }
        String url = getCommandLine().getURL(0);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        SVNCommitClient commitClient = new SVNCommitClient(getCredentialsProvider(), getOptions(), new SVNCommandEventProcessor(out, err, false));
        long revision = commitClient.doImport(new File(path), url, message, recursive);
        if (revision >= 0) {
            out.println();
            out.println("Imported revision " + revision + ".");
        }
    }

}
