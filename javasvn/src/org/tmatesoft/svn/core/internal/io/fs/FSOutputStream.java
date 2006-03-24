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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSOutputStream extends FSBufferStream implements ISVNDeltaConsumer {
    
    public static final int WRITE_BUFFER_SIZE = 512000;
    public static final int SVN_DELTA_WINDOW_SIZE = 102400;

    private boolean isHeaderWritten;
    private CountingStream myTargetFile;    
    private long myDeltaStart;
    private long myRepSize;    
    private long myRepOffset;    
    private InputStream mySourceStream;    
    private SVNDeltaGenerator myDeltaGenerator;    
    private FSRevisionNode myRevNode;    
    private MessageDigest myDigest;    
    private FSTransactionRoot myTxnRoot;    
    private long mySourceOffset;
    private boolean isSourceDone;    
    private int myTargetLength;    
    private int mySourceLength;

    private byte[] mySourceBuf = new byte[SVN_DELTA_WINDOW_SIZE];    
    private byte[] myTargetBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private boolean myIsClosed;
    
    private FSOutputStream(FSRevisionNode revNode, CountingStream file, InputStream source, long deltaStart, long repSize, long repOffset, FSTransactionRoot txnRoot) throws SVNException {
        myTxnRoot = txnRoot;
        myTargetFile = file;
        mySourceStream = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset; 
        isHeaderWritten = false;
        myDeltaGenerator = new SVNDeltaGenerator(SVN_DELTA_WINDOW_SIZE);
        myRevNode = revNode;
        mySourceOffset = 0;
        isSourceDone = false;
        myTargetLength = 0;
        mySourceLength = 0;
        myIsClosed = false;
        
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
    }
    
    private void reset(FSRevisionNode revNode, CountingStream file, InputStream source, long deltaStart, long repSize, long repOffset, FSTransactionRoot txnRoot) {
        super.myBufferLength = 0;
        super.myBuffer = null;
        myTxnRoot = txnRoot;
        myTargetFile = file;
        mySourceStream = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset; 
        isHeaderWritten = false;
        myRevNode = revNode;
        mySourceOffset = 0;
        isSourceDone = false;
        myTargetLength = 0;
        mySourceLength = 0;
        myIsClosed = false;
        myDigest.reset();
    }
    
    public static OutputStream createStream(FSRevisionNode revNode, FSTransactionRoot txnRoot, FSOutputStream dstStream) throws SVNException {
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to set textual contents of a *non*-file node");
            SVNErrorManager.error(err);
        }

        if(!revNode.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to set textual contents of an immutable node");
            SVNErrorManager.error(err);
        }
        
        OutputStream targetOS = null;
        InputStream sourceStream = null;
        long offset = -1;
        long deltaStart = -1;
        try{
            File targetFile = txnRoot.getTransactionRevFile(); 
            offset = targetFile.length();
            targetOS = SVNFileUtil.openFileForWriting(targetFile, true);
            CountingStream revWriter = new CountingStream(targetOS, offset);
            
            FSRepresentation baseRep = revNode.chooseDeltaBase(txnRoot.getOwner());
            sourceStream = FSInputStream.createDeltaStream(new SVNDeltaCombiner(), baseRep, txnRoot.getOwner());
            String header;
            
            if(baseRep != null){
                header = FSConstants.REP_DELTA + " " + baseRep.getRevision() + " " + baseRep.getOffset() + " " + baseRep.getSize() + "\n"; 
            }else{
                header = FSConstants.REP_DELTA + "\n";
            }

            revWriter.write(header.getBytes());
            deltaStart = revWriter.getPosition();
            
            if(dstStream == null){
                return new FSOutputStream(revNode, revWriter, sourceStream, deltaStart, 0, offset, txnRoot);
            }
            
            dstStream.reset(revNode, revWriter, sourceStream, deltaStart, 0, offset, txnRoot);
            return dstStream;
        }catch(IOException ioe){
            SVNFileUtil.closeFile(targetOS);
            SVNFileUtil.closeFile(sourceStream);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(targetOS);
            SVNFileUtil.closeFile(sourceStream);
            throw svne;
        }
        return null;
    }
    
    public void write(int b) throws IOException{
        super.write(b);
        myDigest.update((byte)b);
        myRepSize++; 
        if(super.myBufferLength > WRITE_BUFFER_SIZE){
            try{
                makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            }catch(SVNException svne){
                throw new IOException(svne.getMessage());
            }
            super.myBufferLength = 0;
            super.myBuffer = null;
        }
    }
    
    public void write(byte[] b) throws IOException{
        write(b, 0, b.length);
    }
    
    public void write(byte[] b, int off, int len) throws IOException{
        super.write(b, off, len);
        myDigest.update(b, off, len);
        myRepSize += len;
        if(super.myBufferLength > WRITE_BUFFER_SIZE){
            try{
                makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            }catch(SVNException svne){
                throw new IOException(svne.getMessage());
            }
            super.myBufferLength = 0;
            super.myBuffer = null;
        }
    }

    public void close() throws IOException {
        if(myIsClosed){
            return;
        }
        
        myIsClosed = true;
        
        try{
            makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            flushNextWindow();
            
            super.myBufferLength = 0;
            super.myBuffer = null;
            
            FSRepresentation rep = new FSRepresentation();
            rep.setOffset(myRepOffset);
            
            long offset = myTargetFile.getPosition();
            
            rep.setSize(offset - myDeltaStart);
            rep.setExpandedSize(myRepSize);
            rep.setTxnId(myRevNode.getId().getTxnID());
            rep.setRevision(FSConstants.SVN_INVALID_REVNUM);
            
            rep.setHexDigest(SVNFileUtil.toHexDigest(myDigest));
            
            myTargetFile.write("ENDREP\n".getBytes());
            myRevNode.setTextRepresentation(rep);

            myTxnRoot.getOwner().putTxnRevisionNode(myRevNode.getId(), myRevNode);
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }finally{
            closeStreams();
        }
    }
    
    public void closeStreams(){
        SVNFileUtil.closeFile(myTargetFile);
        SVNFileUtil.closeFile(mySourceStream);
    }
    
    public FSRevisionNode getRevisionNode(){
        return myRevNode;
    }
    
    private void writeDiffWindow(SVNDiffWindow window) throws SVNException {
        try {
            window.writeTo(myTargetFile, !isHeaderWritten);
            isHeaderWritten = true;
        } catch(IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
    
    private void makeDiffWindowFromData(byte[] data, int dataLength) throws IOException, SVNException {
        if(data == null) {
            return;
        }
        int dataPos = 0;
        while(dataLength > 0) {
            if(mySourceLength == 0 && !isSourceDone) {
                mySourceLength = mySourceStream.read(mySourceBuf);
                mySourceLength = mySourceLength < 0 ? 0 : mySourceLength;
                if(mySourceLength < SVN_DELTA_WINDOW_SIZE){
                    isSourceDone = true;
                }
            }
            int chunkLength = SVN_DELTA_WINDOW_SIZE - myTargetLength;
            if(chunkLength > dataLength) {
                chunkLength = dataLength;
            }
            System.arraycopy(data, dataPos, myTargetBuf, myTargetLength, chunkLength);
            dataPos += chunkLength;
            dataLength -= chunkLength;
            myTargetLength += chunkLength;
            if(myTargetLength == SVN_DELTA_WINDOW_SIZE){
                flushNextWindow();
                mySourceOffset += mySourceLength;
                mySourceLength = 0;
                myTargetLength = 0;
            }
        }
    }
    
    private void flushNextWindow() throws SVNException {
        ByteArrayInputStream sourceData = new ByteArrayInputStream(mySourceBuf, 0, mySourceLength);
        ByteArrayInputStream targetData = new ByteArrayInputStream(myTargetBuf, 0, myTargetLength);
        myDeltaGenerator.sendDelta(null, sourceData, (int)mySourceOffset, targetData, this, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException{
        writeDiffWindow(diffWindow);
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException{
    }
    public void applyTextDelta(String path, String baseChecksum) throws SVNException{
    }
}
