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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CatCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote();
        } else {
            runLocally();
        }
    }

    private void runRemote() throws SVNException {
        throw new SVNException("Remote cat is currently not supported.");
    }

    private void runLocally() throws SVNException {
        for (int index = 0; index < getCommandLine().getPathCount(); index++) {
            final String absolutePath = getCommandLine().getPathAt(index);
            final ISVNWorkspace workspace = createWorkspace(absolutePath);
            final String path = SVNUtil.getWorkspacePath(workspace, absolutePath);
            cat(workspace, path);
        }
    }

    private void cat(final ISVNWorkspace workspace, final String path) throws SVNException {
        final ISVNEntryContent content = workspace.getContent(path);
        if (!(content instanceof ISVNFileContent)) {
            throw new SVNException("Can only cat files.");
        }

        try {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            content.asFile().getBaseFileContent(os);
            os.close();

            final InputStreamReader is = new InputStreamReader(new ByteArrayInputStream(os.toByteArray()));
            for (int b = is.read(); b != -1; b = is.read()) {
                System.out.print((char) b);
            }
        } catch (IOException ex) {
            throw new SVNException(ex);
        }
    }
}
