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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaAlgorithm;
import org.tmatesoft.svn.core.internal.delta.SVNVDeltaAlgorithm;
import org.tmatesoft.svn.core.internal.delta.SVNXDeltaAlgorithm;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaGenerator {
    
    private SVNDeltaAlgorithm myXDelta = new SVNXDeltaAlgorithm();
    private SVNDeltaAlgorithm myVDelta = new SVNVDeltaAlgorithm();
    
    private byte[] mySourceBuffer;
    private byte[] myTargetBuffer;
    
    public SVNDeltaGenerator() {
        this(1024*100);
    }
    
    public SVNDeltaGenerator(int maximumDiffWindowSize) {
        mySourceBuffer = new byte[maximumDiffWindowSize];
        myTargetBuffer = new byte[maximumDiffWindowSize];
    }

    public String sendDelta(String path, InputStream target, ISVNEditor consumer, boolean computeChecksum) throws SVNException {
        return sendDelta(path, SVNFileUtil.DUMMY_IN, target, consumer, computeChecksum);
    }
    
    public String sendDelta(String path, InputStream source, InputStream target, ISVNEditor consumer, boolean computeChecksum) throws SVNException {
        MessageDigest digest = null;
        if (computeChecksum) {
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
                return null;
            }
        }
        int sourceOffset = 0;

        while(true) {
            int targetLength;
            int sourceLength;
            try {
                targetLength = target.read(myTargetBuffer, 0, myTargetBuffer.length);
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
                return null;
            }
            if (targetLength <= 0) {
                break;
            }
            try {
                sourceLength = source.read(mySourceBuffer, 0, mySourceBuffer.length);
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
                return null;
            }
            if (sourceLength < 0) {
                sourceLength = 0;
            }
            // update digest,
            if (digest != null) {
                digest.update(myTargetBuffer, 0, targetLength);
            }
            // generate and send window
            sendDelta(path, sourceOffset, mySourceBuffer, sourceLength, myTargetBuffer, targetLength, consumer);
            sourceOffset += sourceLength;
        }
        consumer.textDeltaEnd(path);
        return SVNFileUtil.toHexDigest(digest);
    }
    
    private void sendDelta(String path, int sourceOffset, byte[] source, int sourceLength, byte[] target, int targetLength, ISVNEditor consumer) throws SVNException {
        // use x or v algorithm depending on sourceLength
        SVNDeltaAlgorithm algorithm = sourceLength == 0 ? myVDelta : myXDelta;
        algorithm.computeDelta(source, sourceLength, target, targetLength);
        // send single diff window to the editor.
        SVNDiffInstruction[] instructions = algorithm.getDiffInstructions();        
        ByteArrayOutputStream newData = algorithm.getNewDataStream();
        SVNDiffWindow window = new SVNDiffWindow(sourceOffset, sourceLength, targetLength, instructions, newData.size());
        OutputStream newDataStream = consumer.textDeltaChunk(path, window);
        try {
            newData.writeTo(newDataStream);
        } catch (IOException e) {
            SVNDebugLog.logInfo(e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(newDataStream);
        }
        algorithm.reset();
    }
}
