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

package org.tmatesoft.svn.core.io;

import org.tmatesoft.svn.util.DebugLog;

/**
 *  @author TMate Software Ltd.
 */
public class SVNSimpleCredentialsProvider implements ISVNCredentialsProvider {
    
    private SimpleCredentials myCredentials;
    private boolean myIsFinished;

    public SVNSimpleCredentialsProvider(String userName, String password) {
        this(userName, password, null);
    }
    
    public SVNSimpleCredentialsProvider(String userName, String password, String privateKey) {
        this(userName, password, privateKey, null);
    }
    
    public SVNSimpleCredentialsProvider(String userName, String password, String privateKey, String passphrase) {
        myCredentials = new SimpleCredentials(userName, password, privateKey, passphrase);
    }

    public ISVNCredentials nextCredentials(String realm) {
        if (myIsFinished) {
            return null;
        }
        myIsFinished = true;
        return myCredentials;
    }

    public void accepted(ISVNCredentials credentials) {
    }

    public void notAccepted(ISVNCredentials credentials, String failureReason) {
    }
    
    public void reset() {
        myIsFinished = false;
    }
    
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SVNSimpleCredentialsProvider)) {
            return false;
        }
        SVNSimpleCredentialsProvider provider = (SVNSimpleCredentialsProvider) o;
        return myCredentials.equals(provider.myCredentials);
    }
    
    public int hashCode() {
        return myCredentials.hashCode();
    }
    /**
	 * <p>
	 * This class is a wrapper for clients' credentials.
	 * When the server process receives a client request, it typically demands that the
	 * client identify itself. It issues an authentication challenge to the client, and
	 * the client responds by providing <b>credentials</b> back to the server. Once 
	 * authentication is complete, the server responds with the original information
	 * the client asked for. Notice that this system is different from systems like CVS,
	 * where the client pre-emptively offers credentials (“logs in”) to the server
	 * before ever making a request. In Subversion, the server “pulls” credentials by
	 * challenging the client at the appropriate moment, rather than the client
	 * “pushing” them. 
	 * </p>
     * @version 1.0
     * @author TMate Software Ltd.
     */
    public static class SimpleCredentials implements ISVNSSHCredentials {
        private String myPassword;
        private String myUserName;
        private String myPrivateKey;
        private String myPassphrase;

        public SimpleCredentials(String userName, String password, String privateKey, String passprhase) {
            myUserName = userName;
            myPassword = password;
            myPrivateKey = privateKey;
            myPassphrase = passprhase;
            // if key is null, try to get it from javasvn.ssh2.key (workaround for Subclipse)
            if (myPrivateKey == null) {
                myPrivateKey = System.getProperty("javasvn.ssh2.key");
                if (myPrivateKey != null) {
                    DebugLog.log("using private key defined in javasvn.ssh2.key property: " + myPrivateKey);
                }
                if (myPassphrase == null) {
                    myPassphrase = System.getProperty("javasvn.ssh2.passphrase");
                }
            }
        }
        
        public String getName() {
            return myUserName;
        }
        
        public String getPassword() {
            return myPassword;
        }
        
        public String getPrivateKeyID() {
            return myPrivateKey;
        }
        
        public String getPassphrase() {
            return myPassphrase;
        }
        
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SimpleCredentials)) {
                return false;
            }
            SimpleCredentials credentials = (SimpleCredentials) o;
            boolean equals = true;
            equals = myUserName == null ? credentials.myUserName == null : myUserName.equals(credentials.myUserName);
            if (!equals) {
                return false;
            }
            equals = myPassword == null ? credentials.myPassword == null : myPassword.equals(credentials.myPassword);
            if (!equals) {
                return false;
            }
            equals = myPrivateKey == null ? credentials.myPrivateKey == null : myPrivateKey.equals(credentials.myPrivateKey);
            if (!equals) {
                return false;
            }
            return myPassphrase == null ? credentials.myPassphrase == null : myPassphrase.equals(credentials.myPassphrase);
        }
        
        public int hashCode() {
            int hashCode = 17;
            hashCode += myUserName != null ? myUserName.hashCode()*31 : 0;
            hashCode += myPassword != null ? myPassword.hashCode()*31 : 0;
            hashCode += myPrivateKey != null ? myPrivateKey.hashCode()*31 : 0;
            hashCode += myPassphrase != null ? myPassphrase.hashCode()*31 : 0;
            return hashCode;
        }
        
    }
}
