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
public class FSTransaction {
    /* node revision id of the root node.  */
    private FSID myRootID;
    
    /* node revision id of the node which is the root of the revision
     * upon which this txn is base.  (unfinished only) 
     */
    private FSID myBaseID;
    
    public FSTransaction(FSID rootID, FSID baseID) {
        myRootID = rootID;
        myBaseID = baseID;
    }
    
    public FSID getBaseID() {
        return myBaseID;
    }
    
    public FSID getRootID() {
        return myRootID;
    }
    
}
