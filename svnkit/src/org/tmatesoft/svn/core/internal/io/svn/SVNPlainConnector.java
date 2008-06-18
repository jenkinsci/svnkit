/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNPlainConnector implements ISVNConnector {

    private static final int DEFAULT_SVN_TIMEOUT = 0;

    private Socket mySocket;
    private OutputStream myOutputStream;
    private InputStream myInputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        if (mySocket != null) {
            return;
        }
        SVNURL location = repository.getLocation();
        try {
            int connectTimeout = repository.getAuthenticationManager() != null ? repository.getAuthenticationManager().getConnectTimeout(repository) : DEFAULT_SVN_TIMEOUT;
            int readTimeout = repository.getAuthenticationManager() != null ? repository.getAuthenticationManager().getReadTimeout(repository) : DEFAULT_SVN_TIMEOUT;
            mySocket = SVNSocketFactory.createPlainSocket(location.getHost(), location.getPort(), connectTimeout, readTimeout);
        } catch (SocketTimeoutException e) {
	        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "timed out waiting for server", null, SVNErrorMessage.TYPE_ERROR, e);
            SVNErrorManager.error(err, e);
        } catch (UnknownHostException e) {
	        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "Unknown host " + e.getMessage(), null, SVNErrorMessage.TYPE_ERROR, e);
            SVNErrorManager.error(err, e);
        } catch (ConnectException e) {
	        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "connection refused by the server", null, SVNErrorMessage.TYPE_ERROR, e);
            SVNErrorManager.error(err, e);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
    }

    public void close(SVNRepositoryImpl repository) throws SVNException {
        if (mySocket != null) {
            try {
                mySocket.close();
            } catch (IOException ex) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, ex.getMessage(), ex));
            } finally {
                mySocket = null;
                myInputStream = null;
                myOutputStream = null;
            }
        }
    }
    
    public boolean isStale() {
        try {
            SVNDebugLog.getLog(SVNLogType.NETWORK).logInfo("checking whether connection is stale.");
            boolean result = mySocket != null && SVNSocketFactory.isSocketStale(mySocket);
            SVNDebugLog.getLog(SVNLogType.NETWORK).logInfo("connection is stale: " + result);
            return result;
        } catch (IOException e) {
            SVNDebugLog.getLog(SVNLogType.NETWORK).logInfo("failure during stale check");
            SVNDebugLog.getLog(SVNLogType.NETWORK).logInfo(e);
            return true;
        }
    }
    
    public boolean isConnected(SVNRepositoryImpl repos) throws SVNException {
        return mySocket != null && mySocket.isConnected();
    }

    public InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
            myInputStream = mySocket.getInputStream();
        }
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
            myOutputStream = new BufferedOutputStream(mySocket.getOutputStream());
        }
        return myOutputStream;
    }

    public void free() {
    }

    public boolean occupy() {
        return true;
    }
}