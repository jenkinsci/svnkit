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

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCleanupCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String path = getCommandLine().getPathAt(0);
        SVNWCClient client = getClientManager().getWCClient();
        client.doCleanup(new File(path).getAbsoluteFile());
    }
}
