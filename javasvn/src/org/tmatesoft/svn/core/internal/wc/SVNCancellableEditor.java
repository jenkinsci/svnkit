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
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.util.SVNDebugLog;


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
        SVNDebugLog.logInfo("root");
        myDelegate.openRoot(revision);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("del " + path);
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
        SVNDebugLog.logInfo("add dir " + path);
        myDelegate.addDir(path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("open dir " + path);
        myDelegate.openDir(path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("change dir prop " + name + " : " + value);
        myDelegate.changeDirProperty(name, value);
    }

    public void closeDir() throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("close dir");
        myDelegate.closeDir();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("add file " + path);
        myDelegate.addFile(path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("open file " + path);
        myDelegate.openFile(path, revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("apply delta" + path);
        myDelegate.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        SVNDebugLog.logInfo("delta chunk" + path);
        return myDelegate.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        SVNDebugLog.logInfo("delta end " + path);
        myDelegate.textDeltaEnd(path);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("change file prop " + name + " = " + value);
        myDelegate.changeFileProperty(path, name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("close file " + path);
        myDelegate.closeFile(path, textChecksum);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myCancel.checkCancelled();
        SVNDebugLog.logInfo("close edit");
        return myDelegate.closeEdit();
    }

    public void abortEdit() throws SVNException {
        SVNDebugLog.logInfo("abort edit");
        myDelegate.abortEdit();
    }

}
