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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaProcessor {
    
    private String myChecksum;
    
    private SVNDiffWindow myLastWindow;
    private ByteArrayOutputStream myDataStream;
    private SVNDiffWindowApplyBaton myApplyBaton;
    
    public SVNDeltaProcessor() {
        myDataStream = new ByteArrayOutputStream(110*1024);
    }
    
    public void applyTextDelta(File baseFile, File targetFile, boolean computeCheksum) throws SVNException {
        MessageDigest digest = null;
        try {
            digest = computeCheksum ? MessageDigest.getInstance("MD5") : null;
        } catch (NoSuchAlgorithmException e1) {
        }
        if (!targetFile.exists()) {
            SVNFileUtil.createEmptyFile(targetFile);
        }
        myChecksum = null;
        myDataStream.reset();
        if (baseFile != null) {
            myApplyBaton = SVNDiffWindowApplyBaton.create(baseFile, targetFile, digest);
        } else {
            myApplyBaton = SVNDiffWindowApplyBaton.create(SVNFileUtil.DUMMY_IN, SVNFileUtil.openFileForWriting(targetFile), digest);
        }
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
    
    public String getChecksum() {
        return myChecksum;
    }
    
    public boolean textDeltaEnd() throws SVNException {
        boolean result = false;
        if (myLastWindow != null) {
            // apply last window.
            result = true;
            myLastWindow.apply(myApplyBaton, new ByteArrayInputStream(myDataStream.toByteArray()));
        }
        myLastWindow = null;
        myDataStream.reset();
        close();
        return result;
    }
    
    public void close() {
        if (myApplyBaton != null) {
            myChecksum = myApplyBaton.close();
            myApplyBaton = null;
        }
    }
}
