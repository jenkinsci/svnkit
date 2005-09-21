/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNGanymedConnector implements ISVNConnector {

    private static final String SVNSERVE_COMMAND = "svnserve --tunnel";

    private Session mySession;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNAuthenticationManager authManager = repository.getAuthenticationManager();

        String realm = repository.getLocation().getProtocol() + "://" + repository.getLocation().getHost();
        if (repository.getLocation().hasPort()) {
            realm += ":" + repository.getLocation().getPort();
        }        
        SVNSSHAuthentication authentication = (SVNSSHAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
        Connection connection = null;

        while (authentication != null) {
            try {
                connection = SVNGanymedSession.getConnection(repository.getLocation(), authentication);
                if (connection == null) {
                    SVNErrorManager.error("svn: Connection to '" + realm + "'failed");
                }
                authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.SSH, realm, null, authentication);
                repository.setExternalUserName(authentication.getUserName());
                break;
            } catch (SVNAuthenticationException e) {
                authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.SSH, realm, e.getMessage(), authentication);
                authentication = (SVNSSHAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
                connection = null;
            }
        }
        if (authentication == null) {
            throw new SVNAuthenticationException("svn: Authenticantion cancelled");
        } else if (connection == null) {
            SVNErrorManager.error("svn: Connection to '" + realm + "' failed");
        }
        try {
            mySession = connection.openSession();
            mySession.execCommand(SVNSERVE_COMMAND);

            myOutputStream = mySession.getStdin();
            myInputStream = new StreamGobbler(mySession.getStdout());
        } catch (IOException e) {
            SVNDebugLog.logInfo(e);
            close();
            SVNErrorManager.error("svn: Connection to '" + realm + "' failed\nsvn: Could not open SSH session: " + e.getMessage());
        }
    }

    public void close() throws SVNException {
        SVNFileUtil.closeFile(myOutputStream);
        SVNFileUtil.closeFile(myInputStream);
        if (mySession != null) {
            mySession.close();
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
        return true;
    }
}