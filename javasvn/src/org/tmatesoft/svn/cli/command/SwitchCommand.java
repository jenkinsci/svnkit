/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SwitchCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String url = getCommandLine().getURL(0);
        String absolutePath = getCommandLine().getPathAt(0);

        long revNumber = parseRevision(getCommandLine(), null, null);
        SVNRevision revision = SVNRevision.HEAD;
        if (revNumber >= 0) {
            revision = SVNRevision.create(revNumber);
        }
        SVNUpdateClient updater = new SVNUpdateClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, err, false, false));
        try {
            if (getCommandLine().hasArgument(SVNArgument.RELOCATE)) {
                updater.doRelocate(new File(absolutePath).getAbsoluteFile(), url, getCommandLine().getURL(1), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            } else {
                updater.doSwitch(new File(absolutePath).getAbsoluteFile(), url, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        } catch (Throwable th) {
            DebugLog.error(th);
            println(err, th.getMessage());
            println(err);
            System.exit(1);
        }
    }
}
