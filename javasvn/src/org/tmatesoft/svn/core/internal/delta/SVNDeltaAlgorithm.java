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
package org.tmatesoft.svn.core.internal.delta;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNDeltaAlgorithm {

    private Collection myDiffInstructions = new ArrayList();
    private ByteArrayOutputStream myNewData = new ByteArrayOutputStream();

    public void reset() {
        myDiffInstructions.clear();
        myNewData.reset();
    }

    public abstract void computeDelta(byte[] a, int aLength, byte[] b, int bLength);
    
    public SVNDiffInstruction[] getDiffInstructions() {
        return (SVNDiffInstruction[]) myDiffInstructions.toArray(new SVNDiffInstruction[myDiffInstructions.size()]);
    }
    
    public ByteArrayOutputStream getNewDataStream() {
        return myNewData;
    }

    public Iterator diffInstructions() {
        return myDiffInstructions.iterator();
    }

    public byte[] getNewData() {
        return myNewData.toByteArray();
    }

    protected void copyFromSource(int position, int length) {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, length, position);
        myDiffInstructions.add(instruction);
    }

    protected void copyFromTarget(int position, int length) {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_TARGET, length, position);
        myDiffInstructions.add(instruction);
    }

    protected void copyFromNewData(byte[] data, int offset, int length) {
        SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, length, 0);
        myDiffInstructions.add(instruction);
        myNewData.write(data, offset, length);
    }
}
