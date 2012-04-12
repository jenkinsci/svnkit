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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;

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
public class SVNAdminRecoverCommand extends SVNAdminCommand implements ISVNAdminEventHandler {

    public SVNAdminRecoverCommand() {
        super("recover", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNAdminOption.WAIT);
        return options;
    }

    public void run() throws SVNException {
        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        client.setEventHandler(this);
        client.doRecover(getLocalRepository());
        getEnvironment().getOut().println();
        getEnvironment().getOut().println("Recovery completed.");
        long youngestRevision = client.getYoungestRevision(getLocalRepository());
        String message = "The latest repos revision is {0}.";
        message = MessageFormat.format(message, new Object[] { String.valueOf(youngestRevision) });
        getEnvironment().getOut().println(message);
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event.getAction() == SVNAdminEventAction.RECOVERY_STARTED) {
            getEnvironment().getOut().println("Repository lock acquired.");
            getEnvironment().getOut().println("Please wait; recovering the repository may take some time...");
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
        getEnvironment().checkCancelled();
    }

}
