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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class LsCommand extends SVNCommand {

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

    private void runLocally(final PrintStream out) throws SVNException {
        for (int index = 0; index < getCommandLine().getPathCount(); index++) {
            final String absolutePath = getCommandLine().getPathAt(index);
            final ISVNWorkspace workspace = createWorkspace(absolutePath);
            final String path = SVNUtil.getWorkspacePath(workspace, absolutePath);
            final SVNRepositoryLocation location = workspace.getLocation();
            final long revision = parseRevision(getCommandLine(), workspace, path);
            list(location, path, revision, out);
        }
    }

    private void list(SVNRepositoryLocation location, String path, long revision, PrintStream out) throws SVNException {
        final SVNRepository repository = createRepository(location.toString());
        final ArrayList entries = new ArrayList();
        final SVNNodeKind kind = repository.checkPath(path, revision);
        if (kind == SVNNodeKind.FILE) {
            repository.getFile(path, revision, null, new ByteArrayOutputStream());
            println(out, PathUtil.tail(path));
            return;
        }

        repository.getDir(path, revision, new HashMap(), entries);

        Collections.sort(entries, new DirEntryByNameComparator());
        for (Iterator it = entries.iterator(); it.hasNext();) {
            final SVNDirEntry entry = (SVNDirEntry) it.next();
            println(out, entry.getName() + (entry.getKind() == SVNNodeKind.DIR ? "/" : ""));
        }
    }

    private static final class DirEntryByNameComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            return ((SVNDirEntry) o1).getName().compareTo(((SVNDirEntry) o2).getName());
        }
    }
}
