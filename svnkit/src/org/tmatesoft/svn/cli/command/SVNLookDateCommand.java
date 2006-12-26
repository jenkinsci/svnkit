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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookDateCommand extends SVNCommand {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (EE, d MMM yyyy)", Locale.getDefault());

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
            Date date = lookClient.doGetDate(reposRoot, transactionName);
            String dateStamp = date != null ? formatDate(date) : ""; 
            SVNCommand.println(out, dateStamp);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 
        Date date = lookClient.doGetDate(reposRoot, revision);
        String dateStamp = date != null ? formatDate(date) : ""; 
        SVNCommand.println(out, dateStamp);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public static String formatDate(Date date) {
        return DATE_FORMAT.format(date);
    }

}
