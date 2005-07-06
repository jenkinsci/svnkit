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

package org.tmatesoft.svn.core.diff;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffInstruction {
    
    public static final int COPY_FROM_SOURCE = 0x00;
    public static final int COPY_FROM_TARGET = 0x01;
    public static final int COPY_FROM_NEW_DATA = 0x02;
    
    public SVNDiffInstruction(int t, long l, long o) {
        type = t;
        length = l;
        offset = o;        
    }
    
    public SVNDiffInstruction() {
        this(0,0,0);
        
    }
    
    public int type; 
    public long length;
    public long offset;

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
