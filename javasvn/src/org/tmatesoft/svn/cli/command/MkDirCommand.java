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

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class MkDirCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasURLs()) {
            createLocalDirectories(out);
        } else {
            createRemoteDirectories(out);
        }
    }

    private void createLocalDirectories(final PrintStream out) throws SVNException {
        final Collection paths = new ArrayList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            paths.add(getCommandLine().getPathAt(i));
        }

        final String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        final String root = PathUtil.getFSCommonRoot(pathsArray);
        DebugLog.log("MKDIR root: " + root);

        final ISVNWorkspace ws = createWorkspace(root);
        ws.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void modified(String path, int kind) {
                try {
                    path = convertPath(root, ws, path);
                } catch (IOException e) {}

                println(out, "A  " + path);
            }
        });

        for (int i = 0; i < pathsArray.length; i++) {
            ws.add(SVNUtil.getWorkspacePath(ws, pathsArray[i]), true, false);
        }
    }

    private void createRemoteDirectories(final PrintStream out) throws SVNException {
        Collection urls = new ArrayList();
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            urls.add(getCommandLine().getURL(i));
        }

        String[] urlsArray = (String[]) urls.toArray(new String[urls.size()]);
        final String root = PathUtil.getFSCommonRoot(urlsArray);
        DebugLog.log("MKDIR root: " + root);

        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        for (int i = 0; i < urlsArray.length; i++) {
            String dir = urlsArray[i].substring(root.length());
            dir = PathUtil.removeLeadingSlash(dir);
            urlsArray[i] = dir;
        }
        ISVNEditor editor = null;
        SVNRepository repository = createRepository(root);
        editor = repository.getCommitEditor(message, null);

        final SVNCommitInfo info;
        try {
            editor.openRoot(-1);
            for (int i = 0; i < urlsArray.length; i++) {
                final String path = PathUtil.decode(urlsArray[i]);
                DebugLog.log(path);

                editor.addDir(path, null, -1);
            }
            editor.closeDir();
        } finally {
            info = editor.closeEdit();
        }

        println(out);
        println(out, "Committed revision " + info.getNewRevision() + ".");
    }
}
