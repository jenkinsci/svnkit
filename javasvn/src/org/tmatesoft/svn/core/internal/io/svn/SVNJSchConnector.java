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

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNAuthentication;
import org.tmatesoft.svn.util.DebugLog;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNJSchConnector implements ISVNConnector {

    private static final String CHANNEL_TYPE = "exec";

    private static final String SVNSERVE_COMMAND = "svnserve --tunnel";

    private ChannelExec myChannel;

    private InputStream myInputStream;

    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNAuthenticationManager authManager = repository
                .getAuthenticationManager();

        String realm = repository.getLocation().getProtocol() + "://"
                + repository.getLocation().getHost() + ":"
                + repository.getLocation().getPort();
        SVNAuthentication authentication = authManager.getFirstAuthentication(
                ISVNAuthenticationManager.SSH, realm);
        if (authentication == null) {
            throw new SVNCancelException();
        }
        SVNAuthenticationException lastException = null;
        Session session = null;

        while (authentication != null) {
            try {
                session = SVNJSchSession.getSession(repository.getLocation(), authentication);
                if (session != null && !session.isConnected()) {
                    session = null;
                    continue;
                }
                lastException = null;
                authManager.addAuthentication(realm, authentication, authManager.isAuthStorageEnabled());
                repository.setExternalUserName(authentication.getUserName());
                break;
            } catch (SVNAuthenticationException e) {
                if (session != null && session.isConnected()) {
                    DebugLog.log("DISCONNECTING: " + session);
                    session.disconnect();
                    session = null;
                }
                lastException = e;
                if (e.getMessage() != null
                        && e.getMessage().toLowerCase().indexOf("Auth") >= 0) {
                    authentication = authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm);
                    if (authentication == null) {
                        throw new SVNCancelException();
                    }
                } else {
                    throw e;
                }
            }
        }
        if (lastException != null || session == null) {
            if (lastException != null) {
                throw lastException;
            }
            throw new SVNAuthenticationException(
                    "Can't establish SSH connection without credentials");
        }
        try {
            int retry = 1;
            while (true) {
                myChannel = (ChannelExec) session.openChannel(CHANNEL_TYPE);
                String command = SVNSERVE_COMMAND;
                myChannel.setCommand(command);

                myOutputStream = myChannel.getOutputStream();
                myInputStream = myChannel.getInputStream();

                DebugLog.log("JSCH command: " + command);
                try {
                    myChannel.connect();
                } catch (Throwable e) {
                    DebugLog.error(e);
                    retry--;
                    if (retry < 0) {
                        throw new SVNException(e);
                    }
                    if (session.isConnected()) {
                        session.disconnect();
                    }
                    continue;
                }
                break;
            }
        } catch (Throwable e) {
            DebugLog.error(e);
            close();
            if (session.isConnected()) {
                session.disconnect();
            }
            throw new SVNException("Failed to open SSH session: " + e.getMessage());
        }

        myInputStream = new FilterInputStream(myInputStream) {
            public void close() {
            }
        };
        myOutputStream = new FilterOutputStream(myOutputStream) {
            public void close() {
            }
        };
    }

    public void close() throws SVNException {
        if (myChannel != null) {
            myChannel.disconnect();
        }
        myChannel = null;
        myOutputStream = null;
        myInputStream = null;
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }
}