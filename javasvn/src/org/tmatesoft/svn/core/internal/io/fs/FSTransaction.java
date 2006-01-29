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

import java.util.Collection;
import java.util.Map;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSTransaction {
    /* kind of transaction. */
    FSTransactionKind myKind;
    
    /* node revision id of the root node.  */
    FSID myRootId;
    
    /* node revision id of the node which is the root of the revision
     * upon which this txn is base.  (unfinished only) 
     */
    FSID myBaseId;
    
    /* copies list (String copy-ids), or null if there have been
     * no copies in this transaction.  
     */
    Collection myCopies;
    
    /* property list (String name, String value).
     * may be null if there are no properties.  
     */
    Map myProperties;

    public FSTransaction(FSTransactionKind txnKind, FSID rootId, FSID baseId, Collection copies, Map properties) {
        myKind = txnKind;
        myRootId = rootId;
        myBaseId = baseId;
        myCopies = copies;
        myProperties = properties;
    }
    
    public FSID getBaseId() {
        return myBaseId;
    }
    
    public void setBaseId(FSID baseId) {
        myBaseId = baseId;
    }
    
    public Collection getCopies() {
        return myCopies;
    }
    
    public void setCopies(Collection copies) {
        myCopies = copies;
    }
    
    public FSTransactionKind getKind() {
        return myKind;
    }
    
    public void setKind(FSTransactionKind kind) {
        myKind = kind;
    }
    
    public Map getProperties() {
        return myProperties;
    }
    
    public void setProperties(Map properties) {
        myProperties = properties;
    }
    
    public FSID getRootId() {
        return myRootId;
    }
    
    public void setRootId(FSID rootId) {
        myRootId = rootId;
    }
}
