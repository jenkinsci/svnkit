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

import java.util.Date;


/**
 * <code>SVNDirEntry</code> represents a directory entry informational wrapper.
 * 
 * <p>
 * Every directory or file added, updated, deleted (and so on) needs to be versioned.
 * This class provides such an informational wrapper for every entry ever handled by the
 * repository. All the versioned repository contents are considered as directory entries,
 * since all the files and directories are located inside the Subversion repository root 
 * directory.
 * 
 * <p>
 * <code>SVNDirEntry</code> is responsible for keeping an entry name, 
 * entry kind (is it a file or directory or maybe unknown type), entry size (in bytes);
 * it knows if the entry has any properties, it remembers the date when the entry was created and 
 * its first revision when it appeared in the repository as well as the last person who updated
 * the item. All this information is binded together in one class.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNDirEntryHandler
 */
public class SVNDirEntry {

    private String myName;
    private SVNNodeKind myKind;
    private long mySize;
    private boolean myHasProperties;
    private long myFirstRevision;
    private Date myCreatedDate;
    private String myLastAuthor;
    
    /**
     * Constructs an instance of <code>SVNDirEntry</code> given a directory entry's 
     * name, kind, size, flag saying if it has properties, revision when it was last
     * modified, creation date, and the name of a person who last modified it.
     * 
     * @param name 			the entry name
     * @param kind 			the node kind for the entry
     * @param size 			the entry size in bytes
     * @param hasProperties if the entry has properties
     * @param firstRevision the first revision of the entry
     * @param createdDate 	the date the entry was created at
     * @param lastAuthor 	the person who was the recent to update the entry
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
     * Gets the the directory entry name
     * 
     * @return 	the name of this entry
     */
    public String getName() {
        return myName;
    }
    
    /**
     * Retrieves the entry syze in bytes.
     * 
     * @return 	the size of this entry in bytes
     */
    public long size() {
        return mySize;
    }
    
    /**
     * Tells if the entry has any properties.
     * 
     * @return 	<code>true</code> if has, <code>false</code> - otherwise
     */
    public boolean hasProperties() {
        return myHasProperties;
    }
    
    /**
     * Retrieves the entry node kind - whether it's a directory or file, for instance.
     * 
     * @return  the node kind of this entry 
     * @see 	SVNNodeKind
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Returns the date the entry was created at.
     * 
     * @return 	the creation date
     */
    public Date getDate() {
        return myCreatedDate;
    }
    
    /**
     * Retrieves the revision
     * at which the entry was last modified in the repository.
     * 
     * @return 	the last-modified revision number of this entry 
     */
    public long getRevision() {
        return myFirstRevision;
    }
    
    /**
     * Retrieves the name of the person who was the last to update
     * this entry in the repository.
     * 
     * @return 	the last author's name.
     */
    public String getAuthor() {
        return myLastAuthor;
    }
    
    /**
     * Sets a <code>name</code> for the entry. 
     * 
     * @param name 	a directory entry name. 
     */
    public void setName(String name) {
        myName = name;
    }
    
    /**
     * Represents the current <code>SVNDirEntry</code> object as a string
     * like this way: "name=MyFile.txt, kind=<file>, size=1024, 
     * hasProperties=true, creation-rev=1, lastAuthor=Eric, 
     * creation-date=2004.07.10 AD at 15:08:56 PDT".
     * 
     * @return 	a string representation of this directory entry.
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
}
