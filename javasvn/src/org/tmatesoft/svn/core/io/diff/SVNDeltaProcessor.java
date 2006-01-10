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
package org.tmatesoft.svn.core.io.diff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaProcessor {
    
    private SVNDiffWindow myLastWindow;
    private ByteArrayOutputStream myDataStream;
    private SVNDiffWindowApplyBaton myApplyBaton;
    
    public SVNDeltaProcessor() {
        myDataStream = new ByteArrayOutputStream(110*1024);
    }
    
    public void applyTextDelta(InputStream base, OutputStream target, boolean computeCheksum) {
        reset();
        MessageDigest digest = null;
        try {
            digest = computeCheksum ? MessageDigest.getInstance("MD5") : null;
        } catch (NoSuchAlgorithmException e1) {
        }
        base = base == null ? SVNFileUtil.DUMMY_IN : base;
        myApplyBaton = SVNDiffWindowApplyBaton.create(base, target, digest);
    }
    
    public void applyTextDelta(File baseFile, File targetFile, boolean computeCheksum) throws SVNException {
        if (!targetFile.exists()) {
            SVNFileUtil.createEmptyFile(targetFile);
        }
        InputStream base = baseFile != null && baseFile.exists() ? SVNFileUtil.openFileForReading(baseFile) : SVNFileUtil.DUMMY_IN;
        applyTextDelta(base, SVNFileUtil.openFileForWriting(targetFile), computeCheksum);
    }

    public OutputStream textDeltaChunk(SVNDiffWindow window) throws SVNException {
        if (myLastWindow != null) {
            // apply last window.
            myLastWindow.apply(myApplyBaton, new ByteArrayInputStream(myDataStream.toByteArray()));
        }
        myLastWindow = window;
        myDataStream.reset();
        return myDataStream;
    }
    
    private void reset() {
        myDataStream.reset();
        if (myApplyBaton != null) {
            myApplyBaton.close();
            myApplyBaton = null;
        }
        myLastWindow = null;
    }
    
    public String textDeltaEnd() throws SVNException {
        if (myLastWindow != null) {
            myLastWindow.apply(myApplyBaton, new ByteArrayInputStream(myDataStream.toByteArray()));
        }
        myLastWindow = null;
        myDataStream.reset();
        try {
            return myApplyBaton.close();
        } finally { 
            reset();
        }
    }
}
