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

import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;



/**
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

    public SVNDiffWindow(long sourceViewOffset, long sourceViewLength, long targetViewLength, 
            SVNDiffInstruction[] instructions, long newDataLength) {
        mySourceViewOffset = sourceViewOffset;
        mySourceViewLength = sourceViewLength;
        myTargetViewLength = targetViewLength;
        myInstructions = instructions;
        myNewDataLength = newDataLength;
    }

    public SVNDiffWindow(long sourceViewOffset, long sourceViewLength, long targetViewLength, long instructionsLength, 
            long newDataLength) {
        mySourceViewOffset = sourceViewOffset;
        mySourceViewLength = sourceViewLength;
        myTargetViewLength = targetViewLength;
        myInstructionsLength = instructionsLength;
        myNewDataLength = newDataLength;
    }
    
    public long getInstructionsLength() {
        return myInstructionsLength;
    }
    
    public long getSourceViewOffset() {
        return mySourceViewOffset;
    }
    
    public long getSourceViewLength() {
        return mySourceViewLength;
    }
    
    public long getTargetViewLength() {
        return myTargetViewLength;
    }
    
    public int getInstructionsCount() {
        return myInstructions.length;
    }
    
    public SVNDiffInstruction getInstructionAt(int index) {
        return myInstructions[index];
        
    }
    
    public long getNewDataLength() {
        return myNewDataLength;
    }
    
    public void apply(SVNDiffWindowApplyBaton applyBaton, InputStream newData) throws SVNException {
        // here we have streams and buffer from the previous calls (or nulls).
        
        // 1. buffer for target.
        if (applyBaton.myTargetViewSize < getTargetViewLength()) {
            applyBaton.myTargetBuffer = new byte[(int) getTargetViewLength()];
        }
        applyBaton.myTargetViewSize = getTargetViewLength();
        
        // 2. buffer for source.
        int length = 0;
        if (getSourceViewOffset() != applyBaton.mySourceViewOffset || getSourceViewLength() > applyBaton.mySourceViewLength) {
            byte[] oldSourceBuffer = applyBaton.mySourceBuffer;
            // create new buffer
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
                SVNErrorManager.error(e.getMessage());
            }
        }
        // update offsets in baton.
        applyBaton.mySourceViewLength = getSourceViewLength();
        applyBaton.mySourceViewOffset = getSourceViewOffset();
        
        // apply instructions. 
        if (myInstructions == null) {
            byte[] instrBytes = new byte[(int) getInstructionsLength()];
            try {
                newData.read(instrBytes);
            } catch (IOException e) {
                SVNErrorManager.error(e.getMessage());
            }
            myInstructions = SVNDiffWindowBuilder.createInstructions(instrBytes);
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
            SVNErrorManager.error(e.getMessage());
            
        }
    }

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
}
