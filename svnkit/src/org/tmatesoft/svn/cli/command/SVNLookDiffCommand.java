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
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookDiffCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        
        boolean diffAdded = !getCommandLine().hasArgument(SVNArgument.NO_DIFF_ADDED);
        boolean diffDeleted = !getCommandLine().hasArgument(SVNArgument.NO_DIFF_DELETED);
        boolean diffCopyFrom = !getCommandLine().hasArgument(SVNArgument.DIFF_COPY_FROM);

        SVNRevision revision = SVNRevision.HEAD;
        SVNLookClient lookClient = getClientManager().getLookClient();
        if (getCommandLine().hasArgument(SVNArgument.TRANSACTION)) {
            String transactionName = (String) getCommandLine().getArgumentValue(SVNArgument.TRANSACTION);
            lookClient.doGetDiff(reposRoot, transactionName, diffDeleted, diffAdded, diffCopyFrom, out);
            return;
        } else if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        } 
        lookClient.doGetDiff(reposRoot, revision, diffDeleted, diffAdded, diffCopyFrom, out);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

}
