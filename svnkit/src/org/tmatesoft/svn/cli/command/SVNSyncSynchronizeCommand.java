/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.command;

import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @since   1.1
 */
public class SVNSyncSynchronizeCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            String destURL = getCommandLine().getURL(0);
            if (matchTabsInURL(destURL, err)) {
                return;
            }

            SVNClientManager manager = getClientManager();
            SVNAdminClient adminClient = manager.getAdminClient();
            final PrintStream outStream = out;
            adminClient.setReplayHandler(new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    SVNCommand.println(outStream, "Committed revision " + logEntry.getRevision() + ".");
                }
            });
            adminClient.doSynchronize(SVNURL.parseURIDecoded(destURL));
        }
    }

}
