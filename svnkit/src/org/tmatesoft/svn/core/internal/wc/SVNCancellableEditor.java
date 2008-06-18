/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;



/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNCancellableEditor implements ISVNEditor {

    private ISVNEditor myDelegate;
    private ISVNEventHandler myCancel;
    private ISVNDebugLog myLog;
    
    public static ISVNEditor newInstance(ISVNEditor editor, ISVNEventHandler cancel, ISVNDebugLog log) {
        if (cancel != null) {
            return new SVNCancellableEditor(editor, cancel, log);
        }
        return editor;
    }
    
    private SVNCancellableEditor(ISVNEditor delegate, ISVNEventHandler cancel, ISVNDebugLog log) {
        myDelegate = delegate;
        myCancel = cancel;
        myLog = log == null ? SVNDebugLog.getDefaultLog() : log;
    }

    public void targetRevision(long revision) throws SVNException {
        myCancel.checkCancelled();
        myDelegate.targetRevision(revision);
    }

    public void openRoot(long revision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("root");
        myDelegate.openRoot(revision);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("del " + path);
        myDelegate.deleteEntry(path, revision);
    }

    public void absentDir(String path) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("absent dir " + path);
        myDelegate.absentDir(path);
    }

    public void absentFile(String path) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("absent file " + path);
        myDelegate.absentFile(path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("add dir " + path);
        myDelegate.addDir(path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("open dir " + path);
        myDelegate.openDir(path, revision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("change dir prop " + name + " = " + SVNPropertyValue.getPropertyAsString(value));
        myDelegate.changeDirProperty(name, value);
    }

    public void closeDir() throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("close dir");
        myDelegate.closeDir();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("add file " + path);
        myDelegate.addFile(path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("open file " + path);
        myDelegate.openFile(path, revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("apply delta " + path);
        myDelegate.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        myLog.logInfo("delta chunk " + path);
        return myDelegate.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myLog.logInfo("delta end " + path);
        myDelegate.textDeltaEnd(path);
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("change file prop " + name + " = " + SVNPropertyValue.getPropertyAsString(value));
        myDelegate.changeFileProperty(path, name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("close file " + path);
        myDelegate.closeFile(path, textChecksum);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myCancel.checkCancelled();
        myLog.logInfo("close edit");
        return myDelegate.closeEdit();
    }

    public void abortEdit() throws SVNException {
        myLog.logInfo("abort edit");
        myDelegate.abortEdit();
    }

}
