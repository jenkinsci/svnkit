/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNSSHCredentials;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;

/**
 * @author TMate Software Ltd.
 */
public class SVNSSHConnection extends SVNConnection {
    
    private Session mySession;
    private ChannelExec myChannel;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        String host = repository.getLocation().getHost();
        int port = repository.getLocation().getPort();
        
        ISVNCredentials credentials = repository.getCredentials();
        if (credentials == null) {
            throw new SVNAuthenticationException("credentials are required to establish ssh connection");
        }
        JSch jsch = null;
        jsch = createJSch();
        credentials = repository.getCredentialsProvider().nextCredentials("ssh");
        if (credentials == null) {
            throw new SVNAuthenticationException("authentication failed");
        }
        String identity = null;
        if (credentials instanceof ISVNSSHCredentials) {
            identity = ((ISVNSSHCredentials) credentials).getPrivateKeyID();
        }
        try {
            if (identity != null) {
                jsch.addIdentity(identity);
            }
            mySession = jsch.getSession(credentials.getName(), host, port);
        } catch (JSchException e) {
            throw new SVNException(e);
        }
        mySession.setSocketFactory(new SocketFactory() {
            public Socket createSocket(String h, int p) throws IOException, UnknownHostException {
                return org.tmatesoft.svn.util.SocketFactory.createPlainSocket(h, p);
            }
            public InputStream getInputStream(Socket socket) throws IOException {
                return socket.getInputStream();
            }
            public OutputStream getOutputStream(Socket socket) throws IOException {
                return socket.getOutputStream();
            }
        });
        mySession.setUserInfo(new EmptyUserInfo(credentials.getPassword()));
        long start = System.currentTimeMillis();
        try {
            mySession.connect();
        } catch (JSchException e) {
            mySession.disconnect();
            throw new SVNAuthenticationException(e);
        } 
        repository.getCredentialsProvider().accepted(credentials);
        DebugLog.log("SSH2 jsch.connect(): " + (System.currentTimeMillis() - start));
        try {
            myChannel = (ChannelExec) mySession.openChannel("exec");
            myInputStream = new RollbackInputStream(new BufferedInputStream(myChannel.getInputStream()));
            myOutputStream = myChannel.getOutputStream();
            
            myChannel.setCommand("svnserve -t");
            myChannel.setErrStream(System.err);
            
            start = System.currentTimeMillis();
            myChannel.connect();
            DebugLog.log("SSH2 channel.connect(): " + (System.currentTimeMillis() - start));
        } catch (JSchException e) {
            throw new SVNException(e);
        } catch (IOException e) {
            throw new SVNException(e);
        }
        handshake(repository);
        authenticate(repository, null);
    }

    public void close() throws SVNException {
        if (myChannel != null) {
            myChannel.disconnect();
        }
        if (mySession != null) {
            mySession.disconnect();
        }
        myChannel = null;
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
    
    private JSch createJSch() {
        return new JSch();
    }
    
    private static class EmptyUserInfo implements UserInfo {
        
        private String myPassword;
        
        public EmptyUserInfo(String password) {
            myPassword = password;
        }
        public String getPassphrase() {
            return null;
        }
        public String getPassword() {
            return myPassword;
        }
        public boolean promptPassword(String arg0) {
            return true;
        }
        public boolean promptPassphrase(String arg0) {
            return true;
        }
        public boolean promptYesNo(String arg0) {
            return true;
        }
        public void showMessage(String arg0) {
        }
    }
}
