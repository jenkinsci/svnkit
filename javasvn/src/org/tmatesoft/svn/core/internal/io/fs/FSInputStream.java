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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSInputStream extends InputStream {
    /* The state of all prior delta representations. */
    private LinkedList myRepStateList = new LinkedList();

    /* The index of the current delta chunk, if we are reading a delta. */
    private int myChunkIndex;
    
    private boolean isChecksumFinalized;
    
    private String myHexChecksum;
    private long myLength;
    private long myOffset;
    
    private FSRepresentationState mySourceState;
    private SVNDiffWindowBuilder myDiffWindowBuilder = SVNDiffWindowBuilder.newInstance();
    private MessageDigest myDigest;
    private byte[] myBuffer;
    private int myBufPos = 0;
    
    private FSInputStream(FSRepresentation representation, FSFS owner) throws SVNException {
        myChunkIndex = 0;
        isChecksumFinalized = false;
        myHexChecksum = representation.getHexDigest();
        myOffset = 0;
        myLength = representation.getExpandedSize();
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
        try{
            mySourceState = FSRepresentationState.buildRepresentationList(representation, myRepStateList, owner);
        }catch(SVNException svne){
            /* Something terrible has happened while building rep list, 
             * need to close any files still opened 
             */
            close();
            throw svne;
        }
    }
    
    public static InputStream createDeltaStream(FSRevisionNode fileNode, FSFS owner) throws SVNException {
        if(fileNode == null){
            return SVNFileUtil.DUMMY_IN;
        }else if (fileNode.getType() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get textual contents of a *non*-file node");
            SVNErrorManager.error(err);
        }
        FSRepresentation representation = fileNode.getTextRepresentation(); 
        if(representation == null){
            return SVNFileUtil.DUMMY_IN; 
        }
        return new FSInputStream(representation, owner);
    }

    public static InputStream createDeltaStream(FSRepresentation fileRep, FSFS owner) throws SVNException {
        if(fileRep == null){
            return SVNFileUtil.DUMMY_IN;
        }
        return new FSInputStream(fileRep, owner);
    }

    //to read plain text
    public static InputStream createPlainStream(FSRepresentation representation, FSFS owner) throws SVNException {
        if(representation == null){
            return SVNFileUtil.DUMMY_IN; 
        }
        return new FSInputStream(representation, owner);
    }
    
    public int read(byte[] buf) throws IOException {
        try{
            int r = readContents(buf); 
            return r == 0 ? -1 : r;
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }
    }
    
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int r = 0;
        try{
            r = readContents(buf);
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }
        return r == 0 ? -1 : (int)(buf[0] & 0xFF);
    }
    
    private int readContents(byte[] buf) throws SVNException {
        /* Get the next block of data. */
        int length = getContents(buf);
        /* Perform checksumming.  We want to check the checksum as soon as
         * the last byte of data is read, in case the caller never performs
         * a short read, but we don't want to finalize the MD5 context
         * twice. 
         */
        if(!isChecksumFinalized){
            myDigest.update(buf, 0, length);
            myOffset += length;
            if(myOffset == myLength){
                isChecksumFinalized = true;
                String hexDigest = SVNFileUtil.toHexDigest(myDigest);
                // Compare read and expected checksums
                if (!myHexChecksum.equals(hexDigest)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[]{myHexChecksum, hexDigest});
                    SVNErrorManager.error(err);
                }
            }
        }
        return length;
    }
    
    private int getContents(byte[] buffer) throws SVNException {
        int remaining = buffer.length;
        int targetPos = 0;
        /* Special case for when there are no delta reps, only a plain
         * text. 
         */
        if(myRepStateList.isEmpty()){
            int copyLength = remaining > mySourceState.end - mySourceState.offset ? (int)(mySourceState.end - mySourceState.offset) : remaining;
            int r = copyLength;
            try{
                ByteBuffer bBuffer = ByteBuffer.wrap(buffer, 0, copyLength);
                r = mySourceState.file.read(bBuffer);
            }catch(IOException ioe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
            mySourceState.offset += r;
            return r;
        }
        while(remaining > 0){
            if(myBuffer != null){
                //copy bytes to buffer and mobe the bufPos pointer
                /* Determine how much to copy from the buffer. */
                int copyLength = myBuffer.length - myBufPos;
                if(copyLength > remaining){
                    copyLength = remaining;
                }
                /* Actually copy the data. */
                System.arraycopy(myBuffer, myBufPos, buffer, targetPos, copyLength);
                myBufPos += copyLength;
                targetPos += copyLength;
                remaining -= copyLength;
                /* If the buffer is all used up, clear it. 
                 */
                if(myBufPos == myBuffer.length){
                    myBuffer = null;
                    myBufPos = 0;
                }
            }else{
                FSRepresentationState resultState = (FSRepresentationState)myRepStateList.getFirst();
                if(resultState.offset == resultState.end){
                    break;
                }
                int startIndex = 0;
                for(ListIterator states = myRepStateList.listIterator(); states.hasNext();){
                    FSRepresentationState curState = (FSRepresentationState)states.next();

                    try{
                        /* Skip windows to reach the current chunk if we aren't there yet. */
                        while(curState.chunkIndex < myChunkIndex){
                            skipDiffWindow(curState.file);
                            curState.chunkIndex++;
                            curState.offset = curState.file.position();
                            if(curState.offset >= curState.end){
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Reading one svndiff window read beyond the end of the representation");
                                SVNErrorManager.error(err);
                            }
                        }
                    }catch(IOException ioe){
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                        SVNErrorManager.error(err, ioe);
                    }

                    startIndex = myRepStateList.indexOf(curState);
                    myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
                    try{
                        long currentPos = curState.file.position();
                        myDiffWindowBuilder.accept(curState.file);
                        //go back to the beginning of the window's offsets section
                        curState.file.seek(currentPos);
                    }catch(IOException ioe){
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                        SVNErrorManager.error(err, ioe);
                    }
                    SVNDiffWindow window = myDiffWindowBuilder.getDiffWindow();
                    // TODO make window report whether it has cp_from_src instructions or not.
                    SVNDiffInstruction[] instructions = SVNDiffWindowBuilder.createInstructions(window.getInstructionsData());
                    boolean hasCopiesFromSource = false;
                    for(int i = 0; !hasCopiesFromSource && i < instructions.length; i++){
                        if(instructions[i].type == SVNDiffInstruction.COPY_FROM_SOURCE) {
                            hasCopiesFromSource = true;
                        }
                    }
                    if(!hasCopiesFromSource){
                        break;
                    }
                }
                myBuffer = getNextTextChunk(startIndex);
            }
        }
        return targetPos;
    }
    
    private byte[] getNextTextChunk(int startIndex) throws SVNException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] targetView = null;
        byte[] sourceView = null;
        for(ListIterator states = myRepStateList.listIterator(startIndex + 1); states.hasPrevious();){
            FSRepresentationState state = (FSRepresentationState)states.previous();
            data.reset();
            SVNDiffWindow window = null;
            try{
                window = readWindow(state, myChunkIndex, data);
            }catch(IOException ioe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
            targetView = window.apply(sourceView, data.toByteArray());
            if(states.hasPrevious()){
                sourceView = targetView;
            }
        }
        myChunkIndex++;
        return targetView;
    }

    /* Skip forwards to thisChunk in rep state and then read the next delta
     * window. 
     */
    private SVNDiffWindow readWindow(FSRepresentationState state, int thisChunk, OutputStream dataBuf) throws SVNException, IOException {
        //assertion
        if(state.chunkIndex > thisChunk){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Fatal error while reading diff windows");
            SVNErrorManager.error(err);
        }
        /* Skip windows to reach the current chunk if we aren't there yet. */
        while(state.chunkIndex < thisChunk){
            skipDiffWindow(state.file);
            state.chunkIndex++;
            state.offset = state.file.position();
            if(state.offset >= state.end){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Reading one svndiff window read beyond the end of the representation");
                SVNErrorManager.error(err);
            }
        }
        /* Read the next window. */
        myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
        myDiffWindowBuilder.accept(state.file);
        SVNDiffWindow window = myDiffWindowBuilder.getDiffWindow();
        long length = window.getNewDataLength();
        byte[] buffer = new byte[(int)length];
        ByteBuffer bBuffer = ByteBuffer.wrap(buffer, 0, (int) length);
        int read = state.file.read(bBuffer);
        if(read < length){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_UNEXPECTED_END, "Unexpected end of svndiff input");
            SVNErrorManager.error(err);
        }
        state.chunkIndex++;
        state.offset = state.file.position();
        if(state.offset > state.end){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Reading one svndiff window read beyond the end of the representation");
            SVNErrorManager.error(err);
        }
        dataBuf.write(buffer);
        return window;
    }
    
    private void skipDiffWindow(FSFile file) throws IOException, SVNException {
        myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
        myDiffWindowBuilder.accept(file);
        SVNDiffWindow window = myDiffWindowBuilder.getDiffWindow();
        long len = window.getNewDataLength();
        long curPos = file.position();
        file.seek(curPos + len);
    }
    
    public void close() {
        for(Iterator states = myRepStateList.iterator(); states.hasNext();){
            FSRepresentationState state = (FSRepresentationState)states.next();
            state.file.close();
            //SVNFileUtil.closeFile(state.file);
            states.remove();
        }
        if(mySourceState != null){
            mySourceState.file.close();
            //SVNFileUtil.closeFile(mySourceState.file);
        }
    }
}
