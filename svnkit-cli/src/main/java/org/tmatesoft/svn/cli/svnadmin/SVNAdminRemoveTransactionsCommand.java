/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnadmin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminRemoveTransactionsCommand extends SVNAdminCommand implements ISVNAdminEventHandler {

    public SVNAdminRemoveTransactionsCommand() {
        super("rmtxns", null);
    }

    protected Collection createSupportedOptions() {
        List options = new ArrayList();
        options.add(SVNAdminOption.QUIET);
        return options;
    }

    public void run() throws SVNException {
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        
        List targets = getEnvironment().combineTargets(null, false);
        if (!targets.isEmpty()) {
            targets.remove(0);
        }
        String[] transactions = (String[]) targets.toArray(new String[targets.size()]);
        client.setEventHandler(this);
        client.doRemoveTransactions(getLocalRepository(), transactions);
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event != null && event.getAction() == SVNAdminEventAction.TRANSACTION_REMOVED) {
            if (!getSVNAdminEnvironment().isQuiet()) {
                String txnName = event.getTxnName();
                getEnvironment().getOut().println("Transaction '" + txnName + "' removed.");
            }
        } else if (event.getError() != null) {
            getEnvironment().handleError(event.getError());
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
        getEnvironment().checkCancelled();
    }
}
