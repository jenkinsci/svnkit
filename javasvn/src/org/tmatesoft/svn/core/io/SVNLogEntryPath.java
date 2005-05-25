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
 * <code>SVNLogEntryPath</code> provides information about a changed path, a type of the 
 * change ('M' - Modified, 'A' - Added, 'D'- Deleted) and if this path is actually a copy of 
 * another one in a repository - also its parent's path and revision.
 * 
 * <p>
 * <code>SVNLogEntryPath</code> inctances are contained by an <code>SVNLogEntry</code>
 * as all the paths that were changed in its revision.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNLogEntry
 */
public class SVNLogEntryPath {
    
    private String myPath;
    private char myType;
    private String myCopyPath;
    private long myCopyRevision;
    
    /**
     * Constructs an <code>SVNLogEntryPath</code> given a path that was changed in the 
     * revision, a type of the path change (that is a char label describing the 
     * character of the change as follow:'M' - Modified, 'A' - Added, 'D'- Deleted) and 
     * if the path is actually a branch of another path in the repository then also its
     * parent's path and revision.
     *  
     * @param path				a path that was changed in the revision
     * @param type				a type of the path change
     * @param copyPath			the parent's path if the <code>path</code> is its branch or
     * 							<code>null</code> if not
     * @param copyRevision		the parent's revision if the <code>path</code> is its branch
     * 							or -1 if not
     */
    public SVNLogEntryPath(String path, char type, String copyPath,
            long copyRevision) {
        myPath = path;
        myType = type;
        myCopyPath = copyPath;
        myCopyRevision = copyRevision;
    }
    
    /**
     * Gets the path of the parent (if the current changed path is its branch).
     * 
     * @return	the origin path that the current changed path was branched off
     * 			or <code>null</code> if it's not a branch
     */
    public String getCopyPath() {
        return myCopyPath;
    }
    
    /**
     * Gets the revision of the parent (if the current changed path is its branch).
     * 
     * @return	the revision of the origin path that the current changed path was 
     * 			branched off or -1 if it's not a branch
     */
    public long getCopyRevision() {
        return myCopyRevision;
    }
    
    /**
     * Returns the current changed path.
     * 
     * @return  the changed path represented by this object
     */
    public String getPath() {
        return myPath;
    }
    /**
     * Gets the type of the change applied to the path (as follow:'M' - Modified, 
     * 'A' - Added, 'D'- Deleted).
     * 
     * @return the path change type as a char label
     */
    public char getType() {
        return myType;
    }
    
    /**
     * Sets this object to represent the given <code>path</code> as changed in the 
     * revision.
     * 
     * @param path  	the changed path in the repository in a definite revision
     */
    protected void setPath(String path) {
    	myPath = path;
    }
}
