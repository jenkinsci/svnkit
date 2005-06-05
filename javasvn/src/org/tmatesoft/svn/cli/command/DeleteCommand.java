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
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class DeleteCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote(out);
        } else {
            runLocally(out, err);
        }
    }

    private void runRemote(PrintStream out) throws SVNException {
        final String entryUrl = getCommandLine().getURL(0);
        final String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        final String entry = PathUtil.tail(entryUrl);
        final String url = entryUrl.substring(0, entryUrl.length() - entry.length());
        final SVNRepository repository = createRepository(url);
        ISVNEditor editor = repository.getCommitEditor(commitMessage != null ? commitMessage : "", null);
        try {
            editor.openRoot(-1);
            editor.deleteEntry(entry, -1);
            editor.closeDir();
            SVNCommitInfo info = editor.closeEdit();

            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        } catch (SVNException ex) {
            editor.abortEdit();
            throw ex;
        }
    }

    private void runLocally(final PrintStream out, PrintStream err) throws SVNException {
    	boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
    	SVNWCClient client = new SVNWCClient(getCredentialsProvider(), new SVNCommandEventProcessor(out, err, false));
    	
    	boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            try {
            	client.doDelete(new File(absolutePath), force, false);
            } catch (SVNException e) {
            	err.println(e.getMessage());
            	error = true;
            }
        }
        if (error) {
        	System.exit(1);
        }
    }
}
