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

package org.tmatesoft.svn.core.io;

import java.util.Map;

/**
 * @author Alexander Kitaev
 */
public class SVNFileRevision {
    
    private String myPath;
    private long myRevision;
    
    public SVNFileRevision(String path, long revision, Map properties, Map propertiesDelta) {
        myPath = path;
        myRevision = revision;
        myProperties = properties;
        myPropertiesDelta = propertiesDelta;
    }
    
    public String getPath() {
        return myPath;
    }
    public Map getProperties() {
        return myProperties;
    }
    public Map getPropertiesDelta() {
        return myPropertiesDelta;
    }
    public long getRevision() {
        return myRevision;
    }
    private Map myProperties;
    private Map myPropertiesDelta;

}
