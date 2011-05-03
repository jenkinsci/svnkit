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
 * @author TMate Software Ltd.
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

    public static SVNChecksum deserializeChecksum(String checksum) throws SVNException {
        SVNErrorManager.assertionFailure(checksum != null && checksum.length() > 6, null, SVNLogType.WC);
        SVNChecksumKind kind = checksum.charAt(1) == 'm' ? SVNChecksumKind.MD5 : SVNChecksumKind.SHA1;
        String hexDigest = checksum.substring(6);
        return new SVNChecksum(kind, hexDigest);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SVNChecksum)) {
            return false;
        }
        final SVNChecksum that = (SVNChecksum) obj;
        final SVNChecksumKind thisKind = this.getKind();
        final SVNChecksumKind thatKind = that.getKind();
        if (!(thisKind == null ? thatKind == null : thisKind.equals(thatKind))) {
            return false;
        }
        final String thisDigest = this.getDigest();
        final String thatDigest = that.getDigest();
        return thisDigest == null ? thatDigest == null : thisDigest.equals(thatDigest);
    }

    private volatile int hashCode = 0;

    public int hashCode() {
        if (hashCode == 0) {
            hashCode = 17;
            SVNChecksumKind kind = getKind();
            if (kind != null) {
                hashCode = hashCode * 37 + kind.hashCode();
            }
            String digest = getDigest();
            if (digest != null) {
                hashCode = hashCode * 37 + digest.hashCode();
            }
        }
        return hashCode;
    }

}
