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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CommitCommand extends SVNCommand {

    public void run(final PrintStream out, PrintStream err) throws SVNException {
        checkEditorCommand();
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        String[] localPaths = new String[getCommandLine().getPathCount()];
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            localPaths[i] = getCommandLine().getPathAt(i).replace(File.separatorChar, '/');
        }
        final String homePath = localPaths.length == 1 ? localPaths[0] : PathUtil.getFSCommonRoot(localPaths);

        List pathsList = new ArrayList();
        String[] paths;
        try {
            for (int i = 0; i < getCommandLine().getPathCount(); i++) {
                String path = getCommandLine().getPathAt(i);
                pathsList.add(new File(path).getCanonicalPath().replace(File.separatorChar, '/'));
            }
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return;
        }
        DebugLog.log("commit paths: " + pathsList);
        paths = (String[]) pathsList.toArray(new String[pathsList.size()]);
        // only if path is not a single directory!
        String rootPath;
        if (getCommandLine().getPathCount() == 1 && new File(getCommandLine().getPathAt(0)).isDirectory()) {
            rootPath = (String) pathsList.get(0);
        } else {
            rootPath = PathUtil.getFSCommonRoot(paths);
        }
        DebugLog.log("commit root: " + rootPath);

        final ISVNWorkspace workspace = createWorkspace(rootPath);
        for (int i = 0; i < paths.length; i++) {
            paths[i] = SVNUtil.getWorkspacePath(workspace, paths[i]);
        }
        String message = getCommitMessage();

        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void committed(String committedPath, int kind) {
                DebugLog.log("commit path: " + committedPath);
                DebugLog.log("home path: " + homePath);
                String verb = "Sending ";
                if (kind == SVNStatus.ADDED) {
                    verb = "Adding ";
                    try {
                        String mimeType = workspace.getPropertyValue(committedPath, SVNProperty.MIME_TYPE);

                        if (mimeType != null && !mimeType.startsWith("text")) {
                            verb += " (bin) ";
                        }
                        DebugLog.log("mimetype: " + mimeType);
                    } catch (SVNException e1) {
                        DebugLog.error(e1);
                    }
                } else if (kind == SVNStatus.DELETED) {
                    verb = "Deleting ";
                } else if (kind == SVNStatus.REPLACED) {
                    verb = "Replacing ";
                }
                try {
                    committedPath = convertPath(homePath, workspace, committedPath);
                } catch (IOException e) {}
                DebugLog.log(verb + committedPath);
                out.println(verb + committedPath);
            }
        });
        long revision = workspace.commit(paths, message, recursive);
        if (revision >= 0) {
            out.println("Committed revision " + revision + ".");
        }
    }

    private String getCommitMessage() throws SVNException {
        String fileName = (String) getCommandLine().getArgumentValue(SVNArgument.FILE);
        if (fileName != null) {
            FileInputStream is = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                is = new FileInputStream(fileName);
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    if (r == 0) {
                        // invalid
                        throw new SVNException("error: commit message contains a zero byte");
                    }
                    bos.write(r);
                }
            } catch (IOException e) {
                throw new SVNException(e);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    bos.close();
                } catch (IOException e) {
                    throw new SVNException(e);
                }
            }
            return new String(bos.toByteArray());
        }
        return (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
    }

    private void checkEditorCommand() throws SVNException {
        final String editorCommand = (String) getCommandLine().getArgumentValue(SVNArgument.EDITOR_CMD);
        if (editorCommand == null) {
            return;
        }

        throw new SVNException("Commit failed. Can't handle external editor " + editorCommand);
    }
}
