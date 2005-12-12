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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.InteractiveCallback;
import ch.ethz.ssh2.crypto.PEMDecoder;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNGanymedSession {

    private static Map ourConnectionsPool = new Hashtable();
    private static boolean ourIsUsePersistentConnection;
    
    static {
        String persistent = System.getProperty("javasvn.ssh2.persistent", Boolean.TRUE.toString());
        ourIsUsePersistentConnection = Boolean.TRUE.toString().equals(persistent);
    }

    static Connection getConnection(SVNURL location, SVNSSHAuthentication credentials) throws SVNException {
        if ("".equals(credentials.getUserName()) || credentials.getUserName() == null) {
            throw new SVNAuthenticationException("svn: User name is required to establish svn+shh connection");
        }
        int port = location.hasPort() ? location.getPort() : credentials.getPortNumber();
        if (port < 0) {
            port = 22;
        }
        String key = credentials.getUserName() + ":" + location.getHost() + ":" + port;
        String hostID = location.getHost() + ":" + port;
        Connection connection = isUsePersistentConnection() ? (Connection) ourConnectionsPool.get(key) : null;
        
        if (connection == null) {
            File privateKey = credentials.getPrivateKeyFile();
            String passphrase = credentials.getPassphrase();
            String password = credentials.getPassword();
            String userName = credentials.getUserName();
            
            password = "".equals(password) && privateKey != null ? null : password;
            passphrase = "".equals(passphrase) ? null : passphrase;
            
            if (privateKey != null && !isValidPrivateKey(privateKey, passphrase)) {
                if (password == null) {
                    throw new SVNAuthenticationException("svn: Authentication failed: file '" + privateKey.getAbsolutePath() + "' is not valid OpenSSH DSA or RSA private key file");
                } 
                privateKey = null;
            }
            if (privateKey == null && password == null) {
                throw new SVNAuthenticationException("svn: Either password or OpenSSH private key file required for svn+ssh connection");
            }
            
            connection = new Connection(location.getHost(), port);
            try {
                connection.connect();
                boolean authenticated;
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
                    throw new SVNAuthenticationException("svn: Either password or OpenSSH private key file required for svn+ssh connection");
                }
                if (authenticated) {
                    if (isUsePersistentConnection()) {
                        ourConnectionsPool.put(key, connection);
                    }
                } else {
                    throw new SVNAuthenticationException("svn: SSH server rejects provided credentials (" + (privateKey != null ? "private key" : "password") + ")");
                }
            } catch (IOException e) {
                if (connection != null) {
                    connection.close();
                    if (isUsePersistentConnection()) {
                        ourConnectionsPool.remove(key);
                    }
                }
                SVNErrorManager.error("svn: Connection to '" + hostID + "' failed: '" + e.getMessage() + "'");
            } 
        } 
        return connection;
    }

    private static boolean isValidPrivateKey(File privateKey, String passphrase) {
        if (!privateKey.exists() || !privateKey.isFile() || !privateKey.canRead()) {
            return false;
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
            return false;
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        char[] key = buffer.toString().toCharArray();
        try {
            PEMDecoder.decode(key, passphrase);
        } catch (IOException e) {
            return false;
        }        
        return true;
    }

    public static void shutdown() {
        if (ourConnectionsPool.size() > 0) {
            for (Iterator e = ourConnectionsPool.values().iterator(); e.hasNext();) {
                Connection connection = (Connection) (e.next());
                try {
                    connection.close();
                } catch (Exception ee) {
                }
            }
            ourConnectionsPool.clear();
        }
    }

    static void closeConnection(Connection connection) {
        if (connection != null) {
            connection.close();
            if (!isUsePersistentConnection()) {
                return;
            }
            for (Iterator connections = ourConnectionsPool.entrySet().iterator(); connections.hasNext();) {
                Entry current = (Entry) connections.next();
                if (current.getValue() == connection) {
                    connections.remove();
                    return;
                }
            }
        }
    }
    
    public static boolean isUsePersistentConnection() {
        return ourIsUsePersistentConnection;
    }
    
    public static void setUsePersistentConnection(boolean usePersistent) {
        ourIsUsePersistentConnection = usePersistent;
    }
}
