/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNClassLoader;

/**
 * <code>SVNSocketFactory</code> is a utility class that represents a custom
 * socket factory which provides creating either a plain socket or a secure one
 * to encrypt data transmitted over network.
 *
 * <p>
 * The created socket then used by the inner engine of <b><i>SVNKit</i></b>
 * library to communicate with a Subversion repository.
 *
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSocketFactory {

    private static boolean ourIsSocketStaleCheck = false;
    private static int ourSocketReceiveBufferSize = 0; // default
    private static ISVNThreadPool ourThreadPool = SVNClassLoader.getThreadPool(); 
    private static String ourSSLProtocols = System.getProperty("svnkit.http.sslProtocols");
    
    public static Socket createPlainSocket(String host, int port, int connectTimeout, int readTimeout, ISVNCanceller cancel) throws IOException, SVNException {
        InetAddress address = createAddres(host);
        Socket socket = new Socket();
        int bufferSize = getSocketReceiveBufferSize();
        if (bufferSize > 0) {
            socket.setReceiveBufferSize(bufferSize);
        }
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        connect(socket, socketAddress, connectTimeout, cancel);
        socket.setReuseAddress(true);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoLinger(true, 0);
        socket.setSoTimeout(readTimeout);
        return socket;
    }

    public static Socket createSSLSocket(KeyManager[] keyManagers, TrustManager trustManager, String host, int port, int connectTimeout, int readTimeout, ISVNCanceller cancel) throws IOException, SVNException {
        InetAddress address = createAddres(host);
        Socket sslSocket = createSSLContext(keyManagers, trustManager).getSocketFactory().createSocket();
        int bufferSize = getSocketReceiveBufferSize();
        if (bufferSize > 0) {
            sslSocket.setReceiveBufferSize(bufferSize);
        }
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        connect(sslSocket, socketAddress, connectTimeout, cancel);
        sslSocket.setReuseAddress(true);
        sslSocket.setTcpNoDelay(true);
        sslSocket.setKeepAlive(true);
        sslSocket.setSoLinger(true, 0);
        sslSocket.setSoTimeout(readTimeout);
        sslSocket = configureSSLSocket(sslSocket);
        return sslSocket;
    }

    public static Socket createSSLSocket(KeyManager[] keyManagers, TrustManager trustManager, String host, int port, Socket socket, int readTimeout) throws IOException {
        Socket sslSocket = createSSLContext(keyManagers, trustManager).getSocketFactory().createSocket(socket, host, port, true);
        sslSocket.setReuseAddress(true);
        sslSocket.setTcpNoDelay(true);
        sslSocket.setKeepAlive(true);
        sslSocket.setSoLinger(true, 0);
        sslSocket.setSoTimeout(readTimeout);
        sslSocket = configureSSLSocket(sslSocket);
        return sslSocket;
    }

    public static ISVNThreadPool getThreadPool() {
        return ourThreadPool;
    }
    
    public static void connect(Socket socket, InetSocketAddress address, int timeout, ISVNCanceller cancel) throws IOException, SVNException {
        if (cancel == null || cancel == ISVNCanceller.NULL) {
            socket.connect(address, timeout);
            return;
        }

        SVNSocketConnection socketConnection = new SVNSocketConnection(socket, address, timeout);
        ISVNTask task = ourThreadPool.run(socketConnection, true);

        while (!socketConnection.isSocketConnected()) {
            try {
                cancel.checkCancelled();
            } catch (SVNCancelException e) {
                task.cancel(true);
                throw e;
            }
        }
        
        if (socketConnection.getError() != null) {
            throw socketConnection.getError();           
        }
    }

    private static InetAddress createAddres(String hostName) throws UnknownHostException {
        byte[] bytes = new byte[4];
        int index = 0;
        for (StringTokenizer tokens = new StringTokenizer(hostName, "."); tokens.hasMoreTokens();) {
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
    
    public static synchronized void setSocketReceiveBufferSize(int size) {
        ourSocketReceiveBufferSize = size;
    }

    public static synchronized int getSocketReceiveBufferSize() {
        return ourSocketReceiveBufferSize;
    }
    
    public static void setSocketStaleCheckEnabled(boolean enabled) {
        ourIsSocketStaleCheck = enabled;
    }

    public static boolean isSocketStaleCheckEnabled() {
        return ourIsSocketStaleCheck;
    }

    public static boolean isSocketStale(Socket socket) throws IOException {
        if (!isSocketStaleCheckEnabled()) {
            return socket == null || socket.isClosed() || !socket.isConnected();
        }
        
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

	public static SSLContext createSSLContext(KeyManager[] keyManagers, TrustManager trustManager) throws IOException {
		if (trustManager == null) {
			trustManager = new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}
			};
		}

		if (keyManagers == null) {
			keyManagers = new KeyManager[0];
		}

		final TrustManager[] trustManagers = new TrustManager[] {trustManager};
		try {
			final SSLContext context = SSLContext.getInstance("SSLv3");
			context.init(keyManagers, trustManagers, null);
			return context;
		}
		catch (NoSuchAlgorithmException e) {
			throw new IOException(e.getMessage());
		}
		catch (KeyManagementException e) {
			throw new IOException(e.getMessage());
		}
	}

    public static Socket configureSSLSocket(Socket socket) {
        if (socket == null || !(socket instanceof SSLSocket)) {
            return null;
        }
        SSLSocket sslSocket = (SSLSocket) socket;
        if (ourSSLProtocols != null && "SSLv3".equals(ourSSLProtocols.trim())) {
            sslSocket.setEnabledProtocols(new String[] {"SSLv3"});
            return sslSocket;
        }
        String[] protocols = null;
        
        if (ourSSLProtocols != null) {
            Collection userProtocols = new ArrayList();
            for(StringTokenizer tokens = new StringTokenizer(ourSSLProtocols, ","); tokens.hasMoreTokens();) {
                String userProtocol = tokens.nextToken().trim();
                if (!"".equals(userProtocol)) {
                    userProtocols.add(userProtocol);
                }
            }
            protocols = (String[]) userProtocols.toArray(new String[userProtocols.size()]);
        } else {
            protocols = sslSocket.getSupportedProtocols();
        }
        String[] suites = sslSocket.getSupportedCipherSuites();
        if (protocols != null && protocols.length > 0) {
            sslSocket.setEnabledProtocols(protocols);
        }
        if (suites != null && suites.length > 0) {
            sslSocket.setEnabledCipherSuites(suites);
        }
        return sslSocket;
    }
}
