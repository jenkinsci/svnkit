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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;



/**
 * The <b>SVNDiffWindow</b> class represents a diff window that
 * contains instructions and new data of a delta to apply to a file.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffWindow {
    
    private final long mySourceViewOffset;
    private final long mySourceViewLength;
    private final long myTargetViewLength;
    private SVNDiffInstruction[] myInstructions;
    private final long myNewDataLength;
    private long myInstructionsLength;
    private byte[] myInstructionsData;
    
    /**
     * Constructs an <b>SVNDiffWindow</b> object.
     * 
     * @param sourceViewOffset    an offset in the source view
     * @param sourceViewLength    a number of bytes to read from the
     *                            source view
     * @param targetViewLength    a length in bytes of the target view 
     *                            it must have after copying bytes
     * @param instructions        diff instructions to copy bytes
     * @param newDataLength       a number of bytes of new data
     */
    public SVNDiffWindow(long sourceViewOffset, long sourceViewLength, long targetViewLength, 
            SVNDiffInstruction[] instructions, long newDataLength) {
        mySourceViewOffset = sourceViewOffset;
        mySourceViewLength = sourceViewLength;
        myTargetViewLength = targetViewLength;
        myInstructions = instructions;
        myNewDataLength = newDataLength;
    }
    
    /**
     * Constructs an <b>SVNDiffWindow</b> object. This constructor is
     * used when bytes of instructions are not decoded and converted to
     * <b>SVNDiffInstruction</b> objects yet, but are kept elsewhere 
     * along with new data.
     * 
     * @param sourceViewOffset    an offset in the source view
     * @param sourceViewLength    a number of bytes to read from the
     *                            source view
     * @param targetViewLength    a length in bytes of the target view 
     *                            it must have after copying bytes
     * @param instructionsLength  a number of instructions bytes  
     * @param newDataLength       a number of bytes of new data
     * @see                       SVNDiffInstruction
     */
    public SVNDiffWindow(long sourceViewOffset, long sourceViewLength, long targetViewLength, long instructionsLength, 
            long newDataLength) {
        mySourceViewOffset = sourceViewOffset;
        mySourceViewLength = sourceViewLength;
        myTargetViewLength = targetViewLength;
        myInstructionsLength = instructionsLength;
        myNewDataLength = newDataLength;
    }
    
    /**
     * Returns the length of instructions in bytes. 
     * 
     * @return a number of instructions bytes
     */
    public long getInstructionsLength() {
        return myInstructionsLength;
    }
    
    /**
     * Returns the source view offset.
     * 
     * @return an offset in the source from where the source bytes
     *         must be copied
     */
    public long getSourceViewOffset() {
        return mySourceViewOffset;
    }
    
    /**
     * Returns the number of bytes to copy from the source view to the target one.
     * 
     * @return a number of source bytes to copy
     */
    public long getSourceViewLength() {
        return mySourceViewLength;
    }
    
    /**
     * Returns the length in bytes of the target view. The length of the target
     * view is actually the number of bytes that should be totally copied by all the 
     * instructions of this window.
     * 
     * @return a length in bytes of the target view
     */
    public long getTargetViewLength() {
        return myTargetViewLength;
    }
    
    /**
     * Returns the number of instructions contained in this
     * diff window.
     *  
     * @return an instructions number
     */
    public int getInstructionsCount() {
        return myInstructions.length;
    }
    
    /**
     * Gets a definite diff instruction from an array of instructions. 
     * 
     * @param  index a zero based instruction index
     * @return       an instrucion at <code>index</code>
     */
    public SVNDiffInstruction getInstructionAt(int index) {
        return myInstructions[index];
        
    }
    
    /**
     * Returns the number of new data bytes to copy to the target view.
     * 
     * @return a number of new data bytes
     */
    public long getNewDataLength() {
        return myNewDataLength;
    }
    
    /**
     * Applies this window's instructions. The source and target streams
     * are provided by <code>applyBaton</code>. 
     * 
     * <p>
     * If this window has got any {@link SVNDiffInstruction#COPY_FROM_SOURCE} instructions, then: 
     * <ol>
     * <li>At first copies a source view from the source stream 
     *     of <code>applyBaton</code> to the baton's inner source buffer.  
     *    {@link SVNDiffInstruction#COPY_FROM_SOURCE} instructions of this window are 
     *    relative to the bounds of that source buffer (source view, in other words).
     * <li>Second, according to instructions copies source bytes from the source buffer
     *     to the baton's target buffer (target view, in other words). 
     * <li>Then, if <code>applyBaton</code> is supplied with an MD5 digest, updates it with those bytes
     *     in the target buffer. So, after instructions applying completes, it will be the checksum for
     *     the full text.
     * <li>The last step - appends the target buffer bytes to the baton's 
     *     target stream.        
     * </ol> 
     * 
     * <p>
     * {@link SVNDiffInstruction#COPY_FROM_NEW_DATA} instructions are relative to the bounds of
     * the provided <code>newData</code> stream.
     * 
     * <p>
     * {@link SVNDiffInstruction#COPY_FROM_TARGET} instructions are relative to the bounds of
     * the target buffer. 
     * 
     * @param  applyBaton    a baton that provides the source and target 
     *                       views
     * @param  newData       an input stream to read new data bytes from
     * @throws SVNException
     */
    public void apply(SVNDiffWindowApplyBaton applyBaton, InputStream newData) throws SVNException {
        // here we have streams and buffer from the previous calls (or nulls).
        
        // 1. buffer for target.
        if (applyBaton.myTargetBuffer == null || applyBaton.myTargetViewSize < getTargetViewLength()) {
            applyBaton.myTargetBuffer = new byte[(int) getTargetViewLength()];
        }
        applyBaton.myTargetViewSize = getTargetViewLength();
        
        // 2. buffer for source.
        int length = 0;
        if (getSourceViewOffset() != applyBaton.mySourceViewOffset || getSourceViewLength() > applyBaton.mySourceViewLength) {
            byte[] oldSourceBuffer = applyBaton.mySourceBuffer;
            // create a new buffer
            applyBaton.mySourceBuffer = new byte[(int) getSourceViewLength()];
            // copy from the old buffer.
            if (applyBaton.mySourceViewOffset + applyBaton.mySourceViewLength > getSourceViewOffset()) {
                // copy overlapping part to the new buffer
                int start = (int) (getSourceViewOffset() - applyBaton.mySourceViewOffset);
                System.arraycopy(oldSourceBuffer, start, applyBaton.mySourceBuffer, 0, (int) (applyBaton.mySourceViewLength - start));
                length = (int) (applyBaton.mySourceViewLength - start);
            }            
        }
        if (length < getSourceViewLength()) {
            // fill what remains.
            try {
                long toSkip = getSourceViewOffset() - (applyBaton.mySourceViewOffset + applyBaton.mySourceViewLength);
                if (toSkip > 0) {
                    applyBaton.mySourceStream.skip(toSkip);
                }
                applyBaton.mySourceStream.read(applyBaton.mySourceBuffer, length, applyBaton.mySourceBuffer.length - length);
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            }
        }
        // update offsets in baton.
        applyBaton.mySourceViewLength = getSourceViewLength();
        applyBaton.mySourceViewOffset = getSourceViewOffset();
        
        // apply instructions.
        
        // TODO lazy instructions creation with iterator to avoid keeping 
        //      all thousands of instructions in memory during application.
        if (myInstructions == null) {
            if (myInstructionsData == null) {
                byte[] instrBytes = new byte[(int) getInstructionsLength()];
                try {
                    newData.read(instrBytes);
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                }
                myInstructions = SVNDiffWindowBuilder.createInstructions(instrBytes);
            } else {
                myInstructions = SVNDiffWindowBuilder.createInstructions(myInstructionsData);
                myInstructionsData = null;
            }
        }
        int tpos = 0;
        try {
            for(int i = 0; i < myInstructions.length; i++) {
                int iLength = myInstructions[i].length < getTargetViewLength() - tpos ? (int) myInstructions[i].length : (int) getTargetViewLength() - tpos; 
                switch (myInstructions[i].type) {
                    case SVNDiffInstruction.COPY_FROM_NEW_DATA:
                        newData.read(applyBaton.myTargetBuffer, tpos, iLength);
                        break;
                    case SVNDiffInstruction.COPY_FROM_TARGET:
                        int start = (int) myInstructions[i].offset;
                        int end = (int) myInstructions[i].offset + iLength;
                        int tIndex = tpos;
                        for(int j = start; j < end; j++) {
                            applyBaton.myTargetBuffer[tIndex] = applyBaton.myTargetBuffer[j];
                            tIndex++;
                        }
                        break;
                    case SVNDiffInstruction.COPY_FROM_SOURCE:
                        System.arraycopy(applyBaton.mySourceBuffer, (int) myInstructions[i].offset, 
                                applyBaton.myTargetBuffer, tpos, iLength);
                        break;
                    default:
                }
                tpos += myInstructions[i].length;
                if (tpos >= getTargetViewLength()) {
                    break;
                }
            }
            myInstructions = null;
            // save tbuffer.
            if (applyBaton.myDigest != null) {
                applyBaton.myDigest.update(applyBaton.myTargetBuffer, 0, (int) getTargetViewLength());
            }
            applyBaton.myTargetStream.write(applyBaton.myTargetBuffer, 0, (int) getTargetViewLength());
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
    }

    public byte[] apply(byte[] sourceBuffer, byte[] newData) {
        // here we have streams and buffer from the previous calls (or nulls).
        
        // 1. buffer for target.
        byte[] targetBuffer = new byte[(int) getTargetViewLength()];
        
        // apply instructions. 
        int dataOffset = 0;
        if (myInstructions == null) {
            if (myInstructionsData == null) {
                byte[] instrBytes = new byte[(int) getInstructionsLength()];
                System.arraycopy(newData, 0, instrBytes, 0, (int) getInstructionsLength());
                myInstructions = SVNDiffWindowBuilder.createInstructions(instrBytes);
                dataOffset = (int) getInstructionsLength();
            } else {
                myInstructions = SVNDiffWindowBuilder.createInstructions(myInstructionsData);
                myInstructionsData = null;
            }
        }
        int tpos = 0;
        for(int i = 0; i < myInstructions.length; i++) {
            int iLength = myInstructions[i].length < getTargetViewLength() - tpos ? (int) myInstructions[i].length : (int) getTargetViewLength() - tpos; 
            switch (myInstructions[i].type) {
                case SVNDiffInstruction.COPY_FROM_NEW_DATA:
                    System.arraycopy(newData, dataOffset, targetBuffer, tpos, iLength);
                    dataOffset += iLength;
                    break;
                case SVNDiffInstruction.COPY_FROM_TARGET:
                    int start = (int) myInstructions[i].offset;
                    int end = (int) myInstructions[i].offset + iLength;
                    int tIndex = tpos;
                    for(int j = start; j < end; j++) {
                        targetBuffer[tIndex] = targetBuffer[j];
                        tIndex++;
                    }
                    break;
                case SVNDiffInstruction.COPY_FROM_SOURCE:
                    System.arraycopy(sourceBuffer, (int) myInstructions[i].offset, 
                            targetBuffer, tpos, iLength);
                    break;
                default:
            }
            tpos += myInstructions[i].length;
            if (tpos >= getTargetViewLength()) {
                break;
            }
        }
        myInstructions = null;
        return targetBuffer;
    }
    
    public byte[] getInstructionsData() {
        return myInstructionsData;
    }
    
    public void setInstructionsData(byte[] data) {
        myInstructionsData = data;
    }
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representation of this object
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getSourceViewOffset());
        sb.append(":");
        sb.append(getSourceViewOffset());
        sb.append(":");
        sb.append(getTargetViewLength());
        sb.append(":");
        sb.append(getInstructionsCount());
        sb.append(":");
        sb.append(getNewDataLength());
        sb.append("::");
        for(int i = 0; i < getInstructionsCount(); i++) {
            sb.append(getInstructionAt(i).toString());
        }
        sb.append(":");
        return sb.toString();
    }
    
    public boolean hasInstructions() {
        if (myInstructions != null) {
            return myInstructions.length > 0;
        } else if (myInstructionsData != null) {
            return myInstructionsData.length > 0;
        }
        return false;
    }
}
