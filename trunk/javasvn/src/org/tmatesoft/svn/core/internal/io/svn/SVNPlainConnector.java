package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.SocketFactory;

/**
 * @author Marc Strapetz
 */
public class SVNPlainConnector implements ISVNConnector {

    private Socket mySocket;
    private OutputStream myOutputStream;
    private InputStream myInputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        if (mySocket != null) {
            return;
        }
        try {
            SVNRepositoryLocation location = repository.getLocation();
            mySocket = SocketFactory.createPlainSocket(location.getHost(), location.getPort());
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }

    public void close() throws SVNException {
        if (mySocket != null) {
            try {
                mySocket.close();
            } catch (IOException ex) {
                throw new SVNException(ex);
            } finally {
                mySocket = null;
            }
        }
    }

    public InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
            myInputStream = mySocket.getInputStream();
        }

        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
            myOutputStream = mySocket.getOutputStream();
        }

        return myOutputStream;
    }
}