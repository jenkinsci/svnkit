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

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNSwitchCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String url = getCommandLine().getURL(0);
        String absolutePath = getCommandLine().getPathAt(0);

        SVNRevision revision = parseRevision(getCommandLine());
        if (!revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false, false));
        SVNUpdateClient updater = getClientManager().getUpdateClient();
        try {
            SVNURL switchURL = SVNURL.parseURIEncoded(url);
            
            if (getCommandLine().hasArgument(SVNArgument.RELOCATE)) {
                SVNURL targetURL = SVNURL.parseURIEncoded(getCommandLine().getURL(1));
                updater.doRelocate(new File(absolutePath).getAbsoluteFile(), switchURL, targetURL, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            } else {
                updater.doSwitch(new File(absolutePath).getAbsoluteFile(), switchURL, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        } catch (Throwable th) {
            updater.getDebugLog().info(th);
            println(err, th.getMessage());
            println(err);
            System.exit(1);
        }
    }
}
