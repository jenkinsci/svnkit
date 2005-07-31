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
package org.tmatesoft.svn.core.internal.wc;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNCancellableEditor implements ISVNEditor {

    private ISVNEditor myDelegate;
    private ISVNEventHandler myCancel;
    
    public static ISVNEditor newInstance(ISVNEditor editor, ISVNEventHandler cancel) {
        if (cancel != null) {
            return new SVNCancellableEditor(editor, cancel);
        }
        return editor;
    }
    
    private SVNCancellableEditor(ISVNEditor delegate, ISVNEventHandler cancel) {
        myDelegate = delegate;
        myCancel = cancel;
    }

    public void targetRevision(long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.targetRevision(revision);
    }

    public void openRoot(long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.openRoot(revision);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.deleteEntry(path, revision);
    }

    public void absentDir(String path) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.absentDir(path);
    }

    public void absentFile(String path) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.absentFile(path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.addDir(path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.openDir(path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.changeDirProperty(name, value);
    }

    public void closeDir() throws SVNException {
        myCancel.checkCancelled();
        myDelegate.closeDir();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.addFile(path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.openFile(path, revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDelegate.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDelegate.textDeltaEnd(path);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.changeFileProperty(path, name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.closeFile(path, textChecksum);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myCancel.checkCancelled();
        return myDelegate.closeEdit();
    }

    public void abortEdit() throws SVNException {
        myDelegate.abortEdit();
    }

}
