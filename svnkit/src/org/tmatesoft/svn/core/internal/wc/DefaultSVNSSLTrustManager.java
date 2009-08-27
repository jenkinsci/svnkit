package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class DefaultSVNSSLTrustManager implements X509TrustManager {

	private SVNURL myURL;
	private DefaultSVNAuthenticationManager myAuthManager;

	private X509Certificate[] myTrustedCerts;
	private String myRealm;
	private File myAuthDirectory;
	private boolean myIsUseKeyStore;
	private File[] myServerCertFiles;

    private X509TrustManager[] myDefaultTrustManagers;

	public DefaultSVNSSLTrustManager(File authDir, SVNURL url, File[] serverCertFiles, boolean useKeyStore, DefaultSVNAuthenticationManager authManager) {
		myURL = url;
		myAuthDirectory = authDir;
		myRealm = "https://" + url.getHost() + ":" + url.getPort();
		myAuthManager = authManager;
		myIsUseKeyStore = useKeyStore;
		myServerCertFiles = serverCertFiles;
	}

    private X509TrustManager[] getDefaultTrustManagers() {
        if (myDefaultTrustManagers == null && myIsUseKeyStore) {
            myDefaultTrustManagers = initDefaultTrustManagers();
        }
        return myDefaultTrustManagers;
    }

    private X509TrustManager[] initDefaultTrustManagers() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509", "SunJSSE");
            tmf.init((KeyStore) null);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            if (trustManagers == null || trustManagers.length == 0) {
                return null;
            }
            List x509TrustManagers = new ArrayList();
            for (int i = 0; i < trustManagers.length; i++) {
                TrustManager trustManager = trustManagers[i];
                if (trustManager instanceof X509TrustManager) {
                    x509TrustManagers.add(trustManager);
                }
            }
            return (X509TrustManager[]) x509TrustManagers.toArray(new X509TrustManager[x509TrustManagers.size()]);
        } catch (NoSuchAlgorithmException e) {
            SVNDebugLog.getDefaultLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
        } catch (NoSuchProviderException e) {
            SVNDebugLog.getDefaultLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
        } catch (KeyStoreException e) {
            SVNDebugLog.getDefaultLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
        }
        return null;
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

        X509TrustManager[] trustManagers = getDefaultTrustManagers();
        for (int i = 0; trustManagers != null && i < trustManagers.length; i++) {
            X509TrustManager trustManager = trustManagers[i];
            X509Certificate[] acceptedCerts = trustManager.getAcceptedIssuers();
            for (int c = 0; acceptedCerts != null && c < acceptedCerts.length; c++) {
                X509Certificate cert = acceptedCerts[c];
                trustedCerts.add(cert);
            }
        }
        myTrustedCerts = (X509Certificate[]) trustedCerts.toArray(new X509Certificate[trustedCerts.size()]);
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
			int failures = SVNSSLUtil.getServerCertificateFailures(certs[0], myURL.getHost());
			// compose bit mask.
			// 8 is default
			// check dates for 1 and 2
			// check host name for 4
			if (authProvider != null) {
				boolean store = myAuthManager.isAuthStorageEnabled(myURL);
                boolean trustServer = checkServerTrustedByDefault(certs, algorithm);
                int result;
                if (trustServer) {
                    result = ISVNAuthenticationProvider.ACCEPTED;
                } else {
                    result = authProvider.acceptServerAuthentication(myURL, myRealm, certs[0], store);
                }
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
        }
	}

    private boolean checkServerTrustedByDefault(X509Certificate[] certs, String algorithm) {
        X509TrustManager[] trustManagers = getDefaultTrustManagers();
        if (trustManagers == null) {
            return false;
        }
        for (int i = 0; i < trustManagers.length; i++) {
            X509TrustManager trustManager = trustManagers[i];
            boolean trusted = true;
            try {
                trustManager.checkServerTrusted(certs, algorithm);
            } catch (CertificateException e) {
                trusted = false;
            }
            if (trusted) {
                return true;
            }
        }
        return false;
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
            throw e;
		}
	}

	public static X509Certificate loadCertificate(File pemFile) {
		InputStream is = null;
		try {
			is = SVNFileUtil.openFileForReading(pemFile, SVNLogType.WC);
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