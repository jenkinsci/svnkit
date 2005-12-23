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
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class LsCommand extends SVNCommand implements ISVNDirEntryHandler {

    private PrintStream myPrintStream;

    public void run(PrintStream out, PrintStream err) throws SVNException {
        boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        myPrintStream = out;

        SVNRevision revision = parseRevision(getCommandLine());
        SVNLogClient logClient = getClientManager().getLogClient();
        if (!getCommandLine().hasURLs() && !getCommandLine().hasPaths()) {
            getCommandLine().setPathAt(0, ".");
        }
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            logClient.doList(SVNURL.parseURIEncoded(url), getCommandLine().getPegRevision(i), revision == null || !revision.isValid() ? SVNRevision.HEAD : revision, recursive, this);
        }
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            File path = new File(getCommandLine().getPathAt(i)).getAbsoluteFile();
            logClient.doList(path, getCommandLine().getPathPegRevision(i), revision == null || !revision.isValid() ? SVNRevision.BASE : revision, recursive, this);
        }
    }

    public void handleDirEntry(SVNDirEntry dirEntry) {
        myPrintStream.print(dirEntry.getRelativePath());
        if (dirEntry.getKind() == SVNNodeKind.DIR) {
            myPrintStream.print('/');
        }
        myPrintStream.println();
    }

}
