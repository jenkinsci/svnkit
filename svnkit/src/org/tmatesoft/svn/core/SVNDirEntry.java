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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see 	ISVNDirEntryHandler
 */
public class SVNDirEntry implements Comparable {

    private String myName;
    private SVNNodeKind myKind;
    private long mySize;
    private boolean myHasProperties;
    private long myRevision;
    private Date myCreatedDate;
    private String myLastAuthor;
    private String myPath;
    private String myCommitMessage;
    private SVNLock myLock;
    private SVNURL myURL;

    /**
     * Constructs an instance of <b>SVNDirEntry</b>.
     * 
     * @param url           a url of this entry 
     * @param name 			an entry name
     * @param kind 			the node kind for the entry
     * @param size 			the entry size in bytes
     * @param hasProperties <span class="javakeyword">true</span> if the 
     *                      entry has properties, otherwise <span class="javakeyword">false</span>
     * @param revision      the last changed revision of the entry
     * @param createdDate 	the date the entry was last changed
     * @param lastAuthor 	the person who last changed the entry
     */
    public SVNDirEntry(SVNURL url, String name, SVNNodeKind kind, long size,
                       boolean hasProperties, long revision, Date createdDate,
                       String lastAuthor) {
        myURL = url;
        myName = name;
        myKind = kind;
        mySize = size;
        myHasProperties = hasProperties;
        myRevision = revision;
        myCreatedDate = createdDate;
        myLastAuthor = lastAuthor;
    }
    

    /**
     * Returns the entry's URL.
     * 
     * @return this entry's URL.
     */
    public SVNURL getURL() {
        return myURL;
    }

    /**
     * Constructs an instance of <b>SVNDirEntry</b>.
     * 
     * @param url           a url of this entry 
     * @param name          an entry name
     * @param kind          the node kind for the entry
     * @param size          the entry size in bytes
     * @param hasProperties <span class="javakeyword">true</span> if the 
     *                      entry has properties, otherwise <span class="javakeyword">false</span>
     * @param revision      the last changed revision of the entry
     * @param createdDate   the date the entry was last changed
     * @param lastAuthor    the person who last changed the entry
     * @param commitMessage the log message of the last change commit
     */
    public SVNDirEntry(SVNURL url, String name, SVNNodeKind kind, long size,
            boolean hasProperties, long revision, Date createdDate,
            String lastAuthor, String commitMessage) {
        myURL = url;
        myName = name;
        myKind = kind;
        mySize = size;
        myHasProperties = hasProperties;
        myRevision = revision;
        myCreatedDate = createdDate;
        myLastAuthor = lastAuthor;
        myCommitMessage = commitMessage;
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
     * @return  the size of this entry in bytes
     */
    public long getSize() {
        return mySize;
    }

    /**
     * Returns the file size in bytes (if this entry is a file).
     *
     * @deprecated use {@link #getSize()} instead
     * @return 	the size of this entry in bytes
     */
    public long size() {
        return getSize();
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
        return myRevision;
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
     * Returns the entry's path.
     * 
     * @return a path relative to a repository location or 
     *         <span class="javakeyword">null</span> if no path is
     *         specified
     */
    public String getRelativePath() {
        return myPath == null ? getName() : myPath;
    }
    
    /**
     * @deprecated use {@link #getRelativePath()} instead.
     */
    public String getPath() {
        return getRelativePath();        
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
     * Gets the lock object for this entry (if it's locked).
     * 
     * @return a lock object or <span class="javakeyword">null</span>
     */
    public SVNLock getLock() {
        return myLock;
    }

    /**
     * This method is used by SVNKit internals and not intended for users (from an API point of view).
     * 
     * @param path this entry's path
     */
    public void setRelativePath(String path) {
        myPath = path;
    }
    
    /**
     * This method is used by SVNKit internals and not intended for users (from an API point of view).
     * 
     * @param message a commit message
     */
    public void setCommitMessage(String message) {
        myCommitMessage = message;
    }

    /**
     * Sets the lock object for this entry (if it's locked).
     * 
     * @param lock a lock object
     */
    public void setLock(SVNLock lock) {
        myLock = lock;
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
        result.append(", lastchangedrev=");
        result.append(myRevision);
        if (myLastAuthor != null) {
            result.append(", lastauthor=");
            result.append(myLastAuthor);
        }
        if (myCreatedDate != null) {
            result.append(", lastchangeddate=");
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
     *            or is not an instance of <b>SVNDirEntry</b>, or this entry's URL is lexicographically 
     *            less than the name of <code>o</code>; 
     *            </li>
     *            <li>1 - if this entry's URL is lexicographically greater than the name of <code>o</code>;
     *            </li>
     *            <li>0 - if and only if <code>o</code> has got the same URL as this one has
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
        String otherURL = ((SVNDirEntry) o).getURL().toString();
        return myURL.toString().compareTo(otherURL);
    }
}