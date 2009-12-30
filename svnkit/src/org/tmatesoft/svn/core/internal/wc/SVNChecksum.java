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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNLogType;


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

    public String toString() {
        String checksumRep = myKind == SVNChecksumKind.MD5 ? "$md5 $" : "$sha1$";
        return checksumRep + myDigest;
    }
    
    public static SVNChecksum parseChecksum(String checksum) throws SVNException {
        SVNErrorManager.assertionFailure(checksum != null && checksum.length() > 6, null, SVNLogType.WC);
        SVNChecksumKind kind = checksum.charAt(1) == 'm' ? SVNChecksumKind.MD5 : SVNChecksumKind.SHA1; 
        String hexDigest = checksum.substring(6);
        return new SVNChecksum(kind, hexDigest);
    }
}
