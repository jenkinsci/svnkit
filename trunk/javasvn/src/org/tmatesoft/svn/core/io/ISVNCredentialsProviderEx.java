/*
 * Created on Feb 7, 2005
 */
package org.tmatesoft.svn.core.io;

/**
 * @author alex
 */
public interface ISVNCredentialsProviderEx extends ISVNCredentialsProvider {
	
	public ISVNCredentials nextCredentials(String realm, SVNRepositoryLocation location);
	
}
