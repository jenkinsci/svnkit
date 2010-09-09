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
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNUpdateEditor17 implements ISVNUpdateEditor {

    public static ISVNUpdateEditor createUpdateEditor(SVNWCContext wcContext, File anchorAbspath, String target, SVNURL reposRoot, Object object, SVNExternalsStore externalsStore, boolean allowUnversionedObstructions,
            boolean depthIsSticky, SVNDepth depth, String[] preservedExts, ISVNFileFetcher fileFetcher, boolean updateLocksOnDemand) {
        return null;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
    }

    public void closeDir() throws SVNException {
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public long getTargetRevision() {
        return 0;
    }

}
