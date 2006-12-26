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
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCommitClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNImportCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        String path = ".";
        if (getCommandLine().getPathCount() >= 1) {
            path = getCommandLine().getPathAt(0);
        }
        String url = getCommandLine().getURL(0);
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean disableAutoProps = getCommandLine().hasArgument(SVNArgument.NO_AUTO_PROPS);
        boolean enableAutoProps = getCommandLine().hasArgument(SVNArgument.AUTO_PROPS);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCommitClient commitClient = getClientManager().getCommitClient();

        if (disableAutoProps) {
            commitClient.getOptions().setUseAutoProperties(false);
        }
        if (enableAutoProps) {
            commitClient.getOptions().setUseAutoProperties(true);
        }

        boolean useGlobalIgnores = !getCommandLine().hasArgument(SVNArgument.NO_IGNORE);
        SVNCommitInfo info = commitClient.doImport(new File(path), SVNURL.parseURIEncoded(url), message, useGlobalIgnores, recursive);
        if (info != SVNCommitInfo.NULL) {
            out.println();
            out.println("Imported revision " + info.getNewRevision() + ".");
        }
    }

}
