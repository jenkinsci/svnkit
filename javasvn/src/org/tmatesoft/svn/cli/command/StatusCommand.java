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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNCommandStatusHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.util.DebugLog;

import java.io.File;
import java.io.PrintStream;

/**
 * @author TMate Software Ltd.
 */
public class StatusCommand extends SVNCommand {
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNCommandLine line = getCommandLine();
        for(int i = 0; i < line.getPathCount(); i++) {
            String path = line.getPathAt(i);
            if (path.trim().equals("..")) {
                File dir = new File(path, ".svn");
                if (!dir.exists() || !dir.isDirectory()) {
                    err.println("svn: '..' is not a working copy");
                    return;
                }
            }
        }

        boolean showUpdates = getCommandLine().hasArgument(SVNArgument.SHOW_UPDATES);
        if (getCommandLine().getPathCount() == 0) {
            getCommandLine().setPathAt(0, ".");
        }
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean reportAll = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        boolean ignored = getCommandLine().hasArgument(SVNArgument.NO_IGNORE);
        boolean quiet = getCommandLine().hasArgument(SVNArgument.QUIET);

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNStatusClient stClient = getClientManager().getStatusClient();
        org.tmatesoft.svn.core.wc.ISVNStatusHandler handler = new SVNCommandStatusHandler(System.out, reportAll || showUpdates, reportAll, quiet, showUpdates);
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
              String path = getCommandLine().getPathAt(i);
              File file = new File(path).getAbsoluteFile();
              DebugLog.log("calling status on: " + file);
              try {
                stClient.doStatus(file, recursive, showUpdates, reportAll, ignored, handler);
              } catch (SVNException e) {
                  DebugLog.error(e);
                  err.println(e.getMessage());
                  error = true;
              }
        }
        if (error) {
            System.exit(1);
        }
    }
}
