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
        Collection paths = new ArrayList(); 
        if (!getCommandLine().hasURLs()) {
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                paths.add(getCommandLine().getPathAt(i));
            }
        } else {
            for(int i = 0; i < getCommandLine().getURLCount(); i++) {
                paths.add(getCommandLine().getURL(i));
            }
        }
        String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        final String root = PathUtil.getFSCommonRoot(pathsArray);
        DebugLog.log("MKDIR root: " + root);
        if (getCommandLine().hasURLs()) {
            String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
            for(int i = 0; i < pathsArray.length; i++) {
                String dir = pathsArray[i].substring(root.length());
                dir = PathUtil.removeLeadingSlash(dir);
                pathsArray[i] = dir;
            }
            ISVNEditor editor = null;
            SVNRepository repository = createRepository(root);
            editor = repository.getCommitEditor(message, null);
            editor.openRoot(-1);
            for(int i = 0; i < pathsArray.length; i++) {
                editor.addDir(PathUtil.decode(pathsArray[i]), null, -1);
                editor.closeDir();
            }
            editor.closeDir();
            SVNCommitInfo info = editor.closeEdit();
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        } else {
            final ISVNWorkspace ws = createWorkspace(root);
            ws.addWorkspaceListener(new SVNWorkspaceAdapter() {
                public void modified(String path, int kind) {
                    try {
                        path = convertPath(root, ws, path);
                    }
                    catch (IOException e) {
                    }
                    DebugLog.log("A  " + path);
                    out.println("A  " + path);
                }
            });
            for(int i = 0; i < pathsArray.length; i++) {
                ws.add(SVNUtil.getWorkspacePath(ws, pathsArray[i]), true, false);
            }
        }
	}
}
