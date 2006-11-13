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
 * The <b>SVNUserNameAuthentication</b> class represents a simple 
 * authentication credential class that uses only a username to 
 * authenticate a user. Used along with the 
 * {@link ISVNAuthenticationManager#USERNAME} credential kind.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNUserNameAuthentication extends SVNAuthentication {
    /**
     * Creates a username authentication credential.
     * 
     * @param userName         a user name
     * @param storageAllowed   if <span class="javakeyword">true</span> then
     *                         this credential is allowed to be stored in the 
     *                         global auth cache, otherwise not
     */
    public SVNUserNameAuthentication(String userName, boolean storageAllowed) {
        super(ISVNAuthenticationManager.USERNAME, userName, storageAllowed);
    }
}
