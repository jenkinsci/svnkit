/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * @author TMate Software Ltd.
 */
public interface IDAVSSLManager {
    
    public static final IDAVSSLManager DEFAULT = new IDAVSSLManager() {
        public SSLContext getSSLContext(String host, int port) throws IOException {
            try {
                SSLContext context = SSLContext.getInstance("SSL");
                context.init(null, new TrustManager[] {new X509TrustManager() { 
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }                    
                }}, null);
                return context;
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e.getMessage());
            } catch (KeyManagementException e) {
                throw new IOException(e.getMessage());
            }            
        }
    };
    
    public SSLContext getSSLContext(String host, int port) throws IOException;
    
}
