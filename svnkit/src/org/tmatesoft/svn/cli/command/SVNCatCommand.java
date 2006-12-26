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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCatCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNWCClient wcClient = getClientManager().getWCClient();
        SVNRevision revision = SVNRevision.BASE;

        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        for (int index = 0; index < getCommandLine().getPathCount(); index++) {
            final String absolutePath = getCommandLine().getPathAt(index);
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(index);
            try {
                wcClient.doGetFileContents(new File(absolutePath), pegRevision, revision, true, out);
            } catch (SVNException e) {
                String message = e.getMessage();
                err.println(message);                
            }
            out.flush();
        }

        revision = SVNRevision.HEAD;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        for (int index = 0; index < getCommandLine().getURLCount(); index++) {
            final String url = getCommandLine().getURL(index);
            SVNRevision pegRevision = getCommandLine().getPegRevision(index);
            try {
                wcClient.doGetFileContents(SVNURL.parseURIEncoded(url), pegRevision, revision, true, out);
            } catch (SVNException e) {
                String message = e.getMessage();
                err.println(message);                
            }
            out.flush();
        }
    }
}
