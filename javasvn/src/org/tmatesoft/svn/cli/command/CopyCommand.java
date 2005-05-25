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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.internal.wc.ISVNEventListener;
import org.tmatesoft.svn.core.internal.wc.SVNEvent;
import org.tmatesoft.svn.core.internal.wc.SVNEventAction;
import org.tmatesoft.svn.core.internal.wc.SVNRevision;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateClient;
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
        final ISVNWorkspace workspace = createWorkspace(absoluteSrcPath);
        final String srcPath = SVNUtil.getWorkspacePath(workspace, absoluteSrcPath);
        SVNRepository repository = createRepository(workspace.getLocation(srcPath).toCanonicalForm());
        long revNumber = getRevisionNumber((String) getCommandLine().getArgumentValue(SVNArgument.REVISION),
                srcPath, workspace, repository);
        if (revNumber >= 0 && revNumber != SVNProperty.longValue(workspace.getPropertyValue(srcPath, SVNProperty.REVISION))) {
            getCommandLine().setPathAt(0, null);
            getCommandLine().setURLAt(0, workspace.getLocation(srcPath).toCanonicalForm());
            getCommandLine().setArgumentValue(SVNArgument.REVISION, Long.toString(revNumber));  
            runRemoteToLocal(out, err);
            return;
        }

        workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
            public void modified(String path, int kind) {
                try {
                    path = convertPath(absoluteDstPath, workspace, path);
                } catch (IOException e) {}

                final String kindString = (kind == SVNStatus.ADDED ? "A" : "D");
                println(out, kindString + "  " + path);
            }
        });
        
        final String dstTempPath = SVNUtil.getWorkspacePath(workspace, absoluteDstPath);
        final SVNStatus status = workspace.status(dstTempPath, false);
        DebugLog.log("COPY: dst status is: " + status);
        if (status != null && status.getContentsStatus() == SVNStatus.DELETED) {
            try {
                err.print("Can't copy to '" + convertPath(absoluteDstPath, workspace, dstTempPath) + "' - path is scheduled for deletion");
            } catch (IOException e) {
            }
            return;
        }
        final String dstPath = status != null && status.isDirectory() ? PathUtil.append(dstTempPath, PathUtil.tail(srcPath)) : dstTempPath;
        workspace.copy(srcPath, dstPath, false);
    }

    private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() != 2) {
            throw new SVNException("Please enter SRC and DST URL");
        }

        String srcURL = getCommandLine().getURL(0);
        String destURL = getCommandLine().getURL(1);
        if (matchTabsInPath(PathUtil.decode(srcURL), err) || matchTabsInPath(PathUtil.decode(destURL), err)) {
            return;
        }
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);

        SVNRepository repository = createRepository(destURL);
        String root = destURL;
        String newPath = PathUtil.tail(srcURL);
        SVNNodeKind nodeKind = repository.checkPath("", -1);
        if (nodeKind == SVNNodeKind.NONE) {
            // dst doesn't exists.
            root = PathUtil.removeTail(destURL);
            repository = createRepository(root);
            newPath = PathUtil.tail(destURL);
        } 
        newPath = PathUtil.removeLeadingSlash(newPath);
        newPath = PathUtil.decode(newPath);

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
        DebugLog.log("checking new path: " + newPath);
        nodeKind = repository.checkPath(newPath, -1);
        if (nodeKind != SVNNodeKind.NONE) {
        	DebugLog.log("can't copy to '" + PathUtil.append(destURL, newPath) + "', location already exists");
        	err.println("can't copy to '" + PathUtil.append(destURL, newPath) + "', location already exists");
        	return;
        } else if (repository.checkPath(PathUtil.removeTail(newPath), -1) == SVNNodeKind.NONE) {
            err.println("svn: Path '" + newPath + "' not present");
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

    private void runRemoteToLocal(final PrintStream out, PrintStream err) throws SVNException {
        final String srcURL = getCommandLine().getURL(0);
        String destPathParent = getCommandLine().getPathAt(0);
        long revNumber = parseRevision(getCommandLine(), null, null);
        SVNRevision revision = SVNRevision.HEAD;
        if (revNumber >= 0) {
            revision = SVNRevision.create(revNumber);
        }
        SVNUpdateClient updater = new SVNUpdateClient(getCredentialsProvider(), new ISVNEventListener() {
            public void svnEvent(SVNEvent event) {
                if (event.getAction() == SVNEventAction.ADD) {
                    println(out, "A    " + getPath(event.getFile()));
                } 
            }
        });
        updater.doCopy(srcURL, new File(destPathParent), revision);
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
