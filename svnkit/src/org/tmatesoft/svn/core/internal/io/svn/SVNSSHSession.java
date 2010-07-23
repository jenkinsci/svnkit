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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNSSHHostVerifier;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.transport.ClientServerHello;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSSHSession {
    
    private static final int MAX_SESSIONS_PER_CONNECTION = 8;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 1000*10*60; // ten minutes

    private static Map ourConnectionsPool = new Hashtable();
    private static boolean ourIsUsePersistentConnection;
    private static Object ourRequestor;
    private static long ourTimeout;

    private static int ourLockLevel;
    
    static {
        String persistent = System.getProperty("svnkit.ssh2.persistent", System.getProperty("javasvn.ssh2.persistent", Boolean.TRUE.toString()));
        ourIsUsePersistentConnection = Boolean.TRUE.toString().equals(persistent);
        String timeout = System.getProperty("svnkit.ssh2.persistent.timeout", System.getProperty("javasvn.ssh2.persistent.timeout"));
        long value = DEFAULT_CONNECTION_TIMEOUT;
        if (timeout != null) {
            try {
                value = Long.parseLong(timeout);
                value = value*1000;
            } catch (NumberFormatException nfe) {
            }
        } 
        ourTimeout = value;
    }

    public static SSHConnectionInfo getConnection(SVNURL location, ISVNSSHHostVerifier verifier, SVNSSHAuthentication credentials, int connectTimeout, boolean useConnectionPing) throws SVNException {
        lock(Thread.currentThread());
        try {
            if ("".equals(credentials.getUserName()) || credentials.getUserName() == null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "User name is required to establish SSH connection");
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            }
            int port = location.hasPort() ? location.getPort() : credentials.getPortNumber();
            if (port < 0) {
                port = 22;
            }
            if (!isUsePersistentConnection()) {
                Connection connection = openConnection(location, verifier, credentials, port, connectTimeout);
                return new SSHConnectionInfo(null, "unpersistent", connection, false);
            }
            String key = credentials.getUserName() + ":" + location.getHost() + ":" + port;
            String id = credentials.getUserName() + ":" + location.getHost() + ":" + port;
            if (credentials.getPrivateKeyFile() != null) {
                key += ":" + credentials.getPrivateKeyFile().getAbsolutePath();
            }
            String debugKey = key;
            if (credentials.getPassphrase() != null) {
                key += ":" + credentials.getPassphrase();
                debugKey += ":passphrase";
            }
            if (credentials.getPassword() != null) {
                key += ":" + credentials.getPassword();
                debugKey += ":password";
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                    ourRequestor + ": FETCHING CONNECTION FOR KEY: " + debugKey);
            
            SSHConnectionInfo connectionInfo = null;
            LinkedList connectionsList = (LinkedList) ourConnectionsPool.get(key);
            if (connectionsList == null) {
                connectionsList = new LinkedList();
                ourConnectionsPool.put(key, connectionsList);
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                    ourRequestor + ": EXISTING CONNECTIONS COUNT: " + connectionsList.size());
            for (Iterator infos = connectionsList.iterator(); infos.hasNext();) {
                SSHConnectionInfo info = (SSHConnectionInfo) infos.next();
                // ping connection here. if it is stale - close connection and remove it from the pool.
                if (useConnectionPing) {
                    try {
                        info.myConnection.ping();
                    } catch (IOException e) {
                        // ping failed, remove _this_ info only and close its connection.
                        // the we may check next available connection.
                        
                        // all channels binded to the closed connection will be closed
                        // on the next attempt to access them.
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                                ourRequestor + ": ROTTEN CONNECTION DETECTED, WILL CLOSE IT: " + info);
                        infos.remove();
                        // to let it be closed even if it is the last one.
                        info.setPersistent(false);
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                                ourRequestor + ": ROTTEN CONNECTION MADE NOT PERSISTENT: " + info);
                        closeConnection(info);
                        continue;
                    }
                } else {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                            "SKIPPING CONNECTION PING, IT HAS BEEN DISABLED");
                }
                if (info.getSessionCount() < MAX_SESSIONS_PER_CONNECTION) {
                    info.resetTimeout();
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                            ourRequestor + ": REUSING ONE WITH " + info.getSessionCount() + " SESSIONS: " + info.myConnection);
                    return info;
                }
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": OPENING NEW CONNECTION");
            Connection connection = openConnection(location, verifier, credentials, port, connectTimeout);
            connectionInfo = new SSHConnectionInfo(key, id, connection, true);
            connectionsList.add(connectionInfo);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": NEW CONNECTION OPENED, " +
            		"TOTAL: " + connectionsList.size());
            return connectionInfo;
        } finally {
            unlock();
        }
    }

    static void closeConnection(SSHConnectionInfo connectionInfo) {
        lock(Thread.currentThread());
        try {
            if (!connectionInfo.isPersistent()) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": CLOSED, " +
                		"NOT PERSISTENT OR STALE: " + connectionInfo);
                connectionInfo.dispose();
                return;
            }
            // close whole connection only if there are others usable left.
            LinkedList connectionsList = (LinkedList) ourConnectionsPool.get(connectionInfo.getKey());
            if (connectionsList == null) {
                connectionInfo.dispose();
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": NOTHING TO CLOSE, " +
                        "NO CONNECTIONS FOUND: " + connectionInfo);
                return;
            }

            if (connectionsList.size() <= 1) {
                connectionInfo.startTimeout();
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": NOT CLOSED, " +
                		"SINGLE PERSISTENT: " + connectionInfo);
                // start inactivity timeout for it.
                return;
            }
            int usable = 0;
            for (Iterator infos = connectionsList.iterator(); infos.hasNext();) {
                SSHConnectionInfo info = (SSHConnectionInfo) infos.next();
                if (info == connectionInfo) {
                    continue;
                } else if (info.getSessionCount() >= MAX_SESSIONS_PER_CONNECTION) {
                    continue;
                } 
                usable++;
            }
            if (usable > 0) {
                connectionInfo.dispose();
                connectionsList.remove(connectionInfo);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": CONNECTION CLOSED: " + connectionInfo);
            } else {
                // start inactivity timeout for it.
                connectionInfo.startTimeout();
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": CONNECTION NOT CLOSED: " + connectionInfo + ", usable left: " + usable + ", total " + connectionsList.size());
            }
        } finally {
            unlock();
        }
    }

    public static void shutdown() {
        lock(Thread.currentThread());
        try {
            for(Iterator lists = ourConnectionsPool.values().iterator(); lists.hasNext();) {
                LinkedList list = (LinkedList) lists.next();
                for (Iterator infos = list.iterator(); infos.hasNext();) {
                    SSHConnectionInfo info = (SSHConnectionInfo) infos.next();
                    info.dispose();
                }
            }
            ourConnectionsPool.clear();
        } finally {
            unlock();
        }
    }
    
    public static int getConnectionsCount() {
        lock(Thread.currentThread());
        try {
            int count = 0;
            for (Iterator keys = ourConnectionsPool.keySet().iterator(); keys.hasNext();) {
                String key = (String) keys.next();
                LinkedList list = (LinkedList) ourConnectionsPool.get(key);
                count += list.size();
            }
            return count;
        } finally {
            unlock();
        }
    }

    private static Connection openConnection(final SVNURL location, final ISVNSSHHostVerifier hostVerifier, SVNSSHAuthentication credentials, int port, int connectTimeout) throws SVNException {
        // open and add to the list.
        File privateKeyFile = credentials.getPrivateKeyFile();
        char[] privateKey = credentials.getPrivateKey();
        if (privateKey == null && privateKeyFile != null) {
            privateKey = readPrivateKey(privateKeyFile);
        }
        String passphrase = credentials.getPassphrase();
        String password = credentials.getPassword();
        String userName = credentials.getUserName();
        
        password = "".equals(password) && privateKey != null ? null : password;
        passphrase = "".equals(passphrase) ? null : passphrase;
        
        if (privateKey != null && !isValidPrivateKey(privateKey, passphrase)) {
            if (password == null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "File ''{0}'' is not valid OpenSSH DSA or RSA private key file", privateKeyFile);
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            } 
            privateKey = null;
        }
        if (privateKey == null && password == null) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Either password or private key should be provided to establish SSH connection");
            SVNErrorManager.error(error, SVNLogType.NETWORK);
        }
        
        Connection connection = new Connection(location.getHost(), port);
        try {
            try {
                connection.connect(new ServerHostKeyVerifier() {
                    public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
                        if (hostVerifier != null) {
                            hostVerifier.verifyHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey);
                        }                    
                        return true;
                    }
                }, connectTimeout, connectTimeout);
            } catch (IOException e) {
                if (e.getCause() instanceof SVNException) {
                    throw (SVNException) e.getCause();
                }
                throw e;
            }
            boolean authenticated = false;
            if (privateKey != null) {
                authenticated = connection.authenticateWithPublicKey(userName, privateKey, passphrase);
            } else if (password != null) {
                String[] methods = connection.getRemainingAuthMethods(userName);
                authenticated = false;
                for (int i = 0; i < methods.length; i++) {
                    if ("password".equals(methods[i])) {
                        authenticated = connection.authenticateWithPassword(userName, password);                    
                    } else if ("keyboard-interactive".equals(methods[i])) {
                        final String p = password;
                        authenticated = connection.authenticateWithKeyboardInteractive(userName, new InteractiveCallback() {
                            public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) throws Exception {
                                String[] reply = new String[numPrompts];
                                for (int i = 0; i < reply.length; i++) {
                                    reply[i] = p;
                                }
                                return reply;
                            }
                        });
                    }
                    if (authenticated) {
                        break;
                    }
                }
            } else {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Either password or private key should be provided to establish SSH connection");
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            }
            if (!authenticated) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSH server rejects provided credentials");
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            }
            return connection;
        } catch (IOException e) {
            if (connection != null) {
                connection.close();
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}'': {1}", new Object[] {location.setPath("", false), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.NETWORK);
        }
        return null;
    }

    private static char[] readPrivateKey(File privateKey) {
        if (privateKey == null || !privateKey.exists() || !privateKey.isFile() || !privateKey.canRead()) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, "Can not read private key from '" + privateKey + "'");
            return null;
        }
        Reader reader = null;
        StringWriter buffer = new StringWriter();
        try {
            reader = new BufferedReader(new FileReader(privateKey));
            int ch;
            while(true) {
                ch = reader.read();
                if (ch < 0) {
                    break;
                }
                buffer.write(ch);
            }
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e);
            return null;
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        return buffer.toString().toCharArray();
    }
    
    private static boolean isValidPrivateKey(char[] privateKey, String passphrase) {
        try {
            PEMDecoder.decode(privateKey, passphrase);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, e);
            return false;
        }        
        return true;
    }
    
    static void lock(Object requestor) {
        synchronized(ourConnectionsPool) {
            if (ourRequestor == requestor) {
                ourLockLevel++;
                return;
            }
            while(ourRequestor != null) {
                try {
                    ourConnectionsPool.wait();
                } catch (InterruptedException e) {
                }
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": LOCKED");
            ourLockLevel++;
            ourRequestor = requestor;
        }
    }
    
    static void unlock() {
        synchronized (ourConnectionsPool) {
            ourLockLevel--;
            if (ourLockLevel <= 0) {
                Object requestor = ourRequestor;
                ourLockLevel = 0;
                ourRequestor = null;
                ourConnectionsPool.notify();
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, requestor + ": UNLOCKED");
            }
        }
    }
    
    static long getTimeout() {
        return ourTimeout;
    }
    
    public static boolean isUsePersistentConnection() {
        lock(Thread.currentThread());
        try {
            return ourIsUsePersistentConnection;
        } finally {
            unlock();
        }
    }
    
    public static void setUsePersistentConnection(boolean usePersistent) {
        lock(Thread.currentThread());
        try {
            ourIsUsePersistentConnection = usePersistent;
        } finally {
            unlock();
        }
    }
    
    public static class SSHConnectionInfo {

        private Connection myConnection;
        private int mySessionCount;
        private boolean myIsPersistent;
        private String myKey;
        private Timer myTimer;
        private String myID;
        
        public SSHConnectionInfo(String key, String id, Connection connection, boolean persistent) {
            myConnection = connection;
            myIsPersistent = persistent;
            myKey = key;
            myID = id;
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": CONNECTION CREATED: " + this);
        }
        
        public boolean isSessionPingSupported() {
            lock(Thread.currentThread());
            try {
                if (myConnection == null) {
                    return false;
                }
                ClientServerHello csh = null;
                try {
                    Method getVersionInfoMethod = myConnection.getClass().getMethod("getVersionInfo", new Class[0]);
                    if (getVersionInfoMethod != null) {
                        Object result = getVersionInfoMethod.invoke(myConnection, new Object[0]);
                        if (result instanceof ClientServerHello) {
                            csh = (ClientServerHello) result;
                        }
                    }
                } catch (SecurityException e1) {
                } catch (NoSuchMethodException e1) {
                } catch (IllegalArgumentException e) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
                String version = null;
                if (csh != null && csh.getServerString() != null) {
                    version = new String(csh.getServerString());
                } else {
                    return false;
                }
                if (version != null && version.indexOf("OpenSSH") >= 0) {
                    return true;
                }
                return false;
            } finally {
                unlock();
            }
        }
        
        public void dispose() {
            lock(Thread.currentThread());
            try {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": DISPOSING: " + this);
                if (myTimer != null) {
                    myTimer.cancel();
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": TIMER CANCELLED: " + this);
                    myTimer = null;
                }
                if (myConnection != null) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, ourRequestor + ": CONNECTION CLOSED: " + this);
                    myConnection.close();
                    myConnection = null;
                }
            } finally {
                unlock();
            }
        }
        
        public void setPersistent(boolean persistent) {
            lock(Thread.currentThread());
            try {
                myIsPersistent = persistent;
            } finally {
                unlock();
            }
        }

        public boolean isPersistent() {
            lock(Thread.currentThread());
            try {
                return myIsPersistent;
            } finally {
                unlock();
            }
        }
        
        public String getKey() {
            lock(Thread.currentThread());
            try {
                return myKey;
            } finally {
                unlock();
            }
        }
        
        public int getSessionCount() {
            lock(Thread.currentThread()); 
            try {
                return mySessionCount;
            } finally {
                unlock();
            }
        }
        
        public Session openSession() throws IOException {
            lock(Thread.currentThread());
            try {
                Session session = myConnection.openSession();
                if (session != null) {
                    mySessionCount++;
                }
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                        ourRequestor + ": SESSION OPENED: " + this + "." + mySessionCount);
                return session;
            } finally {
                unlock();
            }
        }
        
        public void startTimeout() {
            lock(Thread.currentThread());
            try {
                if (ourTimeout <= 0) {
                    return;
                }
                if (mySessionCount <= 0) {
                    mySessionCount = 0;
                    if (isPersistent()) {
                        if (myTimer != null) {
                            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                                    ourRequestor + ": TIMER CANCELLED: " + this);
                            myTimer.cancel();
                        }
                        // start timeout count down (10 seconds).
                        myTimer = new Timer(true);
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                                ourRequestor + ": TIMEOUT TASK SCHEDULED: " + this);
                        myTimer.schedule(new TimerTask() {
                            public void run() {
                                runTimeout();
                            }
                        }, ourTimeout);
                    }
                }
            } finally {
                unlock();
            }
        }

        public void resetTimeout() {
            lock(Thread.currentThread());
            try {
                if (myTimer != null) {
                    myTimer.cancel();
                    myTimer = null;
                }
            } finally {
                unlock();
            }
        }
        
        public boolean closeSession(Session session) {
            lock(Thread.currentThread()); 
            try {
                if (session == null) {
                    return false;
                }
                try {
                    session.close();
                    session.waitForCondition(ChannelCondition.CLOSED, 0);
                } finally {
                    mySessionCount--;
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                            ourRequestor + ": SESSION CLOSED: " + this + "." + mySessionCount);
                }
                if (mySessionCount <= 0) {
                    mySessionCount = 0;
                }
                return mySessionCount <= 0;
            } finally {
                unlock();
            }
        }

        public void runTimeout() {
            lock(Thread.currentThread());
            try {                
                if (mySessionCount > 0) {
                    return;
                }
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, 
                        ourRequestor + ": CLOSING BY TIMEOUT: " + this);
                LinkedList list = (LinkedList) ourConnectionsPool.get(myKey);
                if (list != null && list.contains(this)) {
                    list.remove(this);
                }
                if (list != null && list.isEmpty()) {
                    ourConnectionsPool.remove(myKey);
                }
                dispose();
            } finally {
                unlock();
            }
        }
        
        public String toString() {
            return myID + " [" + hashCode() + "]";
        }

        public boolean isDisposed() {
            return myConnection == null;
        }
    }
}
