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

/**
 * @author TMate Software Ltd.
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
    }
}
