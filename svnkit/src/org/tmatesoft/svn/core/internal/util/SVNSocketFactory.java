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

package org.tmatesoft.svn.core.internal.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.StringTokenizer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;

/**
 * <code>SVNSocketFactory</code> is a utility class that represents a custom
 * socket factory which provides creating either a plain socket or a secure one
 * to encrypt data transmitted over network.
 * 
 * <p>
 * The created socket then used by the inner engine of <b><i>SVNKit</i></b>
 * library to communicate with a Subversion repository.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNSocketFactory {
    
    public static Socket createPlainSocket(String host, int port) throws IOException {
        InetAddress address = createAddres(host);
        Socket socket = new Socket(address, port);
        socket.setReuseAddress(true);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        return socket;
    }

    public static Socket createSSLSocket(ISVNSSLManager manager, String host, int port) throws IOException {
        manager = manager == null ? DEFAULT_SSL_MANAGER : manager;
        Socket sslSocket = manager.getSSLContext().getSocketFactory().createSocket(createAddres(host), port);
        sslSocket.setReuseAddress(true);
        sslSocket.setTcpNoDelay(true);
        sslSocket.setKeepAlive(true);
        return sslSocket;
    }

    public static Socket createSSLSocket(ISVNSSLManager manager, String host, int port, Socket socket) throws IOException {
        manager = manager == null ? DEFAULT_SSL_MANAGER : manager;
        Socket sslSocket = manager.getSSLContext().getSocketFactory().createSocket(socket, host, port, true);
        sslSocket.setReuseAddress(true);
        sslSocket.setTcpNoDelay(true);
        sslSocket.setKeepAlive(true);
        return sslSocket;
    }

    private static InetAddress createAddres(String hostName) throws UnknownHostException {
        byte[] bytes = new byte[4];
        int index = 0;
        for (StringTokenizer tokens = new StringTokenizer(hostName, "."); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            try {
                byte b = (byte) Integer.parseInt(token);
                if (index < bytes.length) {
                    bytes[index] = b;
                    index++;
                } else {
                    bytes = null;
                    break;
                }
            } catch (NumberFormatException e) {
                bytes = null;
                break;
            }
        }
        if (bytes != null && index == 4) {
            return InetAddress.getByAddress(hostName, bytes);
        }
        return InetAddress.getByName(hostName);
    }
    
    private static final ISVNSSLManager DEFAULT_SSL_MANAGER = new ISVNSSLManager() {
        public SSLContext getSSLContext() throws IOException {
            SSLContext context = null;
            try {
                context = SSLContext.getInstance("SSL");
                context.init(new KeyManager[] {}, new TrustManager[] {new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }
                    
                }}, null);
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e.getMessage());                
            } catch (KeyManagementException e) {
                throw new IOException(e.getMessage());                
            }
            return context;
        }
        public void acknowledgeSSLContext(boolean accepted, SVNErrorMessage errorMessage) {
        }
        public boolean isClientCertPromptRequired() {
            return false;
        }
        public void setClientAuthentication(SVNSSLAuthentication sslAuthentication) {
        }
        public SVNSSLAuthentication getClientAuthentication() {
            return null;
        }
    };

    public static boolean isSocketStale(Socket socket) throws IOException {
        boolean isStale = true;
        if (socket != null) {
            isStale = false;
            try {
                if (socket.getInputStream().available() == 0) {
                    int timeout = socket.getSoTimeout();
                    try {
                        socket.setSoTimeout(1);
                        socket.getInputStream().mark(1);
                        int byteRead = socket.getInputStream().read();
                        if (byteRead == -1) {
                            isStale = true;
                        } else {
                            socket.getInputStream().reset();
                        }
                    } finally {
                        socket.setSoTimeout(timeout);
                    }
                }
            } catch (InterruptedIOException e) {
                if (!SocketTimeoutException.class.isInstance(e)) {
                    throw e;
                }
            } catch (IOException e) {
                isStale = true;
            }
        }
        return isStale;
    }
}
