package org.tmatesoft.svn.core.internal.io.svn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
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
 * @author Marc Strapetz
 */
public class SVNJSchConnector implements ISVNConnector {

    private Session mySession;
    private ChannelExec myChannel;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNCredentialsProvider provider = repository.getCredentialsProvider();
        if (provider == null) {
            throw new SVNException("Credentials provider is required for SSH connection");
        }
        provider.reset();

        final String host = repository.getLocation().getHost();
        final int port = repository.getLocation().getPort();

        ISVNCredentials credentials = provider.nextCredentials(null);
        SVNAuthenticationException lastException = null;
        while (credentials != null) {
            try {
                connect(host, port, credentials);
                provider.accepted(credentials);
                break;
            } catch (SVNAuthenticationException e) {
                lastException = e;
                credentials = provider.nextCredentials(e.getMessage());
            }
        }

        if (credentials == null) {
            if (lastException != null) {
                throw lastException;
            }
            throw new SVNAuthenticationException("Can't establish SSH connection without credentials");
        }

        long start;
        try {
            myChannel = (ChannelExec) mySession.openChannel("exec");
            myInputStream = myChannel.getInputStream();
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
    }

    private void connect(String host, int port, ISVNCredentials credentials) throws SVNException {
        JSch jsch = createJSch();
        String userName = credentials.getName();
        String password = credentials.getPassword();
        String privateKey = null;
        String passphrase = null;
        if (credentials instanceof ISVNSSHCredentials) {
            privateKey = ((ISVNSSHCredentials) credentials).getPrivateKeyID();
            passphrase = ((ISVNSSHCredentials) credentials).getPassphrase();
            
        }
        try {
            if (privateKey != null) {
                File keyFile = new File(privateKey);
                if (keyFile.exists() && keyFile.isFile()) {
                    if (passphrase != null) {
                        jsch.addIdentity(privateKey, passphrase);
                    } else {
                        jsch.addIdentity(privateKey);
                    }
                }
            }
            mySession = jsch.getSession(userName, host, port);
        } catch (JSchException e) {
            e.printStackTrace();
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
        mySession.setUserInfo(new EmptyUserInfo(password, passphrase));
        long start = System.currentTimeMillis();
        try {
            mySession.connect();
        } catch (JSchException e) {
            mySession.disconnect();
            throw new SVNAuthenticationException(e);
        }
        DebugLog.log("SSH2 jsch.connect(): " + (System.currentTimeMillis() - start));
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
        private String myPassphrase;

        public EmptyUserInfo(String password, String passphrase) {
            myPassword = password;
            myPassphrase = passphrase;
        }

        public String getPassphrase() {
            return myPassphrase;
        }

        public String getPassword() {
            return myPassword;
        }

        public boolean promptPassword(String arg0) {
            return myPassword != null;
        }

        public boolean promptPassphrase(String arg0) {
            return myPassphrase != null;
        }

        public boolean promptYesNo(String arg0) {
            return true;
        }

        public void showMessage(String arg0) {
        }
    }
}