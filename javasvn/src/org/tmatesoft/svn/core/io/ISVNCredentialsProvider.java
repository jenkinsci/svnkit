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
 * This interface is a provider of clients' credentials used by the Repository 
 * Access Layer to authenticate them. 
 * 
 * <p>
 * When the server process receives a client request, it typically demands that the
 * client identify itself. It issues an authentication challenge to the client, and
 * the client responds by providing <b>credentials</b> back to the server. Once 
 * authentication is complete, the server responds with the original information
 * the client asked for. Notice that this system is different from systems like 
 * <i>CVS</i>, where the client pre-emptively offers credentials 
 * (“logs in”) to the server before ever making a request. In <i>Subversion</i>, 
 * the server “pulls” credentials by challenging the client at the appropriate 
 * moment, rather than the client “pushing” them. 
 * 
 * <p>
 * This interface implementation is supplied to an <code>SVNRepository</code>
 * instance used as a current session object to communicate with a repository.
 * Later on the Repository Access Layer inner engine retrieves this provider from
 * the <code>SVNRepository</code> and calling the provider's interface methods
 * obtains all the client's credentials (<code>ISVNCredentials</code> 
 * implementations) provided (if any).
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNCredentialsProviderEx
 * @see 	ISVNCredentials
 * @see 	ISVNSSHCredentials
 * @see 	SVNRepository#setCredentialsProvider(ISVNCredentialsProvider)
 * @see 	SVNRepository#getCredentialsProvider()
 */
public interface ISVNCredentialsProvider {
	
    /**
	 * Gets the next provided client's credentials (if any). 
	 * @param realm		a name that defines which authentication namespace of the 
	 * 					repository will be used when connecting to it 
	 * @return			next client's credentials as an implementation of the 
	 * 					<code>ISVNCredentials</code> interface
	 * @see				ISVNCredentials
	 */
	public ISVNCredentials nextCredentials(String realm);
	
	/**
	 * Notifies that the given <code>credentials</code>  were accepted
	 * by the repository server (the user was authenticated successfully).
	 * 
	 * @param credentials	client's credentials that were accepted by the server
	 * @see					#notAccepted(ISVNCredentials, String)	
	 */
	public void accepted(ISVNCredentials credentials);
	
	/**
	 * Notifies that the given <code>credentials</code> were not accepted by the
	 * repository server and provides a failure reason string (the user wasn't 
	 * authenticated).
	 * 
	 * @param credentials		client's credentials which were declined by the 
	 * 							repository server
	 * @param failureReason		the string that describes why it has happend
	 * @see						#accepted(ISVNCredentials)
	 */
	public void notAccepted(ISVNCredentials credentials, String failureReason);
    
	/**
	 * Resets to the very begginning of the container of all the credentials (if
	 * more than one) provided by the client. 
	 *
	 */
    public void reset();
}
