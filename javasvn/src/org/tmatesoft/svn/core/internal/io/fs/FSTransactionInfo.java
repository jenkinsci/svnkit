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
public class FSTransactionInfo {
    private long myBaseRevision;
    private String myTxnId;
    
    public FSTransactionInfo(long revision, String id) {
        myBaseRevision = revision;
        myTxnId = id;
    }

    public long getBaseRevision() {
        return myBaseRevision;
    }

    public void setBaseRevision(long baseRevision) {
        myBaseRevision = baseRevision;
    }

    public String getTxnId() {
        return myTxnId;
    }

    public void setTxnId(String txnId) {
        myTxnId = txnId;
    }
}
