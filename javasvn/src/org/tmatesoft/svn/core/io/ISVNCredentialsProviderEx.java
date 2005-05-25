/*
 * Created on Feb 7, 2005
 */
package org.tmatesoft.svn.core.io;

/**
 * This interface is an extended form of the <code>ISVNCredentialsProvider</code> 
 * interface. It adds a declaration for an overloaded version of the 
 * <code>nextCredentials()</code> that is used to retrieve the next credentials provided
 * by the client.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		ISVNCredentialsProvider
 */
public interface ISVNCredentialsProviderEx extends ISVNCredentialsProvider {
	
    /**
	 * Gets the next provided client's credentials (if any).
	 * 
	 * @param realm			a name that defines which authentication namespace of the 
	 * 						repository will be used when connecting to it 
	 * @param location		This is a  repository <code>URL</code> incapsulated by the  
	 * 						<code>SVNRepositoryLocation</code> class that tells what
	 * 						repository exactly that <code>realm</code> belongs to. 
	 * @return				next client's credentials as an implementation of the 
	 * 						<code>ISVNCredentials</code> interface
	 * @see					ISVNCredentialsProvider#nextCredentials(String)
	 */
	public ISVNCredentials nextCredentials(String realm, SVNRepositoryLocation location);
	
}
