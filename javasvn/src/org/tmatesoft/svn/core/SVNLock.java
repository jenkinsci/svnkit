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

import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * The public class <code>SVNLock</code> incapsulates information about a 
 * file lock in a repository. A Subversion repository user can lock any 
 * versioned file in a repository, thus preventing someone else from 
 * modifying the same file. Of course, the user must be authenticated in
 * the repository because locking can not be anonymous. In this way, the
 * locked file can only be checked out by other users, but can not be 
 * changed (no one else can commit his changes) until the lock author
 * unlocks it or the lock expires itself (in a definite period  - if an 
 * expiration date is set). Also the locked file can be forcedly relocked
 * by another user (it means the previous user lock is removed and the file
 * is locked by a next user).
 * 
 * @version 	1.0 
 * @author 		TMate Software Ltd.
 * @since 		SVN 1.2
 * @see 		SVNRepository
 */
public class SVNLock {
    
    private String myPath;
    private String myID;
    private String myOwner;
    private String myComment;
    
    private Date myCreationDate;
    private Date myExpirationDate;
    
    
    /**
     * <p>
     * Constructs a <code>SVNLock</code> object.
     * 
     * 
     * @param path 			the file path, relative to the repository root 
     * 						directory.
     * @param id 			a string token identifying the lock.
     * @param owner 		the person whom this lock belongs to. 
     * @param comment 		optional: either it is a string describing
     * 						the lock or it is <code>null</code>.
     * @param created 		a <code>Date</code> instance that corresponds
     * 						to the moment in time when the lock was created.
     * @param expires 		optional: a <code>Date</code> instance that represents
     * 						the moment when the lock expires (i.e. the file is 
     * 						unlocked). If the lock is not to expire then this 
     * 						parameter is <code>null</code>.
     */
    public SVNLock(String path, String id, String owner, String comment, Date created, Date expires) {
        myPath = path;
        myID = id;
        myOwner = owner;
        myComment = comment;
        myCreationDate = created;
        myExpirationDate = expires;
    }
    
    
    /**
     * <p>
     * Gets the lock comment. NOTE: it can be <code>null</code> if
     * no comment was provided for the lock.
     * 
     * @return 		a comment string or <code>null</code>.
     */
    public String getComment() {
        return myComment;
    }
    
    
    /**
     * <p>
     * Gets the creation date of the lock.
     * 
     * @return 		a <code>Date</code> instance that is the time moment when the
     * 				lock was created.
     */
    public Date getCreationDate() {
        return myCreationDate;
    }
    
    
    /**
     * <p>
     * Gets the expiration date of the lock (when the lock just expires itself).
     * NOTE: it can be <code>null</code> if no expiration date was specified for
     * the lock.
     * 
     * @return 		a <code>Date</code> instance that is the time moment when the
     * 				lock expires.
     */
    public Date getExpirationDate() {
        return myExpirationDate;
    }
    
    
    /**
     * <p>
     * Gets the lock id that is a token identifying this lock. 
     * 
     * @return 		 the string that is the lock id token.
     */
    public String getID() {
        return myID;
    }
    /**
     * <p>
     * Gets the lock owner. No file in a repository can be locked anonymously,
     * so every lock has such attribute as its owner.
     * 
     * @return		the name of the user who possesses this lock. 
     */
    public String getOwner() {
        return myOwner;
    }
    /**
     * <p>
     * Gets the path of the file which was locked. The path is relative to the
     * repository root directory.
     * 
     * @return		the string that is the path of the file in the repository that
     * 				was locked.
     */
    public String getPath() {
        return myPath;
    }
    /**
     * <p>
     * Gets a string representation of this <code>SVNLock</code> object.
     * The string, that is returned, includes complete information about 
     * the file lock.  
     * 
     * @return		a string representation of this <code>SVNLock</code> object.
     */
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("path=");
        result.append(myPath);
        result.append(", token=");
        result.append(myID);
        result.append(", owner=");
        result.append(myOwner);
        if (myComment != null) {
            result.append(", comment=");
            result.append(myComment);
        }
        result.append(", created=");
        result.append(myCreationDate);
        if (myExpirationDate != null) {
            result.append(", expires=");
            result.append(myExpirationDate);
        }        
        return result.toString();
    }
}
