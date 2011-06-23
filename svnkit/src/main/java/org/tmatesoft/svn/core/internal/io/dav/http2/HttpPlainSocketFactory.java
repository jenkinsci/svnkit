package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.internal.util.ISVNTask;
import org.tmatesoft.svn.core.internal.util.SVNSocketConnection;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNClassLoader;

public class HttpPlainSocketFactory implements SchemeSocketFactory {
    
    public Socket createSocket(HttpParams params) throws IOException {
        return new Socket();
    }

    public Socket connectSocket(Socket socket, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        ISVNCanceller canceller = (ISVNCanceller) params.getParameter(HttpConnection.CANCELLER_PARAMETER);
        
        Socket sock = socket;
        if (sock == null) {
            sock = createSocket(params);
        }
        if (localAddress != null) {
            sock.setReuseAddress(HttpConnectionParams.getSoReuseaddr(params));
            sock.bind(localAddress);
        }
        int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
        int soTimeout = HttpConnectionParams.getSoTimeout(params);
        sock.setKeepAlive(true);
        sock.setReuseAddress(true);
        sock.setSoLinger(true, 0);
        sock.setTcpNoDelay(true);
        sock.setSoTimeout(soTimeout);

        try {
            if (canceller == null || canceller == ISVNCanceller.NULL) {
                sock.connect(remoteAddress, connTimeout);
            } else {
                SVNSocketConnection socketConnection = new SVNSocketConnection(socket, remoteAddress, connTimeout);
                ISVNTask task = SVNClassLoader.getThreadPool().run(socketConnection, true);

                while (!socketConnection.isSocketConnected()) {
                    try {
                        canceller.checkCancelled();
                    } catch (SVNCancelException e) {
                        task.cancel(true);
                        throw new SVNCancellableOutputStream.IOCancelException(e.getMessage());
                    }
                }
                
                if (socketConnection.getError() != null) {
                    throw socketConnection.getError();           
                }
            }
        } catch (SocketTimeoutException ex) {
            throw new ConnectTimeoutException("Connect to " + remoteAddress.getHostName() + "/"
                    + remoteAddress.getAddress() + " timed out");
        }
        return sock;
    }

    public boolean isSecure(Socket sock) throws IllegalArgumentException {
        return false;
    }

}
