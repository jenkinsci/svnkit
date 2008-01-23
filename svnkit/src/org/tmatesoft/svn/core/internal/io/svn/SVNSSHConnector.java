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

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHSession.SSHConnectionInfo;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNSSHConnector implements ISVNConnector {

    private static final String SVNSERVE_COMMAND = "svnserve -t";
    private static final String SVNSERVE_COMMAND_WITH_USER_NAME = "svnserve -t --tunnel-user ";

    private Session mySession;
    private InputStream myInputStream;
    private OutputStream myOutputStream;
    private SSHConnectionInfo myConnection;

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
                        connection = SVNSSHSession.getConnection(repository.getLocation(), authentication);
                        if (connection == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}''", repository.getLocation().setPath("", false));
                            SVNErrorManager.error(err);
                        }
                        authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.SSH, realm, null, authentication);
                        break;
                    } catch (SVNAuthenticationException e) {
                        SVNDebugLog.getDefaultLog().info(e);
                        authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.SSH, realm, e.getErrorMessage(), authentication);
                        authentication = (SVNSSHAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
                        connection = null;
                    }
                }
                if (authentication == null) {
                    SVNErrorManager.cancel("authentication cancelled");
                } else if (connection == null) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Can not establish connection to ''{0}''", realm));
                }
                try {
                    mySession = connection.openSession();
                    SVNAuthentication author = authManager.getFirstAuthentication(ISVNAuthenticationManager.USERNAME, realm, repository.getLocation());
                    if (author == null) {
                        SVNErrorManager.cancel("authentication cancelled");
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
                    author = new SVNUserNameAuthentication(userName, author.isStorageAllowed());
                    authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.USERNAME, realm, null, author);
    
                    if ("".equals(repository.getExternalUserName())) {
                        mySession.execCommand(SVNSERVE_COMMAND);
                    } else {
                        mySession.execCommand(SVNSERVE_COMMAND_WITH_USER_NAME + "\"" + repository.getExternalUserName() + "\"");
                    }
        
                    myOutputStream = mySession.getStdin();
                    myOutputStream = new BufferedOutputStream(myOutputStream, 16*1024);
                    myInputStream = mySession.getStdout();
                    new StreamGobbler(mySession.getStderr());
                    myConnection = connection;
                    return;
                } catch (IOException e) {
                    reconnect--;
                    if (reconnect >= 0) {
                        // try again, but close session first.
                        connection.closeSession(mySession);
                        continue;
                    }
                    repository.getDebugLog().info(e);
                    close(repository);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}'': {1}", new Object[] {repository.getLocation().setPath("", false), e.getMessage()});
                    SVNErrorManager.error(err, e);
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
            SVNSSHSession.lock(Thread.currentThread());
            SVNDebugLog.getDefaultLog().info(Thread.currentThread() + ": ABOUT TO CLOSE SESSION IN : " + myConnection);
            try {
                if (myConnection.closeSession(mySession)) {
                    // no sessions left in connection, close it.
                    // SVNSSHSession will make sure that connection is disposed if necessary.
                    SVNDebugLog.getDefaultLog().info(Thread.currentThread() + ": ABOUT TO CLOSE CONNECTION: " + myConnection);
                    SVNSSHSession.closeConnection(myConnection);
                    myConnection = null;
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
        try {
            mySession.ping();
        } catch (IOException e) {
            // any failure here means that channel is stale.
            // session will be closed then.
            SVNDebugLog.getDefaultLog().info(Thread.currentThread() + ": DETECTED STALE SESSION : " + myConnection);
            return true;
        }
        return false;
    }
}