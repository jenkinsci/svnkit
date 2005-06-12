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
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

import java.io.File;
import java.io.PrintStream;

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
                    runRemoteToLocal(out, err);
                } else {
                    runLocalToRemote(out, err);
                }
            } else {
                runRemote(out, err);
            }
        } else {
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

        SVNRevision srcRevision = SVNRevision.WORKING;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }

        SVNCopyClient updater = new SVNCopyClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, err, false));
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        updater.doCopy(new File(absoluteSrcPath), null, srcRevision, new File(absoluteDstPath), null, SVNRevision.WORKING, force, false, null);
    }

    private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() != 2) {
            throw new SVNException("Please enter SRC and DST URL");
        }
        String srcURL = getCommandLine().getURL(0);
        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        SVNRevision srcPegRevision = getCommandLine().getPegRevision(0);
        String dstURL = getCommandLine().getURL(1);
        SVNRevision dstPegRevision = getCommandLine().getPegRevision(1);

        if (matchTabsInPath(srcURL, err) || matchTabsInPath(dstURL, err)) {
            return;
        }

        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        SVNCopyClient updater = new SVNCopyClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, err, false));
        long committedRevision = updater.doCopy(srcURL, srcPegRevision, srcRevision, dstURL, dstPegRevision, false, commitMessage);
        if (committedRevision >= 0) {
            out.println();
            out.println("Committed revision " + committedRevision + ".");
        }
    }

    private void runRemoteToLocal(final PrintStream out, PrintStream err) throws SVNException {
        final String srcURL = getCommandLine().getURL(0);
        String dstPath = getCommandLine().getPathAt(0);
        SVNRevision revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNCopyClient updater = new SVNCopyClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, err, false));
        updater.doCopy(srcURL, getCommandLine().getPegRevision(0), revision, new File(dstPath), null, SVNRevision.WORKING, false, null);
    }
    
    private void runLocalToRemote(final PrintStream out, PrintStream err) throws SVNException {
        final String dstURL = getCommandLine().getURL(0);
        String srcPath = getCommandLine().getPathAt(0);
        if (matchTabsInPath(srcPath, err) || matchTabsInPath(PathUtil.decode(dstURL), err)) {
            return;
        }
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        srcPath = srcPath.replace(File.separatorChar, '/');

        final ISVNWorkspace ws = createWorkspace(srcPath);
        String wsPath = SVNUtil.getWorkspacePath(ws, srcPath);
        DebugLog.log("workspace path is : " + wsPath);
        long revision = ws.copy(wsPath, SVNRepositoryLocation.parseURL(dstURL), message);

        out.println();
        out.println("Committed revision " + revision + ".");
    }
}
