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
 * <code>SVNSimpleCredentialsProvider</code> is an implementation of the client 
 * credentials provider interface - <code>ISVNCredentialsProvider</code>. It lets
 * a user to provide his name/password individual information to the Repository
 * Access Layer for a further authentication that can be requested by a repository
 * server.
 * 
 * <p>
 * To define his credentials the user simply creates an instance of 
 * <code>SVNSimpleCredentialsProvider</code> passing his name & password to a 
 * constructor and registers this provider in his <code>SVNRepository</code> object:
 * <blockquote><pre>
 * 		import org.tmatesoft.svn.core.io.*;
 * 
 * 		<i>//somewhere creates an <code>SVNRepository</code></i>
 * 		SVNRepository repository = SVNRepositoryFactory.create(location);
 * 		
 * 		<i>//......</i>
 * 		
 * 		<i>//creates an <code>SVNSimpleCredentialsProvider</code></i>
 * 		SVNSimpleCredentialsProvider scp = new SVNSimpleCredentialsProvider("userName", "userPassword");
 * 		
 * 		<i>//registers the provider in his <code>SVNRepository</code></i>
 * 		repository.setCredentialsProvider(scp);
 * 
 * </blockquote></pre>
 * 
 * <p>
 * This provider class stores a single name/password info (exactly one instance of
 * <code>SimpleCredentials</code> at a time) per one user account.
 * 
 * <p>
 * <b>See</b> additional notices on the {@link #nextCredentials(String)} method.
 * 
 * @version	1.0     
 * @author 	TMate Software Ltd.
 * @see		SimpleCredentials
 * @see		ISVNCredentialsProvider
 * @see		SVNRepository#setCredentialsProvider(ISVNCredentialsProvider)
 */
public class SVNSimpleCredentialsProvider implements ISVNCredentialsProvider {
    
    private SimpleCredentials myCredentials;
    private boolean myIsFinished;
    
    /**
     * Constructs an <code>SVNSimpleCredentials</code> given  user's name & password
     * account info.
     *  
     * @param userName	a user's account name in a repository
     * @param password	a user's password for the account <code>name</code>
     */
    public SVNSimpleCredentialsProvider(String userName, String password) {
        this(userName, password, null);
    }
    
    /**
     * Constructs an <code>SVNSimpleCredentials</code> given  user's name & password
     * account info.
     *  
     * @param userName		a user's account name in a repository
     * @param password		a user's password for the account <code>name</code>
     * @param privateKey	
     */
    public SVNSimpleCredentialsProvider(String userName, String password, String privateKey) {
        this(userName, password, privateKey, null);
    }

    /**
     * Constructs an <code>SVNSimpleCredentials</code> given  user's name & password
     * account info.
     *  
     * @param userName		a user's account name in a repository
     * @param password		a user's password for the account <code>name</code>
     * @param privateKey
     * @param passphrase	
     */
    public SVNSimpleCredentialsProvider(String userName, String password, String privateKey, String passphrase) {
        myCredentials = new SimpleCredentials(userName, password, privateKey, passphrase);
    }
    
    /**
     * Gets user's credentials. 
     * 
     * <p>
     * As a user can have several accounts in different repository account namespaces 
     * (realms in other words) an implementation of <code>ISVNCredentialsProvider</code>
     * is assumed to possibly store more than one user's credentials at a time. And 
     * the <code>nextCredentials()</code> method is used to retrieve a next 
     * <code>ISVNCredentials</code>-implementation (per one user's account) from the
     * sequence of all the provided by the user. If these credentials are not accepted
     * by the repository server the Repository Access Layer can perform 
     * <code>ISVNCredentialsProvider</code>'s 
     * {@link ISVNCredentialsProvider#notAccepted(ISVNCredentials, String) notAccepted()}
     * to handle this fact or 
     * {@link ISVNCredentialsProvider#accepted(ISVNCredentials) accepted()} in an 
     * opposite case (when accepted). When the credentials list is over the 
     * <code>nextCredentials()</code> method returns <code>null</code> and 
     * the provider should be reset with a call to 
     * {@link ISVNCredentialsProvider#reset()} to move to the very beginning of the
     * credentials list.
     * 
     * <p> 
     * As the <code>SVNSimpleCredentials</code> class stores only one 
     * <code>ISVNCredentials</code>-instance per one user's account this method 
     * implementation call should be preceded by a call to {@link #reset()}. 
     * 
     * @param realm		a name of a repository account namespace
     * @return			next user's credentials (if any) or <code>null</code>
     * 					if the credentials list is over
     */
    public ISVNCredentials nextCredentials(String realm) {
        if (myIsFinished) {
            return null;
        }
        myIsFinished = true;
        return myCredentials;
    }
    
    /**
     *  Does nothing.
     * 
     */
    public void accepted(ISVNCredentials credentials) {
    }
    
    /**
     * Does nothing.
     * 
     */
    public void notAccepted(ISVNCredentials credentials, String failureReason) {
    }
    
    /**
     * Resets the list of credentials to the very beginning.
     * 
     * @see		#nextCredentials(String) 
     */
    public void reset() {
        myIsFinished = false;
    }
    
    /**
     * Compares this object with another one.
     * 
     * @return <code>true</code> if <code>this=o</code> or if <code>o</code> is an
     * 			</code>SVNSimpleCredentialsProvider</code> and both (<code>o</code>
     * 			and <code>this</code>) have the same credentials
     * @see		SimpleCredentials#equals(Object)
     */
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
    
    /**
     * Returns a hash code for the <code>ISVNCredentials</code> currently in use.
     * 
     * @return	a hash code value
     */
    public int hashCode() {
        return myCredentials.hashCode();
    }
    
    /**
	 * This class is an implementation of the <code>ISVNSSHCredentials</code> 
	 * interface that is used to store a client's account name and password as
	 * well as his private key and the key passphrase when accessing a repository via
	 * an SSH tunnel (<code>svn+ssh</code> protocol).  
	 * 
	 * <p>
	 * It is destined for usage by its outer class - 
	 * <code>SVNSimpleCredentialsProvider</code> as a client's credentials source.
	 * 
     * @version 	1.0
     * @author 		TMate Software Ltd.
     * @see			ISVNSSHCredentials
     */
    public static class SimpleCredentials implements ISVNSSHCredentials {
        private String myPassword;
        private String myUserName;
        private String myPrivateKey;
        private String myPassphrase;
        
        /**
         * Constructs an instance of <code>SimpleCredentials</code> given a client's 
         * account name, password and, if an SSH tunnel is to be used for data 
         * interchanging between the client and the server, - also the client's
         * private key and passphrase to the key.
         * 
         * <p>
         * If the <code>privateKey</code> is <code>null</code> the constructor tries
         * to get a key from the "javasvn.ssh2.key" system property. So does it
         * when the <code>passprhase</code> is <code>null</code> (but now - from the 
         * "javasvn.ssh2.passphrase" system property).
         * 
         * <p>
         * Actually the SSH2 version of the <i>SSH</i> protocol is used.
         *  
         * @param userName		a client's account name in a repository
         * @param password		a client's password for the account <code>name</code>
         * @param privateKey	a client's private key 
         * @param passprhase	a client's passphrase for the key
         */
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
        
        /**
         * Gets the client's account name.
         * 
         * @return	the client's name string
         */
        public String getName() {
            return myUserName;
        }
        
        /**
         * Gets the client's pasword for his account name.
         * 
         * @return	the client's password string 
         */
        public String getPassword() {
            return myPassword;
        }
        
        /**
         * Gets the client's private key (if any)
         * 
         * @return	the client's private key to be used for securing an SSH tunnel
         */
        public String getPrivateKeyID() {
            return myPrivateKey;
        }
        
        /**
         * Gets the passphrase to the client's private key
         * 
         * @return	the passphrase string to the client's private key
         * @see		#getPrivateKeyID()
         */
        public String getPassphrase() {
            return myPassphrase;
        }
        
        /**
         * Compares this object with another.
         *  
         * @param o	 an <code>Object</code> to be compared with this one
         * @return	 <code>true</code> when (<code>this=o</code>) or (<code>o</code> is 
         * 			 a <code>SimpleCredentials</code> and both <code>o</code> and 
         * 			 <code>this</code> have the same (equal) user name (or 
         * 			 <code>null</code> for both), user password,.., passphrase);
         * 			 <code>false</code> - otherwise.
         * 
         */
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
        
        /**
         * Returns a hash code for this object evaluated as:
         * <blockquote><pre>
         * 		17 + (userNameString!=null ? userNameString.hashCode()*31 : 0) + 
         * 		(userPasswordString!=null ? userPasswordString.hashCode()*31 : 0) +
         * 		(userPrivateKeyString!=null ? userPrivateKeyString.hashCode()*31 : 0) +
         * 		(userPassphraseString!=null ? userPassphraseString.hashCode()*31 : 0)
         * </pre></blockquote>
         * 
         * @return	 a hash code value for this object
         */
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
