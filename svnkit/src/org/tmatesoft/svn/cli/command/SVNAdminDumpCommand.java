/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNDumpHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminDumpCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "jsvnadmin: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        
        SVNRevision rStart = SVNRevision.UNDEFINED;
        SVNRevision rEnd = SVNRevision.UNDEFINED;
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        if (revStr != null && revStr.indexOf(':') > 0) {
            rStart = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
            rEnd = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
        } else if (revStr != null) {
            rStart = SVNRevision.parse(revStr);
        }

        boolean isIncremental = getCommandLine().hasArgument(SVNArgument.INCREMENTAL);
        boolean useDeltas = getCommandLine().hasArgument(SVNArgument.DELTAS);
        
        SVNAdminClient adminClient = getClientManager().getAdminClient();
        ISVNDumpHandler handler = null;
        if (!getCommandLine().hasArgument(SVNArgument.QUIET)) {
            final PrintStream output = err;
            handler = new ISVNDumpHandler() {
                public void handleDumpRevision(long rev) throws SVNException {
                    output.println("* Dumped revision " + rev + ".");
                }

                public void handleLoadRevision(long rev, long original) throws SVNException {
                }
            };
        }
        adminClient.doDump(reposRoot, out, rStart, rEnd, isIncremental, useDeltas, handler);
    }

}
