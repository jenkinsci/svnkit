/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNSSLManager implements ISVNSSLManager {

    private SVNURL myURL;
    private File myClientCertFile;
    private String myClientCertPassword;
    private DefaultSVNAuthenticationManager myAuthManager;
    
    private KeyManager[] myKeyManagers;
    private X509Certificate[] myTrustedCerts;
    private boolean myIsKeyManagerCreated;
    private String myRealm;
    private File myAuthDirectory;
    private boolean myIsUseKeyStore;
    private File[] myServerCertFiles;
    private boolean myIsPromptForClientCert;
    private SVNSSLAuthentication myClientAuthentication;

    public DefaultSVNSSLManager(File authDir, SVNURL url, 
            File[] serverCertFiles, boolean useKeyStore, File clientFile, String clientPassword,
            boolean promptForClientCert,
            DefaultSVNAuthenticationManager authManager) {
        myURL = url;
        myAuthDirectory = authDir;
        myClientCertFile = clientFile;
        myClientCertPassword = clientPassword;
        myIsPromptForClientCert = promptForClientCert;
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
                    } catch (NoSuchAlgorithmException e) {
                    } catch (CertificateException e) {
                    } catch (IOException e) {
                    } catch (SVNException e) { 
                    } finally {
                        SVNFileUtil.closeFile(is);
                    }
                    PKIXParameters params = new PKIXParameters(keyStore);
                    for (Iterator anchors = params.getTrustAnchors().iterator(); anchors.hasNext(); ) {
                        TrustAnchor ta = (TrustAnchor) anchors.next();
                        X509Certificate cert = ta.getTrustedCert();
                        if (cert != null) {
                            trustedCerts.add(cert);
                        }
                    }
                    
                }
            } catch (KeyStoreException e) {
            } catch (InvalidAlgorithmParameterException e) {
            }
        }
        myTrustedCerts = (X509Certificate[]) trustedCerts.toArray(new X509Certificate[trustedCerts.size()]);
    }

    public SSLContext getSSLContext() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("SSLv3");
            context.init(getKeyManagers(), new TrustManager[] {new X509TrustManager() { 
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
                                    throw new CertificateException("svn: Server SSL ceritificate for '" + myRealm + "' cannot be saved");
                                }
                            }
                            if (result != ISVNAuthenticationProvider.REJECTED) {
                                myAuthManager.getRuntimeAuthStorage().putData("svn.ssl.server", myRealm, data);
                                return;
                            } 
                            throw new CertificateException("svn: Server SSL ceritificate for '" + myRealm + "' rejected");
                        } 
                        // like as tmp. accepted.
                        return;
                    }
                }                    
            }}, null);
            return context;
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e.getMessage());
        }            
    }

    public void acknowledgeSSLContext(boolean accepted, SVNErrorMessage errorMessage) {
        if (!accepted) {
            myIsKeyManagerCreated = false;
            myKeyManagers = null;
            myTrustedCerts = null;
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
            mask |= 4;
        }
        return mask;
    }
    
    private String getStoredServerCertificate(String realm) {
        File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
        if (!file.isFile()) {
            return null;
        }
        SVNProperties props = new SVNProperties(file, "");
        try {
            String storedRealm = props.getPropertyValue("svn:realmstring");
            if (!realm.equals(storedRealm)) {
                return null;
            }
            return props.getPropertyValue("ascii_cert");
        } catch (SVNException e) {
        }
        return null;
    }

    private void storeServerCertificate(String realm, String data, int failures) throws SVNException {
        myAuthDirectory.mkdirs();
        
        File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
        SVNProperties props = new SVNProperties(file, "");
        props.delete();
        try {
            props.setPropertyValue("ascii_cert", data);
            props.setPropertyValue("svn:realmstring", realm);
            props.setPropertyValue("failures", Integer.toString(failures));
            
            SVNFileUtil.setReadonly(props.getFile(), false);
        } catch (SVNException e) {
            props.delete();
        }
    }
    
    private KeyManager[] getKeyManagers() {
        if (myIsKeyManagerCreated) {
            return myKeyManagers;
        }
        myIsKeyManagerCreated = true;
        if (myClientCertFile == null) {
            return null;
        }
        char[] passphrase = null;
        if (myClientCertPassword != null) {
            passphrase = myClientCertPassword.toCharArray();
        }
        KeyStore keyStore = null;            
        InputStream is;
        try {
            is = SVNFileUtil.openFileForReading(myClientCertFile);
        } catch (SVNException e1) {
            return null;
        }
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            if (keyStore != null) {
                keyStore.load(is, passphrase);                    
            }
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        KeyManagerFactory kmf = null;
        if (keyStore != null) {
            try {
                kmf = KeyManagerFactory.getInstance("SunX509");
                if (kmf != null) {
                    kmf.init(keyStore, passphrase);
                    myKeyManagers = kmf.getKeyManagers();
                }
            } catch (Throwable e) {
                SVNDebugLog.getDefaultLog().info(e);
            } 
        }
        return myKeyManagers;
    }

    private static X509Certificate loadCertificate(File pemFile) {
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(pemFile);
        } catch (SVNException e) {
            return null;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X509");
            return (X509Certificate) factory.generateCertificate(is);
        } catch (CertificateException e) {
            return null;
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }

    public boolean isClientCertPromptRequired() {
        return myIsPromptForClientCert;
    }

    public void setClientAuthentication(SVNSSLAuthentication sslAuthentication) {
        if (sslAuthentication != null) {
            myClientCertFile = sslAuthentication.getCertificateFile();
            myClientCertPassword = sslAuthentication.getPassword();
        } else {
            myClientCertFile = null;
            myClientCertPassword = null;
        }
        myClientAuthentication = sslAuthentication;
    } 
    
    public SVNSSLAuthentication getClientAuthentication() {
        return myClientAuthentication;
    }
}
