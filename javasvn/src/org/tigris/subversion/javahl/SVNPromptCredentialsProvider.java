/*
 * Created on 17.02.2005
 *
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProviderEx;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author alex
 */
public class SVNPromptCredentialsProvider implements ISVNCredentialsProviderEx {
	
	private PromptUserPassword myPrompt;
	private boolean myIsAccepted;
	private boolean myIsAllowedtoSave;
	private SVNRepositoryLocation myLocation;	
	private String myInitialUserName;
	private String myInitialPassword;
	
	private static ISVNCredentialsStorage ourStorage;
	
	public SVNPromptCredentialsProvider(PromptUserPassword svnPrompt, String userName, String password) {
		myPrompt = svnPrompt;
		myIsAccepted = true;
		myInitialUserName = userName;
		myInitialPassword = password;
		myInitialUserName = "".equals(myInitialUserName) ? null : myInitialUserName;
	}
	
	public static void setCredentialsStorage(ISVNCredentialsStorage storage) {
		ourStorage = storage;
	}

	public ISVNCredentials nextCredentials(String realm, SVNRepositoryLocation location) {
		if (location != null) {
			realm = realm == null ? location.toCanonicalForm() : realm;
		}
		if (myInitialUserName != null && myInitialPassword != null) {
			// this may be null if
			// a) there is no initial creds.
			// b) initial creds was not accepted.
			return new SVNSimpleCredentialsProvider.SimpleCredentials(myInitialUserName, myInitialPassword, null, null);
		}
		myLocation = location;
		if (ourStorage != null && location != null) {
			ISVNCredentials credentials = ourStorage.getCredentials(location);
			if (credentials != null) {
				DebugLog.log("stored credentials are used");
				return credentials;
			} 
			DebugLog.log("no stored credentials found");
		}
		String userName = myPrompt.getUsername();
		if (userName == null) {
			userName = System.getProperty("user.name");
		}
		String password = myPrompt.getPassword();
		myIsAllowedtoSave = false;
		if (myPrompt instanceof PromptUserPassword3) {
			PromptUserPassword3 prompt3 = (PromptUserPassword3) myPrompt;
			if ((!prompt3.userAllowedSave() || !myIsAccepted)) {
				if (prompt3.prompt(realm, userName, true)) {
					userName = prompt3.getUsername();
					password = prompt3.getPassword();
					myIsAllowedtoSave = prompt3.userAllowedSave();
				} else {
					return null;
				}
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
		DebugLog.log("credentials accepted: " + credentials.getName());
		if (myInitialUserName != null) {
			DebugLog.log("credentials are not stored");
			return;
		}
		if (myIsAllowedtoSave && ourStorage != null && myLocation != null) {
			ourStorage.saveCredentials(myLocation, credentials);
			DebugLog.log("credentials are stored");
			myLocation = null;
			myIsAllowedtoSave = false;
		}
		myIsAccepted = true;
	}
	public void notAccepted(ISVNCredentials credentials, String failureReason) {
		DebugLog.log("credentials NOT accepted: " + credentials.getName());
		if (myInitialUserName != null) {
			DebugLog.log("stored credentials are NOT deleted");
			myInitialUserName = null;
			myInitialPassword = null;
			return;
		}
		if (ourStorage != null && myLocation != null) {
			ourStorage.deleteCredentials(myLocation);
			DebugLog.log("credentials are deleted from storage");
			myLocation = null;
			myIsAllowedtoSave = false;
		}
		myIsAccepted = false;
	}
	public void reset() {
	}

}
