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


/**
 * Represents where in the current svndiff data block each
 * representation is.
 *  
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepresentationState {
    FSFile file;
    /* The starting offset for the raw svndiff/plaintext data minus header. */
    long start;
    /* The current offset into the file. */
    long offset;
    /* The end offset of the raw data. */
    long end;
    /* If a delta, what svndiff version? */
    int version;
    
    int chunkIndex;

    public FSRepresentationState() {
    }

    public FSRepresentationState(FSFile file, long start, long offset, long end, int version, int index) {
        this.file = file;
        this.start = start;
        this.offset = offset;
        this.end = end;
        this.version = version;
        chunkIndex = index;
    }


    /* Read the next line from a file and parse it as a text
     * representation entry. Return parsed args.
     */
}
