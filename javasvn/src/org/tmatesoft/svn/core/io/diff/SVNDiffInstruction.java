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


/**
 * The <b>SVNDiffInstruction</b> class represents instructions used to
 * apply delta. 
 * <p>
 * For now there are three types of copy instructions:
 * <ul>
 * <li>
 * {@link SVNDiffInstruction#COPY_FROM_SOURCE}: that is when bytes are copied from
 * the source (for example, existing revision of a file) to the target 
 * what means that those bytes are left the same as in the source    
 * </li>
 * <li>
 * {@link SVNDiffInstruction#COPY_FROM_NEW_DATA}: new data is some new bytes that a user
 * has added to an existing revision of a file, i.e. bytes of the user's
 * changes, in other words 
 * </li>
 * <li>
 * {@link SVNDiffInstruction#COPY_FROM_TARGET}: that is, when a sequence of bytes in the 
 * target must be repeated
 * </li>
 * </ul>
 * 
 * <p>
 * When a new file is added to a repository in a particular revision,
 * its contents are entirely new data. But all the further changed 
 * revisions of this file may be got as copying some source bytes
 * from a previous revision and some new bytes that were added only 
 * in the latest (at any point) revision.
 *  
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffInstruction {
    /**
     * A type of an instruction that says that data must be copied
     * from the source view to the target one.
     */
    public static final int COPY_FROM_SOURCE = 0x00;
    
    /**
     * A type of an instruction that says that data must be copied
     * from the target view to the target itself.  
     */
    public static final int COPY_FROM_TARGET = 0x01;
    
    /**
     * A type of an instruction that says that data must be copied
     * from the new data to the target view.  
     */
    public static final int COPY_FROM_NEW_DATA = 0x02;
    
    /**
     * Creates a particular type of a diff instruction.
     * Instruction offsets are relative to the bounds of views, i.e.
     * a source/target view is a window of bytes (specified in a concrete 
     * diff window) in the source/target stream (this can be a file, a buffer).
     * 
     * @param t  a type of an instruction
     * @param l  a number of bytes to copy
     * @param o  an offset in the source (which may be a source or a target
     *           view, or a new data stream) from where
     *           the bytes are to be copied
     * @see      SVNDiffWindow
     */
    public SVNDiffInstruction(int t, long l, long o) {
        type = t;
        length = l;
        offset = o;        
    }
    
    /**
     * Creates a new instruction object.
     * It's the instruction for the empty contents file.
     *
     */
    public SVNDiffInstruction() {
        this(0,0,0);
        
    }
    
    /**
     * A type of this instruction.
     */
    public int type; 
    
    /**
     * A length bytes to copy.    
     */
    public long length;
    
    /**
     * An offset in the source from where the bytes
     * should be copied. Instruction offsets are relative to the bounds of 
     * views, i.e. a source/target view is a window of bytes (specified in a concrete 
     * diff window) in the source/target stream (this can be a file, a buffer).
     *  
     */
    public long offset;
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representation of this object
     */
    public String toString() {
        StringBuffer b = new StringBuffer();
        switch(type) {
            case COPY_FROM_SOURCE:
                b.append("S->");
                break;
            case COPY_FROM_TARGET:
                b.append("T->");
                break;
            case COPY_FROM_NEW_DATA:
                b.append("D->");
                break;
        }
        if (type == 0 || type == 1) {
            b.append(offset);
        } else {
            b.append("x");
        }
        b.append(":");
        b.append(length);
        return b.toString();
    }
}
