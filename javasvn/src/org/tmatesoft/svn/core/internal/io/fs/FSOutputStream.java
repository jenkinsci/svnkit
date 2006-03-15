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

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSOutputStream extends FSBufferStream implements ISVNDeltaConsumer {
    
    public static final int WRITE_BUFFER_SIZE = 512000;

    public static final int SVN_DELTA_WINDOW_SIZE = 102400;

    private boolean isHeaderWritten;

    /* Actual file to which we are writing. */
    private RandomAccessFile myTargetFile;
    
    /* Start of the actual data. */
    private long myDeltaStart;

    /* How many bytes have been written to this rep already. */
    private long myRepSize;
    
    /* Where is this representation header stored. */
    private long myRepOffset;
    
    private InputStream mySourceStream;
    
    private SVNDeltaGenerator myDeltaGenerator;
    
    private FSBufferStream myNewDataStream;
    
    private FSRevisionNode myRevNode;
    
    private MessageDigest myDigest;
    
    private File myReposRootDir;
    
    private long mySourceOffset;

    private boolean isSourceDone;
    
    private int myTargetLength;
    
    private int mySourceLength;

    private byte[] mySourceBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private byte[] myTargetBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private SVNDiffWindow myLastWindow;
    
    private FSOutputStream(FSRevisionNode revNode, RandomAccessFile file, InputStream source, long deltaStart, long repSize, long repOffset, File reposRootDir) throws SVNException {
        super();
        myReposRootDir = reposRootDir;
        myTargetFile = file;
        mySourceStream = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset; 
        isHeaderWritten = false;
        myDeltaGenerator = new SVNDeltaGenerator(SVN_DELTA_WINDOW_SIZE);
        myNewDataStream = new FSBufferStream();
        myRevNode = revNode;
        mySourceOffset = 0;
        isSourceDone = false;
        myTargetLength = 0;
        mySourceLength = 0;
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
    }
    
    /*
     * revNode must be mutable!
     */
    public static OutputStream createStream(FSRevisionNode revNode, String txnId, File reposRootDir) throws SVNException {
        /* Make sure our node is a file. */
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to set textual contents of a *non*-file node");
            SVNErrorManager.error(err);
        }
        /* Make sure our node is mutable. */
        if(!revNode.getId().isTxn()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to set textual contents of an immutable node");
            SVNErrorManager.error(err);
        }
        RandomAccessFile targetFile = null;
        InputStream sourceStream = null;
        long offset = -1;
        long deltaStart = -1;
        try{
            /* Open the prototype rev file and seek to its end. */
            txnId = revNode.getId().getTxnID();
            targetFile = SVNFileUtil.openRAFileForWriting(FSRepositoryUtil.getTxnRevFile(txnId, reposRootDir), true);
            offset = targetFile.getFilePointer();
            /* Get the base for this delta. */
            FSRepresentation baseRep = chooseDeltaBase(revNode, reposRootDir);
            sourceStream = FSInputStream.createDeltaStream(baseRep, new FSFS(reposRootDir));
            /* Write out the rep header. */
            String header;
            if(baseRep != null){
                header = FSConstants.REP_DELTA + " " + baseRep.getRevision() + " " + baseRep.getOffset() + " " + baseRep.getSize() + "\n"; 
            }else{
                header = FSConstants.REP_DELTA + "\n";
            }
            targetFile.write(header.getBytes());
            /* Now determine the offset of the actual svndiff data. */
            deltaStart = targetFile.getFilePointer();
            return new FSOutputStream(revNode, targetFile, sourceStream, deltaStart, 0, offset, reposRootDir);
        }catch(IOException ioe){
            SVNFileUtil.closeFile(targetFile);
            SVNFileUtil.closeFile(sourceStream);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(targetFile);
            SVNFileUtil.closeFile(sourceStream);
            throw svne;
        }
        return null;
    }
    
    private static FSRepresentation chooseDeltaBase(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        /* If we have no predecessors, then use the empty stream as a
         * base. 
         */
        if(revNode.getCount() == 0){
            return null;
        }
        /* Flip the rightmost '1' bit of the predecessor count to determine
         * which file rev (counting from 0) we want to use.  (To see why
         * count & (count - 1) unsets the rightmost set bit, think about how
         * you decrement a binary number.) 
         */
        long count = revNode.getCount();
        count = count & (count - 1);
        /* Walk back a number of predecessors equal to the difference
         * between count and the original predecessor count.  (For example,
         * if noderev has ten predecessors and we want the eighth file rev,
         * walk back two predecessors.) 
         */
        FSRevisionNode baseNode = revNode;
        while((count++) < revNode.getCount()){
            baseNode = FSReader.getRevNodeFromID(reposRootDir, baseNode.getId());
        }
        return baseNode.getTextRepresentation(); 
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
        super.write(b);
        myDigest.update(b);
        myRepSize += b.length;
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
        try{
            //flush all data that is still remaining
            makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            flushNextWindow();
            super.myBufferLength = 0;
            super.myBuffer = null;
            FSRepresentation rep = new FSRepresentation();
            rep.setOffset(myRepOffset);
            /* Determine the length of the svndiff data. */
            long offset = myTargetFile.getFilePointer();
            rep.setSize(offset - myDeltaStart);
            /* Fill in the rest of the representation field. */
            rep.setExpandedSize(myRepSize);
            rep.setTxnId(myRevNode.getId().getTxnID());
            rep.setRevision(FSConstants.SVN_INVALID_REVNUM);
            /* Finalize the MD5 checksum. */
            rep.setHexDigest(SVNFileUtil.toHexDigest(myDigest));
            /* Write out their cosmetic end marker. */
            myTargetFile.write("ENDREP\n".getBytes());
            myRevNode.setTextRepresentation(rep);
            /* Write out the new node-rev information. */
            FSWriter.putTxnRevisionNode(myRevNode.getId(), myRevNode, myReposRootDir);
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }finally{
            closeStreams();
        }
    }
    
    public void closeStreams(){
        /* We're done; clean up. */
        SVNFileUtil.closeFile(myTargetFile);
        SVNFileUtil.closeFile(mySourceStream);
    }
    
    public FSRevisionNode getRevisionNode(){
        return myRevNode;
    }
    
    public OutputStream getNewDataStream(){
        myNewDataStream.myBufferLength = 0;
        return myNewDataStream;
    }
    
    private void writeDiffWindow(SVNDiffWindow window) throws SVNException {
        try{
            /* Make sure we write the header.  */
            SVNDiffWindowBuilder.save(window, !isHeaderWritten, myTargetFile);
            if(!isHeaderWritten){
                isHeaderWritten = true;
            }
            myTargetFile.write(myNewDataStream.myBuffer, 0, myNewDataStream.myBufferLength);
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
    
    private void makeDiffWindowFromData(byte[] data, int dataLength) throws IOException, SVNException {
        if(data == null){
            return;
        }
        int dataPos = 0;
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
            System.arraycopy(data, dataPos, myTargetBuf, myTargetLength, chunkLength);
            dataPos += chunkLength;
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
    
    private void flushNextWindow() throws SVNException {
        ByteArrayInputStream sourceData = new ByteArrayInputStream(mySourceBuf, 0, mySourceLength);
        ByteArrayInputStream targetData = new ByteArrayInputStream(myTargetBuf, 0, myTargetLength);
        myDeltaGenerator.sendDelta(null, sourceData, (int)mySourceOffset, targetData, this, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException{
        if(myLastWindow != null){
            writeDiffWindow(myLastWindow);
        }
        myLastWindow = diffWindow;
        myNewDataStream.myBufferLength = 0;
        return myNewDataStream;

    }

    public void textDeltaEnd(String path) throws SVNException{
        if(myLastWindow != null){
            writeDiffWindow(myLastWindow);
        }
        myNewDataStream.myBufferLength = 0;
    }

    //unnecessary methods 
    public void applyTextDelta(String path, String baseChecksum) throws SVNException{
    }
}
