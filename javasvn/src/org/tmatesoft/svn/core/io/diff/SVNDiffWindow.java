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

import org.tmatesoft.svn.core.io.SVNException;



/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffWindow {
    
    private final long mySourceViewOffset;
    private final long mySourceViewLength;
    private final long myTargetViewLength;
    private final SVNDiffInstruction[] myInstructions;
    private final long myNewDataLength;

    public SVNDiffWindow(long sourceViewOffset, long sourceViewLength, long targetViewLength, 
            SVNDiffInstruction[] instructions, long newDataLength) {
        mySourceViewOffset = sourceViewOffset;
        mySourceViewLength = sourceViewLength;
        myTargetViewLength = targetViewLength;
        myInstructions = instructions;
        myNewDataLength = newDataLength;
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

    public void apply(ISVNRAData source, ISVNRAData target, InputStream newData, long offset) throws SVNException {
        // use ra files as source and target...
        if (target == null) {
            throw new SVNException("target shouldn't be null");
        }
        InputStream src;
        try {
            for(int i = 0; i < myInstructions.length; i++) {
                myInstructions[i].offset += offset;
                switch (myInstructions[i].type) {
                    case SVNDiffInstruction.COPY_FROM_NEW_DATA:
                        src = newData;
                        break;
                    case SVNDiffInstruction.COPY_FROM_TARGET:
                        src = target.read(myInstructions[i].offset, myInstructions[i].length);
                        break;
                    default:
                        src = source.read(myInstructions[i].offset, myInstructions[i].length);
                }
                target.append(src, myInstructions[i].length);
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            try {
                source.close();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            try {
                target.close();
            } catch (IOException e3) {
                e3.printStackTrace();
            }
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
