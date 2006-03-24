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
package org.tmatesoft.svn.core.io.diff;

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
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;


/**
 * The <b>SVNDeltaGenerator</b> is intended for generating diff windows of 
 * fixed size reading two verions of file contents from two 
 * {@link InputStream} streams - source and target ones. The main feature 
 * of this generator is that it uses implementations of native SVN V-Delta 
 * (for binary & new text files) and X-Delta (for calculating changes 
 * between target and source text files) algorithms.    
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaGenerator {
    
    private SVNDeltaAlgorithm myXDelta = new SVNXDeltaAlgorithm();
    private SVNDeltaAlgorithm myVDelta = new SVNVDeltaAlgorithm();
    
    private byte[] mySourceBuffer;
    private byte[] myTargetBuffer;
    
    /**
     * Creates a generator that will produce diff windows of 
     * 100Kbytes contents length. That is, after applying of 
     * such a window you get 100Kbytes of file contents.
     * 
     * @see #SVNDeltaGenerator(int)
     */
    public SVNDeltaGenerator() {
        this(1024*100);
    }
    
    /**
     * Creates a generator that will produce diff windows of 
     * a specified contents length.  
     * 
     * @param maximumDiffWindowSize a maximum size of file contents
     *                              for diff windows to be produced
     */
    public SVNDeltaGenerator(int maximumDiffWindowSize) {
        mySourceBuffer = new byte[maximumDiffWindowSize];
        myTargetBuffer = new byte[maximumDiffWindowSize];
    }
    
    /**
     * Generates a series of diff windows of fixed size comparing 
     * target bytes (from <code>target</code> stream) against an 
     * empty file and sends produced windows to the provided 
     * consumer. <code>consumer</code>'s {@link org.tmatesoft.svn.core.io.ISVNEditor#textDeltaChunk(String, SVNDiffWindow) textDeltaChunk()} 
     * method is called to process generated windows (new data is written to 
     * the output stream returned by that method). 
     * 
     * <p>
     * If <code>computeChecksum</code> is <span class="javakeyword">true</span>, 
     * the return value will be a strig containing a hex representation 
     * of the MD5 digest computed for the target contents. 
     * 
     * @param  path             a file repository path
     * @param  target           an input stream to read target bytes
     *                          from
     * @param  consumer         a diff windows consumer
     * @param  computeChecksum  <span class="javakeyword">true</span> to 
     *                          compute a checksum 
     * @return                  if <code>computeChecksum</code> is <span class="javakeyword">true</span>,  
     *                          a string representing a hex form of the 
     *                          MD5 checksum computed for the target contents; otherwise  <span class="javakeyword">null</span>
     * @throws SVNException
     */
    public String sendDelta(String path, InputStream target, ISVNDeltaConsumer consumer, boolean computeChecksum) throws SVNException {
        return sendDelta(path, SVNFileUtil.DUMMY_IN, 0, target, consumer, computeChecksum);
    }

    /**
     * Generates a series of diff windows of fixed size comparing 
     * target bytes (read from <code>target</code> stream) against source
     * bytes (read from <code>source</code> stream), and sends produced windows to the provided 
     * consumer. <code>consumer</code>'s {@link org.tmatesoft.svn.core.io.ISVNEditor#textDeltaChunk(String, SVNDiffWindow) textDeltaChunk()} 
     * method is called to process generated windows (new data is written to 
     * the output stream returned by that method). 
     * 
     * <p>
     * If <code>computeChecksum</code> is <span class="javakeyword">true</span>, 
     * the return value will be a strig containing a hex representation 
     * of the MD5 digest computed for the target contents. 
     * 
     * @param  path             a file repository path
     * @param  source           an input stream to read source bytes
     *                          from
     * @param  sourceOffset     an offset of the source view in the given <code>source</code> stream 
     * @param  target           an input stream to read target bytes
     *                          from
     * @param  consumer         a diff windows consumer
     * @param  computeChecksum  <span class="javakeyword">true</span> to 
     *                          compute a checksum 
     * @return                  if <code>computeChecksum</code> is <span class="javakeyword">true</span>,  
     *                          a string representing a hex form of the 
     *                          MD5 checksum computed for the target contents; otherwise  <span class="javakeyword">null</span>
     * @throws SVNException
     */
    public String sendDelta(String path, InputStream source, int sourceOffset, InputStream target, ISVNDeltaConsumer consumer, boolean computeChecksum) throws SVNException {
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
        boolean windowSent = false;
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
                // send empty window, needed to create empty file. 
                // only when no windows was sent at all.
                if (!windowSent && consumer != null) {
                    consumer.textDeltaChunk(path, SVNDiffWindow.EMPTY);
                }
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
            windowSent = true;
            sourceOffset += sourceLength;
        }
        if (consumer != null) {
            consumer.textDeltaEnd(path);
        }
        return SVNFileUtil.toHexDigest(digest);
    }

    private void sendDelta(String path, int sourceOffset, byte[] source, int sourceLength, byte[] target, int targetLength, ISVNDeltaConsumer consumer) throws SVNException {
        // use x or v algorithm depending on sourceLength
        SVNDeltaAlgorithm algorithm = sourceLength == 0 ? myVDelta : myXDelta;
        algorithm.computeDelta(source, sourceLength, target, targetLength);
        // send single diff window to the editor.
        if (consumer == null) {
            algorithm.reset();
            return;
        }
        int instructionsLength = algorithm.getInstructionsLength();
        int newDataLength = algorithm.getNewDataLength();
        SVNDiffWindow window = new SVNDiffWindow(sourceOffset, sourceLength, targetLength, instructionsLength, newDataLength);
        window.setData(algorithm.getData());
        OutputStream os = consumer.textDeltaChunk(path, window);
        SVNFileUtil.closeFile(os);
        algorithm.reset();
    }
}
