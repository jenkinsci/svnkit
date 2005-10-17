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

package org.tmatesoft.svn.core;

import java.util.Date;



/**
 * The <b>SVNDirEntry</b> class is a representation of a versioned 
 * directory entry.
 * 
 * <p>
 * <b>SVNDirEntry</b> keeps an entry name, entry kind (is it a file or directory), 
 * file size (in case an entry is a file), the last changed revision, the date when 
 * the entry was last changed, the name of the author who last changed the entry, the
 * commit log message for the last changed revision. <b>SVNDirEntry</b> also knows 
 * if the entry has any properties. 
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNDirEntryHandler
 */
public class SVNDirEntry implements Comparable {

    private String myName;
    private SVNNodeKind myKind;
    private long mySize;
    private boolean myHasProperties;
    private long myFirstRevision;
    private Date myCreatedDate;
    private String myLastAuthor;
    private String myPath;
    private String myCommitMessage;

    /**
     * Constructs an instance of <b>SVNDirEntry</b>.
     * 
     * @param name 			an entry name
     * @param kind 			the node kind for the entry
     * @param size 			the entry size in bytes
     * @param hasProperties <span class="javakeyword">true</span> if the 
     *                      entry has properties, otherwise <span class="javakeyword">false</span>
     * @param firstRevision the last changed revision of the entry
     * @param createdDate 	the date the entry was last changed
     * @param lastAuthor 	the person who last changed the entry
     */
    public SVNDirEntry(String name, SVNNodeKind kind, long size,
                       boolean hasProperties, long firstRevision, Date createdDate,
                       String lastAuthor) {
        myName = name;
        myKind = kind;
        mySize = size;
        myHasProperties = hasProperties;
        myFirstRevision = firstRevision;
        myCreatedDate = createdDate;
        myLastAuthor = lastAuthor;
    }
    
    /**
     * Sets the entry's path. 
     * 
     * @param path a path relative to a repository location
     */
    public void setPath(String path) {
        myPath = path;
    }
    
    /**
     * Returns the entry's path.
     * 
     * @return a path relative to a repository location or 
     *         <span class="javakeyword">null</span> if no path is
     *         specified
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Gets the the directory entry name
     * 
     * @return 	the name of this entry
     */
    public String getName() {
        return myName;
    }
    
    /**
     * Returns the file size in bytes (if this entry is a file).
     * 
     * @return 	the size of this entry in bytes
     */
    public long size() {
        return mySize;
    }
    
    /**
     * Tells if the entry has any properties.
     * 
     * @return 	<span class="javakeyword">true</span> if has, 
     *          <span class="javakeyword">false</span> otherwise
     */
    public boolean hasProperties() {
        return myHasProperties;
    }
    
    /**
     * Returns the entry node kind.
     * 
     * @return  the node kind of this entry 
     * @see 	SVNNodeKind
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Returns the date the entry was last changed.
     * 
     * @return 	the datestamp when the entry was last changed
     */
    public Date getDate() {
        return myCreatedDate;
    }
    
    /**
     * Gets the last changed revision of this entry.
     * 
     * @return 	the revision of this entry when it was last changed 
     */
    public long getRevision() {
        return myFirstRevision;
    }
    
    /**
     * Retrieves the name of the author who last changed this entry.
     * 
     * @return 	the last author's name.
     */
    public String getAuthor() {
        return myLastAuthor;
    }
    
    /**
     * Sets the entry's name. 
     * 
     * @param name 	a directory entry name 
     */
    public void setName(String name) {
        myName = name;
    }
    
    /**
     * Returns the commit log message for the revision of this entry.
     * 
     * @return a commit log message
     */
    public String getCommitMessage() {
        return myCommitMessage;
    }
    
    /**
     * Sets the commit log message of the revision of this entry.
     * 
     * @param message a commit log message
     */
    public void setCommitMessage(String message) {
        myCommitMessage = message;
    }
    
    /**
     * Retirns a string representation of this object. 
     * 
     * @return 	a string representation of this directory entry
     */
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("name=");
        result.append(myName);
        result.append(", kind=");
        result.append(myKind);
        result.append(", size=");
        result.append(mySize);
        result.append(", hasProps=");
        result.append(myHasProperties);
        result.append(", creation-rev=");
        result.append(myFirstRevision);
        if (myLastAuthor != null) {
            result.append(", lastAuthor=");
            result.append(myLastAuthor);
        }
        if (myCreatedDate != null) {
            result.append(", creation-date=");
            result.append(myCreatedDate);
        }
        return result.toString();
    }
    
    /**
     * Compares this object with another one.
     * 
     * @param   o an object to compare with
     * @return    <ul>
     *            <li>-1 - if <code>o</code> is either <span class="javakeyword">null</span>,
     *            or is not an instance of <b>SVNDirEntry</b>, or this entry's name is lexicographically 
     *            less than the name of <code>o</code>; 
     *            </li>
     *            <li>1 - if this entry's name is lexicographically greater than the name of  
     *            <code>o</code>;
     *            </li>
     *            <li>0 - if and only if <code>o</code> has got the same name 
     *            as this one has
     *            </li>
     *            </ul>
     */
    public int compareTo(Object o) {
        if (o == null || o.getClass() != SVNDirEntry.class) {
            return -1;
        }
        SVNNodeKind otherKind = ((SVNDirEntry) o).getKind();
        if (otherKind != getKind()) {
            return getKind().compareTo(otherKind);    
        }
        String otherName = ((SVNDirEntry) o).getName();
        if (myName == null || otherName == null) {
            return myName == otherName ? 0 : (myName == null ? -1 : 1); 
        }
        return myName.compareTo(otherName);
    }
}
