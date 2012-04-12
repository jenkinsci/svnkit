/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import org.tigris.subversion.javahl.Version;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
class SVNClientImplVersion extends org.tigris.subversion.javahl.Version {

    private static SVNClientImplVersion ourInstance;

    public int getMajor() {
        return SVNClientImpl.versionMajor();
    }

    public int getMinor() {
        return SVNClientImpl.versionMinor();
    }

    public int getPatch() {
        return SVNClientImpl.versionMicro();
    }
    
    public long getRevisionNumber() {
        return SVNClientImpl.versionRevisionNumber();
    }

    public String toString() {
        String revision = getRevisionNumber() < 0 ? "SNAPSHOT" : Long.toString(getRevisionNumber());
        return "SVNKit v" + getMajor() + "." + getMinor() + "." + getPatch() + "." + revision;
    }

    public static Version getInstance() {
        if (ourInstance == null) {
            ourInstance = new SVNClientImplVersion();
        }
        return ourInstance;
    }
    
}