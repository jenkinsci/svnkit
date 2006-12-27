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

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.admin.ISVNTransactionHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminListTransactionsCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "svnadmin: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  

        ListTransactionHandler handler = new ListTransactionHandler(out);
        SVNAdminClient adminClient = getClientManager().getAdminClient();
        adminClient.doListTransactions(reposRoot, handler);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    private class ListTransactionHandler implements ISVNTransactionHandler {

        private PrintStream myOut;
        
        public ListTransactionHandler(PrintStream out) {
            myOut = out;
        }
        
        public void handleRemoveTransaction(String txnName, File txnDir) throws SVNException {
        }
        
        public void handleTransaction(String txnName, File txnDir) throws SVNException {
            SVNCommand.println(myOut, txnName);
        }
    }

}
