/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNChecksum {
    private SVNChecksumKind myKind;
    private String myDigest;
    
    public SVNChecksum(SVNChecksumKind kind, String digest) {
        myKind = kind;
        myDigest = digest;
    }
    
    public SVNChecksumKind getKind() {
        return myKind;
    }

    public String getDigest() {
        return myDigest;
    }

}
