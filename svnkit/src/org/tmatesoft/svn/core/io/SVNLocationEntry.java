/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

/**
 * The <b>SVNLocationEntry</b> represents a mapping of a path to its 
 * revision. That is, the repository path of an item in a particular
 * revision.  
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNLocationEntryHandler
 */
public class SVNLocationEntry {
    
    private long myRevision;
    private String myPath;
    
    /**
     * Constructs an <b>SVNLocationEntry</b> object.
     * 
     * @param revision  a revision number
     * @param path      an item's path in the reposytory in 
     *                  the <code>revision</code>
     */
    public SVNLocationEntry(long revision, String path) {
        myRevision = revision;
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
    
    /**
     * Gets the revision number.
     * 
     * @return a revision number.
     */
    public long getRevision() {
        return myRevision;
    }
}
