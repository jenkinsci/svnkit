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
import java.io.IOException;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CopyCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
    	if (getCommandLine().hasPaths() && getCommandLine().hasURLs()) {
    	}
        if (getCommandLine().hasURLs()) {
            if (getCommandLine().hasPaths()) {
                final String path = getCommandLine().getPathAt(0);
                final String url = getCommandLine().getURL(0);
                if (getCommandLine().isPathURLBefore(url, path)) {
                    runRemoteToLocal(out);
                } else {
                    err.println("WC->URL copy is not yet implemented");
                    return;
                }
            } else {
                runRemote(out, err);
            }
        } else {
            runLocally(out);
        }
    }

    private void runLocally(final PrintStream out) throws SVNException {
        if (getCommandLine().getPathCount() != 2) {
            throw new SVNException("Please enter SRC and DST path");
        }

        final String absoluteSrcPath = getCommandLine().getPathAt(0);
        final String absoluteDstPath = getCommandLine().getPathAt(1);
        final ISVNWorkspace workspace = createWorkspace(absoluteSrcPath);
        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void modified(String path, int kind) {
                try {
                    path = convertPath(absoluteDstPath, workspace, path);
                } catch (IOException e) {}

                final String kindString = (kind == SVNStatus.ADDED ? "A" : "D");
                println(out, kindString + "  " + path);
            }
        });

        final String srcPath = SVNUtil.getWorkspacePath(workspace, absoluteSrcPath);
        final String dstTempPath = SVNUtil.getWorkspacePath(workspace, absoluteDstPath);
        final SVNStatus status = workspace.status(dstTempPath, false);
        final String dstPath = status != null && status.isDirectory() ? PathUtil.append(dstTempPath, PathUtil.tail(srcPath)) : dstTempPath;
        workspace.copy(srcPath, dstPath, false);
    }

    private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() != 2) {
            throw new SVNException("Please enter SRC and DST URL");
        }

        String srcURL = getCommandLine().getURL(0);
        String destURL = getCommandLine().getURL(1);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);

        String newPath = PathUtil.tail(destURL);
        newPath = PathUtil.removeLeadingSlash(newPath);
        newPath = PathUtil.decode(newPath);
        String root = PathUtil.removeTail(destURL);
        SVNRepository repository = createRepository(root);

        long revNumber = -1;
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        if (revStr != null) {
            try {
                revNumber = Long.parseLong(revStr);
            } catch (NumberFormatException e) {
                revNumber = -1;
            }
        }
        if (revNumber < 0) {
            revNumber = repository.getLatestRevision();
        }
        String newPathParent = null;
        SVNNodeKind nodeKind = repository.checkPath(newPath, -1);
        if (nodeKind == SVNNodeKind.DIR) {
        	DebugLog.log("path " + newPath + " already exists and its a dir");
        	newPathParent = newPath; 
        	newPath = PathUtil.tail(srcURL); 
        	newPath = PathUtil.append(newPathParent, newPath);
            nodeKind = repository.checkPath(newPath, -1);
            if (nodeKind == SVNNodeKind.DIR) {
            	DebugLog.log("can't copy to '" + PathUtil.append(destURL, newPath) + "', location already exists");
            	err.println("can't copy to '" + PathUtil.append(destURL, newPath) + "', location already exists");
            	return;
            }
        }

        SVNRepositoryLocation srcLocation = SVNRepositoryLocation.parseURL(srcURL);
        srcURL = srcLocation.getPath();
        srcURL = PathUtil.decode(srcURL);
        if (repository.getRepositoryRoot() == null) {
            repository.testConnection();
        }
        srcURL = srcURL.substring(repository.getRepositoryRoot().length());
        if (!srcURL.startsWith("/")) {
            srcURL += "/";
        }

        ISVNEditor editor = repository.getCommitEditor(message, null);
        try {
            editor.openRoot(-1);
            if (newPathParent != null) {
            	editor.openDir(newPathParent, -1);
            }
            editor.addDir(newPath, srcURL, revNumber);
            editor.closeDir();
            if (newPathParent != null) {
            	editor.closeDir();
            }
            editor.closeDir();

            SVNCommitInfo info = editor.closeEdit();

            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        } catch (SVNException e) {
            if (editor != null) {
                try {
                    editor.abortEdit();
                } catch (SVNException es) {}
            }
            throw e;
        }
    }

    private void runRemoteToLocal(final PrintStream out) throws SVNException {
        final String srcURL = getCommandLine().getURL(0);
        String destPathParent = getCommandLine().getPathAt(0);
        destPathParent = destPathParent.replace(File.separatorChar, '/');

        long revision = -1;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
            revision = Long.parseLong(revStr);
        }
        final ISVNWorkspace checkoutWorkspace = createWorkspace(PathUtil.removeTail(destPathParent), false);
        checkoutWorkspace.copy(SVNRepositoryLocation.parseURL(srcURL), PathUtil.tail(destPathParent), revision);
    }
}
