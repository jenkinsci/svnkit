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
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author TMate Software Ltd.
 */
public class LogCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote();
        } else {
            runLocally();
        }
    }

    private void runRemote() throws SVNException {
        final String url = getCommandLine().getURL(0);

        if (url.startsWith("file://")) {
            throw new SVNException("Unable to open an ra_local session to URL.");
        }

        final SVNRepository repository = createRepository(url);
        repository.log(new String[] { "." }, -1, -1, true, true, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) {
            }
        });
    }

    private void runLocally() throws SVNException {
        throw new SVNException("Local log not supported!");
    }
}
