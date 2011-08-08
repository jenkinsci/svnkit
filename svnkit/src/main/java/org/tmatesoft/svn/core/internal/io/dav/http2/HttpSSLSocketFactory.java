package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSchemeSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;

public class HttpSSLSocketFactory implements LayeredSchemeSocketFactory {
    
    private SSLContext mySSLContext;

    public HttpSSLSocketFactory(KeyManager[] keyManagers, TrustManager trustManager) throws IOException {
        this(SVNSocketFactory.createSSLContext(keyManagers, trustManager));
    }

    public HttpSSLSocketFactory(SSLContext context) {
        mySSLContext = context;
    }

    public Socket createSocket(HttpParams params) throws IOException {
        return SVNSocketFactory.configureSSLSocket(mySSLContext.getSocketFactory().createSocket());
    }

    public Socket connectSocket(Socket socket, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpParams params)
            throws IOException, UnknownHostException, ConnectTimeoutException {
        Socket sock = socket != null ? socket : new Socket();
        if (localAddress != null) {
            sock.setReuseAddress(HttpConnectionParams.getSoReuseaddr(params));
            sock.bind(localAddress);
        }

        int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
        int soTimeout = HttpConnectionParams.getSoTimeout(params);

        try {
            sock.setSoTimeout(soTimeout);
            sock.connect(remoteAddress, connTimeout);
        } catch (SocketTimeoutException ex) {
            throw new ConnectTimeoutException("Connect to " + remoteAddress.getHostName() + "/"
                    + remoteAddress.getAddress() + " timed out");
        }
        SSLSocket sslsock;
        if (sock instanceof SSLSocket) {
            sslsock = (SSLSocket) sock;
        } else {
            sslsock = (SSLSocket) mySSLContext.getSocketFactory().createSocket(sock, remoteAddress.getHostName(), remoteAddress.getPort(), true);
            sslsock = (SSLSocket) SVNSocketFactory.configureSSLSocket(sslsock);
        }
        return sslsock;
    }

    public boolean isSecure(Socket sock) throws IllegalArgumentException {
        return true;
    }

    public Socket createLayeredSocket(Socket socket, String target, int port, boolean autoClose) throws IOException, UnknownHostException {
        return SVNSocketFactory.configureSSLSocket(mySSLContext.getSocketFactory().createSocket(socket, target, port, autoClose));
    }
}
