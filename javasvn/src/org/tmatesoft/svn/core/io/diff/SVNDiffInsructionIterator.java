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

import java.util.Iterator;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
class SVNDiffInsructionIterator implements Iterator {
    
    private int myOffset;
    private byte[] myInstructionsData;
    private SVNDiffInstruction myInstruction;

    public SVNDiffInsructionIterator(byte[] instructionsData) {
        myInstructionsData = instructionsData;
        myOffset = 0;
        readInstruction();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext() {
        return myInstruction != null;
    }

    public Object next() {
        SVNDiffInstruction result = myInstruction;
        readInstruction();
        return result;
    }

    private SVNDiffInstruction readInstruction() {
        if (myOffset >= myInstructionsData.length) {
            return null;
        }
        myInstruction = new SVNDiffInstruction();
        myInstruction.type = (myInstructionsData[myOffset] & 0xC0) >> 6;
        myInstruction.length = myInstructionsData[myOffset] & 0x3f;
        myOffset++;
        if (myInstruction.length == 0) {
            // read length from next byte                
            myInstruction.length = readInt();
        } 
        if (myInstruction.type == 0 || myInstruction.type == 1) {
            // read offset from next byte (no offset without length).
            myInstruction.offset = readInt();
        } 
        return myInstruction;
    }

    private int readInt() {
        int result = 0;
        while(true) {
            byte b = myInstructionsData[myOffset];
            result = result << 7;
            result = result | (b & 0x7f);
            if ((b & 0x80) != 0) {
                // high bit
                myOffset++;
                if (myOffset >= myInstructionsData.length) {
                    return -1;
                }
                continue;
            }
            // integer read.
            myOffset++;
            return result;
        }
    }
}
