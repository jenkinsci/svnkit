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

/**
 * @author TMate Software Ltd.
 */
public class ExportCommand extends SVNCommand {

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
        SVNUpdateClient updater = new SVNUpdateClient(getCredentialsProvider(), 
                new SVNCommandEventProcessor(out, err, false, true));
        String eol = (String) getCommandLine().getArgumentValue(SVNArgument.EOL_STYLE);
        if (url != null) {
            if (revision != SVNRevision.HEAD && revision.getDate() == null && revision.getNumber() < 0) {
                revision = SVNRevision.HEAD;
            }
            updater.doExport(url, new File(path).getAbsoluteFile(), revision, revision, eol, 
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
