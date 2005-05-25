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
 * The <code>ISVNCredentials</code> is an interface that is used to get 
 * such client's cridentials as his name and password by user credentials provider
 * interface - <code>ISVNCredentialsProvider</code>.
 * 
 * <p>
 * A Subversion repository can be configured to let clients manipulate with it only 
 * being authenticated. An implementation of this interface incapsulates a client's 
 * authentication information per one repository account (name/password) and provides
 * it via implemented methods.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see 	ISVNCredentialsProvider
 * @see 	SVNRepository#setCredentialsProvider(ISVNCredentialsProvider)
 * @see		SVNSimpleCredentialsProvider
 * @see		ISVNSSHCredentials		
 */
public interface ISVNCredentials {
	/**
	 * Gets a user's account name.
	 * 
	 * @return user's name 
	 */
	public String getName();
	
	/**
	 * Gets a user's account password.
	 * 
	 * @return user's password
	 */
	public String getPassword();

}
