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
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaChunksGenerator {
    public static final int SVN_DELTA_WINDOW_SIZE = 102400;
    
    private InputStream mySourceStream;
    
    private InputStream myTargetStream;
    
    private ISVNEditor myConsumer;
    
    private String myTargetPath;
    
    private byte[] mySourceBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private byte[] myTargetBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private long mySourceOffset;

    private ISVNDeltaGenerator myDeltaGenerator;
    
    private boolean isSourceDone;
    
    private int myTargetLength;
    
    private int mySourceLength;
    
    private ISVNDiffWindowHandler myHandler;
    
    public SVNDeltaChunksGenerator(InputStream sourceStream, InputStream targetStream, ISVNEditor consumer, String path, File tmpDir) {
        mySourceStream = sourceStream;
        myTargetStream = targetStream;
        myConsumer = consumer;
        myTargetPath = path;
        mySourceOffset = 0;
        isSourceDone = false;
        myDeltaGenerator = new SVNSequenceDeltaGenerator(tmpDir);
    }

    public SVNDeltaChunksGenerator(InputStream sourceStream, ISVNDiffWindowHandler handler, File tmpDir) {
        mySourceStream = sourceStream;
        mySourceOffset = 0;
        isSourceDone = false;
        myTargetLength = 0;
        mySourceLength = 0;
        myDeltaGenerator = new SVNSequenceDeltaGenerator(tmpDir);
        myHandler = handler;
    }
    
    public void sendWindows() throws SVNException {
        //runs loop until target runs out of text 
        while(generateNextWindowFromStreams()){
            ;
        }
    }

    private boolean generateNextWindowFromStreams() throws SVNException {
        int sourceLength = 0;
        int targetLength = 0;
        try{
            if(!isSourceDone){
                /* Read the source stream. */
                sourceLength = mySourceStream.read(mySourceBuf);
                if(sourceLength < SVN_DELTA_WINDOW_SIZE){
                    isSourceDone = true;
                }
            }
            /* Read the target stream. */
            targetLength = myTargetStream.read(myTargetBuf);
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        sourceLength = sourceLength == -1 ? 0 : sourceLength;
        targetLength = targetLength == -1 ? 0 : targetLength;
        mySourceOffset += sourceLength;
        if(targetLength == 0){
            myConsumer.textDeltaEnd(myTargetPath);
            return false;
        }
        ISVNRAData sourceData = new SVNRABufferData(mySourceBuf, sourceLength);
        ISVNRAData targetData = new SVNRABufferData(myTargetBuf, targetLength);
        myDeltaGenerator.generateNextDiffWindow(myTargetPath, myConsumer, targetData, sourceData, mySourceOffset - sourceLength);
        return true;
    }
    
    public void makeDiffWindowFromData(byte[] data, int dataLength) throws IOException, SVNException {
        if(data == null){
            return;
        }
        while(dataLength > 0){
            /* Make sure we're all full up on source data, if possible. */
            if(mySourceLength == 0 && !isSourceDone){
                mySourceLength = mySourceStream.read(mySourceBuf);
                mySourceLength = mySourceLength < 0 ? 0 : mySourceLength;
                if(mySourceLength < SVN_DELTA_WINDOW_SIZE){
                    isSourceDone = true;
                }
            }
            /* Copy in the target data, up to SVN_DELTA_WINDOW_SIZE. */
            int chunkLength = SVN_DELTA_WINDOW_SIZE - myTargetLength;
            if(chunkLength > dataLength){
                chunkLength = dataLength;
            }
            System.arraycopy(data, 0, myTargetBuf, myTargetLength, chunkLength);
            dataLength -= chunkLength;
            myTargetLength += chunkLength;
            /* If we're full of target data, compute and fire off a window. */
            if(myTargetLength == SVN_DELTA_WINDOW_SIZE){
                flushNextWindow();
                mySourceOffset += mySourceLength;
                mySourceLength = 0;
                myTargetLength = 0;
            }
        }
    }
    
    public void flushNextWindow() throws SVNException {
        if(myTargetLength > 0){
            ISVNRAData sourceData = new SVNRABufferData(mySourceBuf, mySourceLength);
            ISVNRAData targetData = new SVNRABufferData(myTargetBuf, myTargetLength);
            OutputStream newData = myHandler.getNewDataStream();
            SVNDiffWindow window = myDeltaGenerator.generateNextDiffWindow(targetData, sourceData, mySourceOffset, newData);
            myHandler.handleDiffWindow(window);
        }
    }
}
