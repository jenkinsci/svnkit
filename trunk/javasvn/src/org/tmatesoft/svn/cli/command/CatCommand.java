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
import java.io.PrintStream;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CatCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote();
        } else {
            runLocally(out);
        }
    }

    private void runRemote() throws SVNException {
        throw new SVNException("Remote cat is currently not supported.");
    }

    private void runLocally(PrintStream out) throws SVNException {
        for (int index = 0; index < getCommandLine().getPathCount(); index++) {
            final String absolutePath = getCommandLine().getPathAt(index);
            final ISVNWorkspace workspace = createWorkspace(absolutePath);
            final String path = SVNUtil.getWorkspacePath(workspace, absolutePath);
            cat(out, workspace, path);
        }
    }

    private void cat(PrintStream out, final ISVNWorkspace workspace, final String path) throws SVNException {
        final ISVNEntryContent content = workspace.getContent(path);
        if (!(content instanceof ISVNFileContent)) {
            throw new SVNException("Can only cat files.");
        }
        String filePath = workspace.getPropertyValue(path, SVNProperty.URL);
        if (workspace.getPropertyValue(path, SVNProperty.COPYFROM_URL) != null) {
            filePath = workspace.getPropertyValue(path, SVNProperty.COPYFROM_URL);
        }
        String fileURL = PathUtil.removeTail(filePath);
        filePath = PathUtil.tail(filePath);

        SVNRepository repository = createRepository(fileURL);
        repository.testConnection();
        String revision = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);

        long revNumber = -1;
        if (revision != null) {
            revNumber = getRevisionNumber(revision, repository);
        } 
        if (revNumber >= 0) {
            String currentRevisionStr = workspace.getPropertyValue(path, SVNProperty.REVISION);
            long currentRevNumber = Long.parseLong(currentRevisionStr);
            if (currentRevNumber != revNumber) {
                String absoluteFilePath = SVNRepositoryLocation.parseURL(fileURL).getPath();
                absoluteFilePath = absoluteFilePath.substring(repository.getRepositoryRoot().length());
                absoluteFilePath = PathUtil.append(absoluteFilePath, filePath);

                final String[] realPath = new String[] {absoluteFilePath};
                repository.log(new String[] {absoluteFilePath}, currentRevNumber, revNumber, true, false, new ISVNLogEntryHandler() {
                   public void handleLogEntry(SVNLogEntry logEntry) {
                       Map paths = logEntry.getChangedPaths();
                       SVNLogEntryPath p = (SVNLogEntryPath) paths.get(realPath[0]);
                       if (p.getCopyPath() != null) {
                           realPath[0] = p.getCopyPath();
                       }
                   }
                });
                filePath = realPath[0];
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        repository.getFile(filePath, revNumber, null, bos);
        out.print(new String(bos.toByteArray()));
    }

    private static long getRevisionNumber(String revision, SVNRepository repository) throws SVNException { 
        if (revision == null) {
            return -2;
        }
        try {
            return Long.parseLong(revision);
        } catch (NumberFormatException nfe) {}
        
        return repository.getLatestRevision();
    }
}
