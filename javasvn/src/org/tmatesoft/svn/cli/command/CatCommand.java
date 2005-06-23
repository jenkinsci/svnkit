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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.PrintStream;

/**
 * @author TMate Software Ltd.
 */
public class CatCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNWCClient wcClient = new SVNWCClient(getOptions(), null);
        SVNRevision revision = SVNRevision.BASE;

        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        for (int index = 0; index < getCommandLine().getPathCount(); index++) {
            final String absolutePath = getCommandLine().getPathAt(index);
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(index);
            wcClient.doGetFileContents(new File(absolutePath), pegRevision, revision, true, out);
            out.flush();
        }

        revision = SVNRevision.HEAD;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        for (int index = 0; index < getCommandLine().getURLCount(); index++) {
            final String url = getCommandLine().getURL(index);
            SVNRevision pegRevision = getCommandLine().getPegRevision(index);
            wcClient.doGetFileContents(url, pegRevision, revision, true, out);
            out.flush();
        }
    }
}
