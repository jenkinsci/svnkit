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

import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class LogCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote(out);
        } else {
            runLocally(out);
        }
    }

    private void runRemote(final PrintStream out) throws SVNException {
        final String url = getCommandLine().getURL(0);

        if (url.startsWith("file://")) {
            throw new SVNException("Unable to open an ra_local session to URL.");
        }

        final SVNRepository repository = createRepository(url);
        repository.log(new String[] { "" }, -1, -1, true, true, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) {
                out.println(logEntry.getMessage());
            }
        });
    }

    private void runLocally(final PrintStream out) throws SVNException {
        final String path = getCommandLine().getPathAt(0);
        // get URL
        ISVNWorkspace ws = SVNUtil.createWorkspace(path);
        String wsPath = SVNUtil.getWorkspacePath(ws, path);
        SVNRepositoryLocation l = ws.getLocation(wsPath);
        DebugLog.log("URL is " + l.toCanonicalForm());
        
        getCommandLine().setURLAt(0, l.toCanonicalForm());
        runRemote(out);
    }
}
