/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNSynchronizeEditor implements ISVNEditor {

    private ISVNEditor myWrappedEditor;
    private SVNURL myTargetURL;
    private boolean myIsRootOpened;
    private long myBaseRevision;
    
    public SVNSynchronizeEditor(ISVNEditor wrappedEditor, SVNURL toURL, long baseRevision) {
        myWrappedEditor = wrappedEditor;
        myTargetURL = toURL;
        myIsRootOpened = false;
        myBaseRevision = baseRevision;
    }
    
    public void abortEdit() throws SVNException {
    
    }

    public void absentDir(String path) throws SVNException {
        myWrappedEditor.absentDir(path);
    }

    public void absentFile(String path) throws SVNException {
        myWrappedEditor.absentFile(path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            copyFromPath = myTargetURL.appendPath(copyFromPath, false).toDecodedString();
        }
        myWrappedEditor.addDir(path, copyFromPath, copyFromRevision);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            copyFromPath = myTargetURL.appendPath(copyFromPath, false).toDecodedString();
        }
        myWrappedEditor.addFile(path, copyFromPath, copyFromRevision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            myWrappedEditor.changeDirProperty(name, value);
        }
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            myWrappedEditor.changeFileProperty(path, name, value);
        }
    }

    public void closeDir() throws SVNException {
        myWrappedEditor.closeDir();
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpened) {
            myWrappedEditor.openRoot(myBaseRevision);
        }
        return myWrappedEditor.closeEdit();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myWrappedEditor.closeFile(path, textChecksum);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myWrappedEditor.deleteEntry(path, revision);
    }

    public void openDir(String path, long revision) throws SVNException {
        myWrappedEditor.openDir(path, revision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myWrappedEditor.openFile(path, revision);
    }

    public void openRoot(long revision) throws SVNException {
        myWrappedEditor.openRoot(revision);
        myIsRootOpened = true;
    }

    public void targetRevision(long revision) throws SVNException {
        myWrappedEditor.targetRevision(revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myWrappedEditor.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myWrappedEditor.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myWrappedEditor.textDeltaEnd(path);
    }

}
