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
package org.tmatesoft.svn.core.internal.wc.db;

import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNRepositoryInfo {
    private SVNURL myURL;
    private String myUUID;
    
    public SVNURL getURL() {
        return myURL;
    }
    
    public void setURL(SVNURL url) {
        myURL = url;
    }
    
    public String getUUID() {
        return myUUID;
    }
    
    public void setUUID(String uuid) {
        myUUID = uuid;
    }
    
}
