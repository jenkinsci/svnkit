/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class FSRepresentation {

    public static final String REP_DELTA = "DELTA";
    public static final String REP_PLAIN = "PLAIN";
    public static final String REP_TRAILER = "ENDREP";

    private long myRevision;
    private long myOffset;
    private long mySize;
    private long myExpandedSize;
    private String myMD5HexDigest;
    private String mySHA1HexDigest;
    private String myTxnId;
    private String myUniquifier;
    
    public FSRepresentation(long revision, long offset, long size, long expandedSize, String hexDigest) {
        myRevision = revision;
        myOffset = offset;
        mySize = size;
        myExpandedSize = expandedSize;
        myMD5HexDigest = hexDigest;
    }

    public FSRepresentation(FSRepresentation representation) {
        myRevision = representation.getRevision();
        myOffset = representation.getOffset();
        mySize = representation.getSize();
        myExpandedSize = representation.getExpandedSize();
        myMD5HexDigest = representation.getMD5HexDigest();
        myTxnId = representation.myTxnId;
    }

    public FSRepresentation() {
        myRevision = SVNRepository.INVALID_REVISION;
        myOffset = -1;
        mySize = -1;
        myExpandedSize = -1;
        myMD5HexDigest = null;
    }

    public void setRevision(long rev) {
        myRevision = rev;
    }

    public void setOffset(long offset) {
        myOffset = offset;
    }

    public void setSize(long size) {
        mySize = size;
    }

    public void setExpandedSize(long expandedSize) {
        myExpandedSize = expandedSize;
    }

    public void setMD5HexDigest(String hexDigest) {
        myMD5HexDigest = hexDigest;
    }

    public String getSHA1HexDigest() {
        return mySHA1HexDigest;
    }
    
    public void setSHA1HexDigest(String hexDigest) {
        mySHA1HexDigest = hexDigest;
    }

    public String getUniquifier() {
        return myUniquifier;
    }

    
    public void setUniquifier(String uniquifier) {
        myUniquifier = uniquifier;
    }

    public long getRevision() {
        return myRevision;
    }

    public long getOffset() {
        return myOffset;
    }

    public long getSize() {
        return mySize;
    }

    public long getExpandedSize() {
        return myExpandedSize;
    }

    public String getMD5HexDigest() {
        return myMD5HexDigest;
    }

    public static boolean compareRepresentations(FSRepresentation r1, FSRepresentation r2) {
        if (r1 == r2) {
            return true;
        } else if (r1 == null) {
            return false;
        }
        return r1.equals(r2);
    }

    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != FSRepresentation.class) {
            return false;
        }
        FSRepresentation rep = (FSRepresentation) obj;
        return myRevision == rep.getRevision() && myOffset == rep.getOffset();
    }

    public String toString() {
        return myRevision + " " + myOffset + " " + mySize + " " + myExpandedSize + " " + myMD5HexDigest;
    }

    public String getTxnId() {
        return myTxnId;
    }

    public void setTxnId(String txnId) {
        myTxnId = txnId;
    }

    public boolean isTxn() {
        return myTxnId != null;
    }
}
