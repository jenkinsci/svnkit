/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.auth;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class BasicSSLManager implements ISVNSSLManager {

    private SVNSSLAuthentication mySSLAuthentication;
    private Throwable myLoadError;
    
    public BasicSSLManager(SVNSSLAuthentication sslAuthentication) {
        mySSLAuthentication = sslAuthentication;
    }

    public SSLContext getSSLContext() throws IOException, SVNException {
        try {
            SSLContext context = SSLContext.getInstance("SSLv3");
            context.init(getKeyManagers(), null, null);
            return context;
        } catch (NoSuchAlgorithmException nsae) {
            throw (IOException) new IOException(nsae.getMessage()).initCause(nsae);
        } catch (KeyManagementException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    public boolean isClientCertPromptRequired() {
        return false;
    }

    public SVNSSLAuthentication getClientAuthentication() {
        return mySSLAuthentication;
    }

    public void setClientAuthentication(SVNSSLAuthentication sslAuthentication) {
        mySSLAuthentication = sslAuthentication;
    }

    public Throwable getClientCertLoadingError() {
        return myLoadError;
    }

    public void acknowledgeSSLContext(boolean accepted, SVNErrorMessage errorMessage) {
    }

    private KeyManager[] getKeyManagers() {
        char[] passphrase = null;
        String password = mySSLAuthentication.getPassword();
        File certFile = mySSLAuthentication.getCertificateFile();
        
        if (password != null) {
            passphrase = password.toCharArray();
        }
        KeyStore keyStore = null;
        InputStream is;
        try {
            is = SVNFileUtil.openFileForReading(certFile);
        } catch (SVNException e1) {
            myLoadError = e1;
            return null;
        }
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            if (keyStore != null) {
                keyStore.load(is, passphrase);
            }
        } catch (Throwable th) {
            myLoadError = th;
            return null;
        } finally {
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
            } catch (Throwable e) {
            }
        }
        return result;
    } }
