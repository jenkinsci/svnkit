/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 * @since   1.1.2
 */
public class SVNAdminListLocksCommand extends SVNCommand implements ISVNAdminEventHandler {
    private PrintStream myOut;

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "svnadmin: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  

        myOut = out;
        SVNAdminClient adminClient = getClientManager().getAdminClient();
        adminClient.setEventHandler(this);
        adminClient.doListLocks(reposRoot);
    
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event != null && event.getAction() == SVNAdminEventAction.LOCK_LISTED) {
            SVNLock lock = event.getLock();
            if (lock != null) {
                String creationDate = SVNTimeUtil.formatDate(lock.getCreationDate());
                String expirationDate = lock.getExpirationDate() != null ? SVNTimeUtil.formatDate(lock.getExpirationDate()) : "";  
                 
                int commentLines = 0; 
                if (lock.getComment() != null) {
                    commentLines = getLinesCount(lock.getComment());
                }

                SVNCommand.println(myOut, "Path: " + lock.getPath());
                SVNCommand.println(myOut, "UUID Token: " + lock.getID());
                SVNCommand.println(myOut, "Owner: " + lock.getOwner());
                SVNCommand.println(myOut, "Created: " + creationDate);
                SVNCommand.println(myOut, "Expires: " + expirationDate);
                if (commentLines != 1) {
                    SVNCommand.println(myOut, "Comment (" + commentLines + " lines):");
                    SVNCommand.println(myOut, lock.getComment() != null ? lock.getComment() : "");
                } else {
                    SVNCommand.println(myOut, "Comment (" + commentLines + " line):");
                    SVNCommand.println(myOut, lock.getComment() != null ? lock.getComment() : "");
                }
                SVNCommand.println(myOut, "");
            }
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

}
