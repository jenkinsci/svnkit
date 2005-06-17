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
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class AddCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean disableAutoProps = getCommandLine().hasArgument(SVNArgument.NO_AUTO_PROPS);
        
        SVNWCClient wcClient = new SVNWCClient(getCredentialsProvider(), getOptions(), new SVNCommandEventProcessor(out, err, false));
    	wcClient.getOptions().setUseAutoProperties(!disableAutoProps);
    	DebugLog.log("ignored? (foo.o) " + wcClient.getOptions().isIgnored("foo.o"));

    	for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            matchTabsInPath(absolutePath, err);
            DebugLog.log("adding path: " + absolutePath);
            wcClient.doAdd(new File(absolutePath), force, true, false, recursive);
        }
    }
}
