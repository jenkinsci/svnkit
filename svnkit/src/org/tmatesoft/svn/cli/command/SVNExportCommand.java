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
public class SVNExportCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String path = getCommandLine().getPathAt(0);
        String url = null;
        if (getCommandLine().hasURLs()) {
            url = getCommandLine().getURL(0);
        }
        String srcPath = null;
        if (url == null) {
            srcPath = getCommandLine().getPathAt(0); 
            path = getCommandLine().getPathAt(1); 
        }

        SVNRevision revision = parseRevision(getCommandLine());
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false, true));
        SVNUpdateClient updater = getClientManager().getUpdateClient();
        String eol = (String) getCommandLine().getArgumentValue(SVNArgument.EOL_STYLE);
        if (url != null) {
            if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
                revision = SVNRevision.HEAD;
            }
            updater.doExport(SVNURL.parseURIEncoded(url), new File(path).getAbsoluteFile(), revision, revision, eol, 
                    getCommandLine().hasArgument(SVNArgument.FORCE), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        } else if (srcPath != null) {
            if (revision == SVNRevision.UNDEFINED) {
                revision = SVNRevision.WORKING;
            }
            updater.doExport(new File(srcPath).getAbsoluteFile(), new File(path).getAbsoluteFile(), SVNRevision.UNDEFINED, revision, eol, 
                    getCommandLine().hasArgument(SVNArgument.FORCE), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
        }
    }
}
