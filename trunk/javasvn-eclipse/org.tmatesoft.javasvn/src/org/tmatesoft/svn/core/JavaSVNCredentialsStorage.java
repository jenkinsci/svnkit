/*
 * Created on Mar 4, 2005
 */
package org.tmatesoft.svn.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.tigris.subversion.javahl.ISVNCredentialsStorage;
import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNSSHCredentials;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;

public class JavaSVNCredentialsStorage implements ISVNCredentialsStorage {
	
	private static URL JAVASN_URL;
	
	static {
		try {
			JAVASN_URL = new URL("http://tmate.org/svn/");
		} catch (MalformedURLException e) {
		}
	}

	public void saveCredentials(SVNRepositoryLocation location, ISVNCredentials credentials) {
		Map info = new HashMap();
		info.put("username", credentials.getName());
		info.put("password", credentials.getPassword());
		if (credentials instanceof ISVNSSHCredentials) {
			info.put("passphrase", ((ISVNSSHCredentials) credentials).getPassphrase());
			info.put("key", ((ISVNSSHCredentials) credentials).getPrivateKeyID());
		}
		
		deleteCredentials(location);
		try {
			String key = getLocationKey(location);
			Platform.addAuthorizationInfo(JAVASN_URL, key, "", info);
		} catch (CoreException e) {
		}
	}

	public void deleteCredentials(SVNRepositoryLocation location) {
		try {
			String key = getLocationKey(location);
			Platform.flushAuthorizationInfo(JAVASN_URL, key, "");
		} catch (CoreException e) {
		}
	}

	public ISVNCredentials getCredentials(SVNRepositoryLocation location) {
		String key = getLocationKey(location);
		Map info = Platform.getAuthorizationInfo(JAVASN_URL, key, "");
		if (info != null) {
			String userName = (String) info.get("username");
			String password = (String) info.get("password");
			String passphrase = (String) info.get("passphrase");
			String keyID = (String) info.get("key");
			return new SVNSimpleCredentialsProvider.SimpleCredentials(userName, password, keyID, passphrase);
		}
		return null;
	}
	
	private String getLocationKey(SVNRepositoryLocation location) {
		return location.getProtocol() + "://" + location.getHost() + ":" + location.getPort();
	}

}
