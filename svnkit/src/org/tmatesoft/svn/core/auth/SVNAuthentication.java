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
package org.tmatesoft.svn.core.auth;

/**
 * The <b>SVNAuthentication</b> is the base class that represents user 
 * credentials. <b>SVNAuthentication</b> provides only a username. Other 
 * kinds of user credentials extend this base class and add their own specific 
 * information.
 * 
 * <p>
 * User credentials used by <b>SVNRepository</b> drivers to authenticate 
 * a user to a repository server, are provided to those drivers by 
 * <b>ISVNAuthenticationManager</b> implementations.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     SVNPasswordAuthentication
 * @see     SVNSSHAuthentication
 * @see     ISVNAuthenticationManager
 * @see     org.tmatesoft.svn.core.io.SVNRepository
 */
public class SVNAuthentication {
    
    private String myUserName;
    private boolean myIsStorageAllowed;
    private String myKind;
    
    /**
     * Creates a username user credential object given a username. 
     * 
     * @param kind              a credential kind
     * @param userName          a repository account username 
     * @param storageAllowed    if <span class="javakeyword">true</span> then
     *                          this credential is allowed to be stored in the 
     *                          global auth cache, otherwise not
     */
    public SVNAuthentication(String kind, String userName, boolean storageAllowed) {
        myUserName = userName;
        myIsStorageAllowed = storageAllowed;
        myKind = kind;
    }
    
    /**
     * Reurns the username. 
     * 
     * @return a repository account username
     */
    public String getUserName() {
        return myUserName;
    }
    
    /**
     * Says if this credential may be cached in the global auth cache.
     *  
     * @return <span class="javakeyword">true</span> if this credential
     *         may be stored, <span class="javakeyword">false</span> if may not
     */
    public boolean isStorageAllowed() {
        return myIsStorageAllowed;
    }
    
    /**
     * Returns a credential kind for which this authentication 
     * credential is used. 
     * 
     * @return a credential kind
     */
    public String getKind() {
        return myKind;
    }

}
