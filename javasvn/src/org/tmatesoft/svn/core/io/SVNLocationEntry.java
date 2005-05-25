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

/**
 * A location entry represents the combination of the two following related matters:
 * <ol>
 * <li>the pathway of a versioned file stored in the repository
 * <li>the revision number of the repository at which the file is located in that pathway
 * </ol>
 * This entity is realized in the public <code>SVNLocationEntry</code> class.
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 * @see ISVNLocationEntryHandler
 */
public class SVNLocationEntry {
    
    private long myRevision;
    private String myPath;
    
    /**
     * Constructs an <code>SVNLocationEntry</code> object.
     * 
     * @param revision the revision number
     * @param path the file pathway in the reposytory
     */
    public SVNLocationEntry(long revision, String path) {
        myRevision = revision;
        myPath = path;
    }
    
    /**
     * Get the file path (relative to the <code>URL</code> used to create 
     * an {@link SVNRepository}).
     * 
     * @return a file pathway in the repository.
     */
    public String getPath() {
        return myPath;
    }
    /**
     * Get the revision number.
     * 
     * @return revision number.
     */
    public long getRevision() {
        return myRevision;
    }
}
