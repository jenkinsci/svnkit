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

package org.tmatesoft.svn.core;

import java.io.InputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public interface ISVNFileEntry extends ISVNEntry {

    /**
     * Applies delta to the base version of the file. Merge method will be
     * called later to merge changes into the actual version.
     * 
     * @param window
     *            delta object
     * @param newData
     *            data referenced by the delta object
     * @param overwrite
     *            actual contents immideatly if true
     * 
     */
    public void applyDelta(SVNDiffWindow window, InputStream newData,
            boolean overwrite) throws SVNException;

    public int deltaApplied(boolean overwrite) throws SVNException;

    public void restoreContents() throws SVNException;

    public void markResolved(boolean contentsOnly) throws SVNException;

    /**
     * Sends delta between base and actual versions to the editor.
     * 
     * @param editor
     *            editor to send delta to
     */
    public String generateDelta(String commitPath, ISVNEditor editor)
            throws SVNException;

    public boolean isContentsModified() throws SVNException;

    public boolean isCorrupted() throws SVNException;
}
