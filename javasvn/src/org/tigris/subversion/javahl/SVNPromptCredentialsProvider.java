/*
 * Created on 17.02.2005
 *
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProviderEx;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;

/**
 * @author alex
 */
class SVNPromptCredentialsProvider implements ISVNCredentialsProviderEx {
	
	private PromptUserPassword myPrompt;
	
	public SVNPromptCredentialsProvider(PromptUserPassword svnPrompt) {
		myPrompt = svnPrompt;
	}

	public ISVNCredentials nextCredentials(String realm, SVNRepositoryLocation location) {
		if (!myPrompt.prompt(realm, System.getProperty("user.name"))) {
			return null;
		}
		String userName = myPrompt.getUsername();
		String password = myPrompt.getPassword();
		return new SVNSimpleCredentialsProvider.SimpleCredentials(userName, password
				, null, null);
	}
	public ISVNCredentials nextCredentials(String realm) {
		return null;
	}
	public void accepted(ISVNCredentials credentials) {
	}
	public void notAccepted(ISVNCredentials credentials, String failureReason) {
	}
	public void reset() {
	}

}
