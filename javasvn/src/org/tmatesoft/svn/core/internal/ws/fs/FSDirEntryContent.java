/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.ISVNDirectoryContent;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSDirEntryContent implements ISVNDirectoryContent {

    // Fields =================================================================

    private final FSDirEntry myEntry;

    // Setup ==================================================================

    public FSDirEntryContent(FSDirEntry entry) {
        myEntry = entry;
    }

    // Implemented ============================================================

    public String getName() {
        return myEntry.getName();
    }

    public String getPath() {
        return myEntry.getPath();
    }

    public boolean isDirectory() {
        return true;
    }

    public ISVNDirectoryContent asDirectory() {
        return this;
    }

    public ISVNFileContent asFile() {
        return null;
    }

    public List getChildContents() throws SVNException {
        final List contents;
        try {
            contents = new ArrayList();
            for (Iterator it = myEntry.childEntries(); it.hasNext();) {
                final ISVNEntry entry = (ISVNEntry) it.next();
                contents.add(entry.getContent());
            }

            for (Iterator it = myEntry.unmanagedChildEntries(true); it
                    .hasNext();) {
                final ISVNEntry entry = (ISVNEntry) it.next();
                contents.add(entry.getContent());
            }

            return contents;
        } finally {
            myEntry.dispose();
        }
    }

    public void deleteWorkingCopyContent() throws SVNException {
        try {
            final File file = myEntry.getRootEntry()
                    .getWorkingCopyFile(myEntry);
            if (myEntry.isManaged()) {
                throw new SVNException(
                        "Can't delete managed/non-empty directory '" + file
                                + "'.");
            }
            FSUtil.deleteAll(file);
        } finally {
            myEntry.dispose();
        }
    }
}