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
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class CopyCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            if (getCommandLine().hasPaths()) {
                final String path = getCommandLine().getPathAt(0);
                final String url = getCommandLine().getURL(0);
                if (getCommandLine().isPathURLBefore(url, path)) {
                    if (getCommandLine().getArgumentValue(SVNArgument.MESSAGE) != null) {
                        SVNErrorManager.error("svn: Local, non-commit operations do not take a log message.");
                    }
                    runRemoteToLocal(out, err);
                } else {
                    runLocalToRemote(out, err);
                }
            } else {
                runRemote(out, err);
            }
        } else {
            if (getCommandLine().getArgumentValue(SVNArgument.MESSAGE) != null) {
                SVNErrorManager.error("svn: Local, non-commit operations do not take a log message.");
            }
            runLocally(out, err);
        }
    }

    private void runLocally(final PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getPathCount() != 2) {
            throw new SVNException("Please enter SRC and DST path");
        }

        final String absoluteSrcPath = getCommandLine().getPathAt(0);
        final String absoluteDstPath = getCommandLine().getPathAt(1);
        if (matchTabsInPath(absoluteDstPath, err) || matchTabsInPath(absoluteSrcPath, err)) {
            return;
        }

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        updater.doCopy(new File(absoluteSrcPath), srcRevision, new File(absoluteDstPath), force, false);
    }

    private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() != 2) {
            throw new SVNException("Please enter SRC and DST URL");
        }
        String srcURL = getCommandLine().getURL(0);
        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        String dstURL = getCommandLine().getURL(1);

        if (matchTabsInURL(srcURL, err) || matchTabsInURL(dstURL, err)) {
            return;
        }

        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        SVNCommitInfo result = updater.doCopy(SVNURL.parseURIEncoded(srcURL), srcRevision, SVNURL.parseURIEncoded(dstURL), false, commitMessage);
        if (result != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + result.getNewRevision() + ".");
        }
    }

    private void runRemoteToLocal(final PrintStream out, PrintStream err) throws SVNException {
        final String srcURL = getCommandLine().getURL(0);
        String dstPath = getCommandLine().getPathAt(0);
        SVNRevision revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        updater.doCopy(SVNURL.parseURIEncoded(srcURL), revision, new File(dstPath));
    }
    
    private void runLocalToRemote(final PrintStream out, PrintStream err) throws SVNException {
        final String dstURL = getCommandLine().getURL(0);
        String srcPath = getCommandLine().getPathAt(0);
        if (matchTabsInPath(srcPath, err) || matchTabsInURL(dstURL, err)) {
            return;
        }
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        SVNRevision revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.WORKING;
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();

        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        SVNCommitInfo info = updater.doCopy(new File(srcPath), srcRevision, SVNURL.parseURIEncoded(dstURL), message);
        if (info != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        }
    }
}
