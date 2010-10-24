/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNSSHHostVerifier;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHSession.SSHConnectionInfo;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSSHConnector implements ISVNConnector {

    private static final String SVNSERVE_COMMAND = "svnserve -t";
    private static final String SVNSERVE_COMMAND_WITH_USER_NAME = "svnserve -t --tunnel-user ";
    
    private static final boolean ourIsUseSessionPing = Boolean.getBoolean("svnkit.ssh2.ping");
    
    private Session mySession;
    private InputStream myInputStream;
    private OutputStream myOutputStream;
    private SSHConnectionInfo myConnection;
    private boolean myIsUseSessionPing;
    private boolean myIsUseConnectionPing;
    
    public SVNSSHConnector() {
        this(true, true);
    }
    
    public SVNSSHConnector(boolean useConnectionPing, boolean useSessionPing) {
        myIsUseConnectionPing = useConnectionPing;
        myIsUseSessionPing = useSessionPing;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNAuthenticationManager authManager = repository.getAuthenticationManager();
        if (authManager == null) {
            SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", repository.getLocation());
            return;
        }

        String realm = repository.getLocation().getProtocol() + "://" + repository.getLocation().getHost();
        if (repository.getLocation().hasPort()) {
            realm += ":" + repository.getLocation().getPort();
        }
        if (repository.getLocation().getUserInfo() != null && !"".equals(repository.getLocation().getUserInfo())) {
            realm = repository.getLocation().getUserInfo() + "@" + realm;
        }

        int reconnect = 1;
        while(true) {
            SVNSSHAuthentication authentication = (SVNSSHAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
            SSHConnectionInfo connection = null;
            
            // lock SVNSSHSession to make sure connection opening and session creation is atomic.
            SVNSSHSession.lock(Thread.currentThread());
            try {
                while (authentication != null) {
                    try {
                        ISVNSSHHostVerifier verifier = (ISVNSSHHostVerifier) (authManager instanceof ISVNSSHHostVerifier ? authManager : null);
                        connection = SVNSSHSession.getConnection(repository.getLocation(), verifier, authentication, authManager.getConnectTimeout(repository), myIsUseConnectionPing);
                        if (connection == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}''", repository.getLocation().setPath("", false));
                            SVNErrorManager.error(err, SVNLogType.NETWORK);
                        }
                        
                        authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.SSH, realm, null, authentication);
                        break;
                    } catch (SVNAuthenticationException e) {
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e);
                        authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.SSH, realm, e.getErrorMessage(), authentication);
                        authentication = (SVNSSHAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
                        connection = null;
                    }
                }
                if (authentication == null) {
                    SVNErrorManager.cancel("authentication cancelled", SVNLogType.NETWORK);
                } else if (connection == null || connection.isDisposed()) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Can not establish connection to ''{0}''", realm), SVNLogType.NETWORK);
                }
                try {
                    mySession = connection.openSession();
                    SVNAuthentication author = authManager.getFirstAuthentication(ISVNAuthenticationManager.USERNAME, realm, repository.getLocation());
                    if (author == null) {
                        SVNErrorManager.cancel("authentication cancelled", SVNLogType.NETWORK);
                    }
                    String userName = author.getUserName();
                    if (userName == null || "".equals(userName.trim())) {
                        userName = authentication.getUserName();
                    }
                    if (author.getUserName() == null || author.getUserName().equals(authentication.getUserName()) || 
                            "".equals(author.getUserName())) {
                        repository.setExternalUserName("");
                    } else {
                        repository.setExternalUserName(author.getUserName()); 
                    }
                    author = new SVNUserNameAuthentication(userName, author.isStorageAllowed(), repository.getLocation(), false);
                    authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.USERNAME, realm, null, author);
    
                    if ("".equals(repository.getExternalUserName())) {
                        mySession.execCommand(SVNSERVE_COMMAND);
                    } else {
                        mySession.execCommand(SVNSERVE_COMMAND_WITH_USER_NAME + "\"" + repository.getExternalUserName() + "\"");
                    }
                    myOutputStream = mySession.getStdin();
                    myOutputStream = new BufferedOutputStream(myOutputStream, 16*1024);
                    myInputStream = mySession.getStdout();
                    myInputStream = new BufferedInputStream(myInputStream, 16*1024);
                    new StreamGobbler(mySession.getStderr());
                    myConnection = connection;
                    return;
                } catch (SocketTimeoutException e) {
	                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "timed out waiting for server", null, SVNErrorMessage.TYPE_ERROR, e);
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                } catch (UnknownHostException e) {
	                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "Unknown host " + e.getMessage(), null, SVNErrorMessage.TYPE_ERROR, e);
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                } catch (ConnectException e) {
	                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, "connection refused by the server", null, SVNErrorMessage.TYPE_ERROR, e);
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                } catch (IOException e) {
                    reconnect--;
                    if (reconnect >= 0) {
                        // try again, but close session first.
                        connection.closeSession(mySession);
                        continue;
                    }
                    repository.getDebugLog().logFine(SVNLogType.NETWORK, e);
                    close(repository);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}'': {1}", new Object[] {repository.getLocation().setPath("", false), e.getMessage()});
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                }
            } finally {
                SVNSSHSession.unlock();
            }
        }
    }

    public void close(SVNRepositoryImpl repository) throws SVNException {
        SVNFileUtil.closeFile(myOutputStream);
        SVNFileUtil.closeFile(myInputStream);
        if (mySession != null) {
            // close session and close owning connection if necessary.
            // close session and connection in atomic way.
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                    Thread.currentThread() + ": ABOUT TO CLOSE SESSION IN : " + myConnection);
            SVNSSHSession.lock(Thread.currentThread());
            try {
                if (myConnection != null) {
                    if (myConnection.closeSession(mySession)) {
                        // no sessions left in connection, close it.
                        //  SVNSSHSession will make sure that connection is disposed if necessary.
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                                Thread.currentThread() + ": ABOUT TO CLOSE CONNECTION: " + myConnection);
                        SVNSSHSession.closeConnection(myConnection);
                        myConnection = null;
                    }
                }
            } finally {
                SVNSSHSession.unlock();
            }
            
        }
        mySession = null;
        myOutputStream = null;
        myInputStream = null;
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }

    public boolean isConnected(SVNRepositoryImpl repos) throws SVNException {
        return mySession != null && !isStale();
    }
    
    public boolean isStale() {
        if (mySession == null) {
            return true;
        }
        if (myConnection == null || myConnection.isDisposed()) {
            return true;
        }
        if (!ourIsUseSessionPing) {
            return false;
        }
        if (!myIsUseSessionPing) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, "SKIPPING CHANNEL PING, IT HAS BEEN DISABLED");
            return false;
        }
        if (!myConnection.isSessionPingSupported()) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, "SKIPPING CHANNEL PING, IT IS NOT SUPPORTED");
            return false;
        }
        try {
            mySession.ping();
        } catch (IOException e) {
            // any failure here means that channel is stale.
            // session will be closed then.
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e); 
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                    Thread.currentThread() + ": DETECTED STALE SESSION : " + myConnection);
            return true;
        }
        return false;
    }
}