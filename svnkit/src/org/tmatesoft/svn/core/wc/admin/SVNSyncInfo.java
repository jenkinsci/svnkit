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
package org.tmatesoft.svn.core.wc.admin;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSyncInfo {
    private String mySrcURL;
    private String mySourceRepositoryUUID;
    private long myLastMergedRevision;

    public SVNSyncInfo(String srcURL, String sourceRepositoryUUID, long lastMergedRevision) {
        mySrcURL = srcURL;
        mySourceRepositoryUUID = sourceRepositoryUUID;
        myLastMergedRevision = lastMergedRevision;
    }

    public String getSrcURL() {
        return mySrcURL;
    }
    
    public String getSourceRepositoryUUID() {
        return mySourceRepositoryUUID;
    }
    
    public long getLastMergedRevision() {
        return myLastMergedRevision;
    }
    
}
