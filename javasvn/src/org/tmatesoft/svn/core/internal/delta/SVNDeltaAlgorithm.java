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
package org.tmatesoft.svn.core.internal.delta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNDeltaAlgorithm {

    private ByteArrayOutputStream myDiffInstructions = new ByteArrayOutputStream();
    private ByteArrayOutputStream myNewData = new ByteArrayOutputStream();

    public void reset() {
        myDiffInstructions.reset();
        myNewData.reset();
    }

    public abstract void computeDelta(byte[] a, int aLength, byte[] b, int bLength) throws IOException;
    
    public byte[] getDiffInstructionsData() {
        return myDiffInstructions.toByteArray();
    }
    
    public ByteArrayOutputStream getNewDataStream() {
        return myNewData;
    }

    public byte[] getNewData() {
        return myNewData.toByteArray();
    }

    protected void copyFromSource(int position, int length) throws IOException {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, length, position);
        instruction.writeTo(myDiffInstructions);
    }

    protected void copyFromTarget(int position, int length) throws IOException {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_TARGET, length, position);
        instruction.writeTo(myDiffInstructions);
    }

    protected void copyFromNewData(byte[] data, int offset, int length) throws IOException {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, length, 0);
        instruction.writeTo(myDiffInstructions);
        myNewData.write(data, offset, length);
    }
}
