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
		if (location != null) {
			realm = realm == null ? location.toCanonicalForm() : realm;
		}
		String userName = myPrompt.getUsername();
		if (userName == null) {
			userName = System.getProperty("user.name");
		}
		String password = myPrompt.getPassword();
		if (myPrompt instanceof PromptUserPassword3) {
			PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
			if (!prompt3.userAllowedSave() && prompt3.prompt(realm, userName, true)) {
				userName = prompt3.getUsername();
				password = prompt3.getPassword();
			}
		} else if (!myPrompt.prompt(realm, userName)) {
			return null;
		}
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
