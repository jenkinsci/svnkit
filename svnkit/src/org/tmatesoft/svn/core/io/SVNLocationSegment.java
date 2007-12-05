/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 * @since   1.2, SVN 1.5
 */
public class SVNLocationSegment {
    private long myStartRevision;
    private long myEndRevision;
    private String myPath;

    public SVNLocationSegment(long startRevision, long endRevision, String path) {
        myStartRevision = startRevision;
        myEndRevision = endRevision;
        myPath = path;
    }

    /**
     * Gets the path.
     * 
     * @return a path 
     */
    public String getPath() {
        return myPath;
    }
    
    public long getStartRevision() {
        return myStartRevision;
    }

    public long getEndRevision() {
        return myEndRevision;
    }

}
