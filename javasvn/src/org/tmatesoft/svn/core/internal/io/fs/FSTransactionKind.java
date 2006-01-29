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
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSTransactionKind {
    /* Transaction Kinds */
    
    /* normal, uncommitted */
    public static final FSTransactionKind TXN_KIND_NORMAL = new FSTransactionKind(0);
    /* committed */
    public static final FSTransactionKind TXN_KIND_COMMITTED = new FSTransactionKind(1);
    /* uncommitted and dead */
    public static final FSTransactionKind TXN_KIND_DEAD = new FSTransactionKind(2);
    
    private int myID;

    private FSTransactionKind(int id) {
        myID = id;
    }
    public boolean equals(Object o){
        if (o == null || o.getClass() != FSTransactionKind.class) {
            return false;
        }
        return myID == ((FSTransactionKind) o).myID;
    }
}
