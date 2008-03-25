/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public final class HTTPSSLKeyManager implements X509KeyManager {

	public static KeyManager[] loadClientCertificate(File clientCertFile, String clientCertPassword) throws SVNException {
		char[] passphrase = null;
		if (clientCertPassword != null) {
			passphrase = clientCertPassword.toCharArray();
		}
		KeyStore keyStore = null;
		final InputStream is = SVNFileUtil.openFileForReading(clientCertFile);
		try {
			keyStore = KeyStore.getInstance("PKCS12");
			if (keyStore != null) {
				keyStore.load(is, passphrase);
			}
		}
		catch (Throwable th) {
			SVNDebugLog.getDefaultLog().info(th);
			throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, th.getMessage(), null, SVNErrorMessage.TYPE_ERROR, th), th);
		}
		finally {
			SVNFileUtil.closeFile(is);
		}
		KeyManagerFactory kmf = null;
		KeyManager[] result = null;
		if (keyStore != null) {
			try {
				kmf = KeyManagerFactory.getInstance("SunX509");
				if (kmf != null) {
					kmf.init(keyStore, passphrase);
					result = kmf.getKeyManagers();
				}
			}
			catch (Throwable th) {
				SVNDebugLog.getDefaultLog().info(th);
				throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, th.getMessage()), th);
			}
		}
		return result;
	}

	private final ISVNAuthenticationManager authenticationManager;
	private final String realm;
	private final SVNURL url;

	private KeyManager[] myKeyManagers;
	private SVNSSLAuthentication myAuthentication;
	private Exception myException;

	public HTTPSSLKeyManager(ISVNAuthenticationManager authenticationManager, String realm, SVNURL url) {
		this.authenticationManager = authenticationManager;
		this.realm = realm;
		this.url = url;
	}

	public String[] getClientAliases(String location, Principal[] principals) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final String[] clientAliases = keyManager.getClientAliases(location, principals);
			if (clientAliases != null) {
				return clientAliases;
			}
		}

		return null;
	}

	public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final String clientAlias = keyManager.chooseClientAlias(strings, principals, socket);
			if (clientAlias != null) {
				return clientAlias;
			}
		}

		return null;
	}

	public String[] getServerAliases(String location, Principal[] principals) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final String[] serverAliases = keyManager.getServerAliases(location, principals);
			if (serverAliases != null) {
				return serverAliases;
			}
		}

		return null;
	}

	public String chooseServerAlias(String location, Principal[] principals, Socket socket) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final String serverAlias = keyManager.chooseServerAlias(location, principals, socket);
			if (serverAlias != null) {
				return serverAlias;
			}
		}

		return null;
	}

	public X509Certificate[] getCertificateChain(String location) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final X509Certificate[] certificateChain = keyManager.getCertificateChain(location);
			if (certificateChain != null) {
				return certificateChain;
			}
		}

		return null;
	}

	public PrivateKey getPrivateKey(String string) {
		if (!initializeNoException()) {
			return null;
		}

		for (Iterator it = getX509KeyManagers(myKeyManagers).iterator(); it.hasNext();) {
			final X509KeyManager keyManager = (X509KeyManager)it.next();
			final PrivateKey privateKey = keyManager.getPrivateKey(string);
			if (privateKey != null) {
				return privateKey;
			}
		}

		return null;
	}

	public Exception getException() {
		return myException;
	}

	public void acknowledgeAndClearAuthentication(SVNErrorMessage errorMessage) throws SVNException {
		if (myAuthentication != null) {
			authenticationManager.acknowledgeAuthentication(errorMessage == null, ISVNAuthenticationManager.SSL, realm, errorMessage, myAuthentication);
		}

		myAuthentication = null;

		if (errorMessage != null) {
			myKeyManagers = null;
		}

		final Exception exception = myException;
		myException = null;
		if (exception instanceof SVNException) {
			throw (SVNException)exception;
		}
		else if (exception != null) {
			throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE, exception);
		}
	}

	private boolean initializeNoException() {
		try {
			final boolean result = initialize();
			myException = null;
			return result;
		}
		catch (Exception exception) {
			myException = exception;
			return false;
		}
	}

	private boolean initialize() throws SVNException {
		if (myKeyManagers != null) {
			return true;
		}

		boolean isFirstAuthentication = true;
		for (; ;) {
			if (isFirstAuthentication) {
				myAuthentication = (SVNSSLAuthentication)authenticationManager.getFirstAuthentication(ISVNAuthenticationManager.SSL, realm, url);
				isFirstAuthentication = false;
			}
			else {
				myAuthentication = (SVNSSLAuthentication)authenticationManager.getNextAuthentication(ISVNAuthenticationManager.SSL, realm, url);
			}

			if (myAuthentication == null) {
				SVNErrorManager.cancel("SSL authentication with client certificate cancelled");
			}

			final KeyManager[] keyManagers;
			try {
				keyManagers = loadClientCertificate(myAuthentication.getCertificateFile(), myAuthentication.getPassword());
			}
			catch (SVNException ex) {
				final SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", new Object[] { ex.getMessage() }, SVNErrorMessage.TYPE_ERROR, ex.getCause());
				authenticationManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.SSL, realm, sslErr, myAuthentication);
				continue;
			}

			myKeyManagers = keyManagers;
			return true;
		}
	}

	private static List getX509KeyManagers(KeyManager[] keyManagers) {
		final List x509KeyManagers = new ArrayList();
		for (int index = 0; index < keyManagers.length; index++) {
			final KeyManager keyManager = keyManagers[index];
			if (keyManager instanceof X509KeyManager) {
				x509KeyManagers.add(keyManager);
			}
		}
		return x509KeyManagers;
	}
}