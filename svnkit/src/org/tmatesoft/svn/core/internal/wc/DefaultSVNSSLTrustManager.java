package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class DefaultSVNSSLTrustManager implements X509TrustManager {

	private SVNURL myURL;
	private DefaultSVNAuthenticationManager myAuthManager;

	private X509Certificate[] myTrustedCerts;
	private String myRealm;
	private File myAuthDirectory;
	private boolean myIsUseKeyStore;
	private File[] myServerCertFiles;

	public DefaultSVNSSLTrustManager(File authDir, SVNURL url, File[] serverCertFiles, boolean useKeyStore, DefaultSVNAuthenticationManager authManager) {
		myURL = url;
		myAuthDirectory = authDir;
		myRealm = "https://" + url.getHost() + ":" + url.getPort();
		myAuthManager = authManager;
		myIsUseKeyStore = useKeyStore;
		myServerCertFiles = serverCertFiles;
	}

	private void init() {
		if (myTrustedCerts != null) {
			return;
		}
		Collection trustedCerts = new ArrayList();
		// load trusted certs from files.
		for (int i = 0; i < myServerCertFiles.length; i++) {
			X509Certificate cert = loadCertificate(myServerCertFiles[i]);
			if (cert != null) {
				trustedCerts.add(cert);
			}
		}
		// load from 'default' keystore
		if (myIsUseKeyStore) {
			try {
				KeyStore keyStore = KeyStore.getInstance("JKS");
				if (keyStore != null) {
					String path = System.getProperty("java.home") + "/lib/security/cacerts";
					path = path.replace('/', File.separatorChar);
					File file = new File(path);
					InputStream is = null;
					try {
						if (file.isFile() && file.canRead()) {
							is = SVNFileUtil.openFileForReading(file);
						}
						keyStore.load(is, null);
					}
					catch (NoSuchAlgorithmException e) {
					}
					catch (CertificateException e) {
					}
					catch (IOException e) {
					}
					catch (SVNException e) {
					}
					finally {
						SVNFileUtil.closeFile(is);
					}
					PKIXParameters params = new PKIXParameters(keyStore);
					for (Iterator anchors = params.getTrustAnchors().iterator(); anchors.hasNext();) {
						TrustAnchor ta = (TrustAnchor)anchors.next();
						X509Certificate cert = ta.getTrustedCert();
						if (cert != null) {
							trustedCerts.add(cert);
						}
					}

				}
			}
			catch (KeyStoreException e) {
			}
			catch (InvalidAlgorithmParameterException e) {
			}
		}
		myTrustedCerts = (X509Certificate[])trustedCerts.toArray(new X509Certificate[trustedCerts.size()]);
	}

	public X509Certificate[] getAcceptedIssuers() {
		init();
		return myTrustedCerts;
	}

	public void checkClientTrusted(X509Certificate[] certs, String arg1) throws CertificateException {
	}

	public void checkServerTrusted(X509Certificate[] certs, String algorithm) throws CertificateException {
		if (certs != null && certs.length > 0 && certs[0] != null) {
			String data = SVNBase64.byteArrayToBase64(certs[0].getEncoded());
			String stored = (String) myAuthManager.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
			if (data.equals(stored)) {
				return;
			}
			stored = getStoredServerCertificate(myRealm);
			if (data.equals(stored)) {
				return;
			}
			ISVNAuthenticationProvider authProvider = myAuthManager.getAuthenticationProvider();
			int failures = getServerCertificateFailures(certs[0]);
			// compose bit mask.
			// 8 is default
			// check dates for 1 and 2
			// check host name for 4
			if (authProvider != null) {
				boolean store = myAuthManager.isAuthStorageEnabled();
				int result = authProvider.acceptServerAuthentication(myURL, myRealm, certs[0], store);
				if (result == ISVNAuthenticationProvider.ACCEPTED && store) {
					try {
						storeServerCertificate(myRealm, data, failures);
					} catch (SVNException e) {
						throw new SVNSSLUtil.CertificateNotTrustedException("svn: Server SSL ceritificate for '" + myRealm + "' cannot be saved");
					}
				}
				if (result != ISVNAuthenticationProvider.REJECTED) {
					myAuthManager.getRuntimeAuthStorage().putData("svn.ssl.server", myRealm, data);
					return;
				}
				throw new SVNSSLUtil.CertificateNotTrustedException("svn: Server SSL ceritificate for '" + myRealm + "' rejected");
			}
			// like as tmp. accepted.
			return;
		}
	}

	private int getServerCertificateFailures(X509Certificate cert) {
		int mask = 8;
		Date time = new Date(System.currentTimeMillis());
		if (time.before(cert.getNotBefore())) {
			mask |= 1;
		}
		if (time.after(cert.getNotAfter())) {
			mask |= 2;
		}
		String hostName = cert.getSubjectDN().getName();
		int index = hostName.indexOf("CN=") + 3;
		if (index >= 0) {
			hostName = hostName.substring(index);
			if (hostName.indexOf(' ') >= 0) {
				hostName = hostName.substring(0, hostName.indexOf(' '));
			}
			if (hostName.indexOf(',') >= 0) {
				hostName = hostName.substring(0, hostName.indexOf(','));
			}
		}
		String realHostName = myURL.getHost();
		if (!realHostName.equals(hostName)) {
			try {
			    Collection altNames = cert.getSubjectAlternativeNames();
			    for (Iterator names = altNames.iterator(); names.hasNext();) {
                    Object nameList = names.next();
                    if (nameList instanceof Collection && ((Collection) nameList).size() >= 2) {
                        Object[] name = ((Collection) nameList).toArray();
                        Object type = name[0];
                        Object host = name[1];
                        if (type instanceof Integer && host instanceof String) {
                            if (((Integer) type).intValue() == 2 && host.equals(realHostName)) {
                                return mask;
                            }
                        }
                    }
                }
            } catch (CertificateParsingException e) {
            }
            mask |= 4;
		}
		return mask;
	}

	private String getStoredServerCertificate(String realm) {
		File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
		if (!file.isFile()) {
			return null;
		}
		SVNWCProperties props = new SVNWCProperties(file, "");
		try {
			String storedRealm = props.getPropertyValue("svn:realmstring");
			if (!realm.equals(storedRealm)) {
				return null;
			}
			return props.getPropertyValue("ascii_cert");
		}
		catch (SVNException e) {
		}
		return null;
	}

	private void storeServerCertificate(String realm, String data, int failures) throws SVNException {
		myAuthDirectory.mkdirs();

		File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
		SVNWCProperties props = new SVNWCProperties(file, "");
		props.delete();
		try {
			props.setPropertyValue("ascii_cert", data);
			props.setPropertyValue("svn:realmstring", realm);
			props.setPropertyValue("failures", Integer.toString(failures));

			SVNFileUtil.setReadonly(props.getFile(), false);
		}
		catch (SVNException e) {
			props.delete();
		}
	}

	public static X509Certificate loadCertificate(File pemFile) {
		InputStream is = null;
		try {
			is = SVNFileUtil.openFileForReading(pemFile);
		}
		catch (SVNException e) {
			return null;
		}
		try {
			CertificateFactory factory = CertificateFactory.getInstance("X509");
			return (X509Certificate)factory.generateCertificate(is);
		}
		catch (CertificateException e) {
			return null;
		}
		finally {
			SVNFileUtil.closeFile(is);
		}
	}
}