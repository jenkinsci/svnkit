/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNRemoteStatusEditor17 extends SVNStatusEditor17 implements ISVNEditor, ISVNStatusHandler {

    public SVNRemoteStatusEditor17(File targetAbsPath, SVNWCContext wcContext, ISVNOptions options, boolean includeIgnored, boolean reportAll, SVNDepth depth, SVNExternalsStore externalsStore, ISVNStatusHandler realHandler) {
        super(targetAbsPath, wcContext, options, includeIgnored, reportAll, depth, externalsStore ,realHandler);
    }

    public void abortEdit() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void absentDir(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void absentFile(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void closeDir() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void openDir(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void openFile(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void openRoot(long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void textDeltaEnd(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void handleStatus(SVNStatus status) throws SVNException {
        throw new UnsupportedOperationException();
    }

}
