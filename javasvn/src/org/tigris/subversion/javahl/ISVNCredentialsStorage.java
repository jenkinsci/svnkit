/*
 * Created on Mar 4, 2005
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

public interface ISVNCredentialsStorage {

	public void saveCredentials(SVNRepositoryLocation location, ISVNCredentials credentials);

	public void deleteCredentials(SVNRepositoryLocation location);
	
	public ISVNCredentials getCredentials(SVNRepositoryLocation location);
}
