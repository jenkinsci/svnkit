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
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookInfoCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            SVNLogEntry entry = lookClient.doGetInfo(reposRoot, transactionName);
            printInfo(entry, out);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 

        SVNLogEntry entry = lookClient.doGetInfo(reposRoot, revision);
        printInfo(entry, out);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    private void printInfo(SVNLogEntry entry, PrintStream out) {
        String author = entry.getAuthor() != null ? entry.getAuthor() : "";
        String date = entry.getDate() != null ? SVNLookDateCommand.formatDate(entry.getDate()) : ""; 
        String log = entry.getMessage() != null ? entry.getMessage() : ""; 
        SVNCommand.println(out, author);
        SVNCommand.println(out, date);
        if (log == null || log.length() == 0) {
            SVNCommand.println(out, "0");
        } else {
            SVNCommand.println(out, String.valueOf(log.length()));
            SVNCommand.println(out, log);
        }
    }
}
