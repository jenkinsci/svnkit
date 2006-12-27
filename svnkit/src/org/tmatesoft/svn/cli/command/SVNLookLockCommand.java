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
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNLookLockCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(err, "jsvnlook: Repository argument required");
            System.exit(1);
        }
        if (getCommandLine().getPathCount() < 2) {
            SVNCommand.println(err, "jsvnlook: Missing path argument");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  
        String path = SVNPathUtil.canonicalizeAbsPath(getCommandLine().getPathAt(1));

        SVNLookClient lookClient = getClientManager().getLookClient();
        SVNLock lock = lookClient.doGetLock(reposRoot, path);
        if (lock != null) {
            String creationTime = SVNLookDateCommand.formatDate(lock.getCreationDate());
            String expirationTime = lock.getExpirationDate() != null ? SVNLookDateCommand.formatDate(lock.getExpirationDate()) : "";
            int commentLines = 0; 
            if (lock.getComment() != null) {
                commentLines = getLinesCount(lock.getComment());
            }
            SVNCommand.println(out, "UUID Token: " + lock.getID());
            SVNCommand.println(out, "Owner: " + lock.getOwner());
            SVNCommand.println(out, "Created: " + creationTime);
            SVNCommand.println(out, "Expires: " + expirationTime);
            if (commentLines != 1) {
                SVNCommand.println(out, "Comment (" + commentLines + " lines):");
                SVNCommand.println(out, lock.getComment() != null ? lock.getComment() : "");
            } else {
                SVNCommand.println(out, "Comment (" + commentLines + " line):");
                SVNCommand.println(out, lock.getComment() != null ? lock.getComment() : "");
            }
        }
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

}
