/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.command;

import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @since   1.1
 */
public class SVNSyncCopyRevpropsCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            String destURL = getCommandLine().getURL(0);
            if (matchTabsInURL(destURL, err)) {
                return;
            }
            
            SVNRevision revision = parseRevision(getCommandLine());
            SVNDebugLog.getDefaultLog().info(getCommandLine().getPathAt(0));

            long revNumber = -1;
            if (revision == SVNRevision.UNDEFINED) {
                
            } else {
                revNumber = revision.getNumber();
            }
            
            SVNClientManager manager = getClientManager();
            SVNAdminClient adminClient = manager.getAdminClient();
            adminClient.doCopyRevisionProperties(SVNURL.parseURIDecoded(destURL), revNumber);
        }
    }

}
