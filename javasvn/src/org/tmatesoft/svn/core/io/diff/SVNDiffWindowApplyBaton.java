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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffWindowApplyBaton {
    
    InputStream mySourceStream;
    OutputStream myTargetStream;
    
    long mySourceViewOffset;
    long mySourceViewLength;
    long myTargetViewSize;
    
    byte[] mySourceBuffer;
    byte[] myTargetBuffer;
    MessageDigest myDigest;
    
    public static SVNDiffWindowApplyBaton create(File source, File target, MessageDigest digest) throws SVNException {
        SVNDiffWindowApplyBaton baton = new SVNDiffWindowApplyBaton();
        baton.mySourceStream = source.exists() ? SVNFileUtil.openFileForReading(source) : SVNFileUtil.DUMMY_IN;
        baton.myTargetStream = SVNFileUtil.openFileForWriting(target, true);
        baton.mySourceBuffer = new byte[0];
        baton.mySourceViewLength = 0;
        baton.mySourceViewOffset = 0;
        baton.myDigest = digest;
        return baton;
    }
    
    private SVNDiffWindowApplyBaton() {
    }

    public String close() {
        SVNFileUtil.closeFile(mySourceStream);
        SVNFileUtil.closeFile(myTargetStream);
        if (myDigest != null) {
            return SVNFileUtil.toHexDigest(myDigest);
        }
        return null;
    }

}
