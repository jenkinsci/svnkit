/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.auth;

import java.io.File;

/**
 * The <b>SVNSSHAuthentication</b> class represents a kind of credentials used 
 * to authenticate a user over an SSH tunnel.
 * 
 * <p> 
 * To obtain an ssh user credential, specify the {@link ISVNAuthenticationManager#SSH SSH} 
 * kind to credentials getter method of <b>ISVNAuthenticationManager</b>: 
 * {@link ISVNAuthenticationManager#getFirstAuthentication(String, String, SVNURL) getFirstAuthentication()}, 
 * {@link ISVNAuthenticationManager#getNextAuthentication(String, String, SVNURL) getNextAuthentication()}.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNAuthenticationManager  
 */
public class SVNSSHAuthentication extends SVNAuthentication {

    private String myPassword;
    private String myPassphrase;
    private File myPrivateKey;
    private int myPortNumber;
    
    /**
     * Creates a user credential object for authenticating over an ssh tunnel. 
     * This kind of credentials is used when an ssh connection requires 
     * a user password instead of an ssh private key.
     * 
     * @param userName         the name of a user to authenticate 
     * @param password         the user's password
     * @param portNumber       the number of a port to establish an ssh tunnel over  
     * @param storageAllowed   if <span class="javakeyword">true</span> then
     *                         this credential is allowed to be stored in the 
     *                         global auth cache, otherwise not
     */
    public SVNSSHAuthentication(String userName, String password, int portNumber, boolean storageAllowed) {
        super(ISVNAuthenticationManager.SSH, userName, storageAllowed);
        myPassword = password;
        myPortNumber = portNumber;
    }
    
    /**
     * Creates a user credential object for authenticating over an ssh tunnel. 
     * This kind of credentials is used when an ssh connection requires 
     * an ssh private key.
     * 
     * @param userName         the name of a user to authenticate 
     * @param keyFile          the user's ssh private key file 
     * @param passphrase       a password to the ssh private key
     * @param portNumber       the number of a port to establish an ssh tunnel over  
     * @param storageAllowed   if <span class="javakeyword">true</span> then
     *                         this credential is allowed to be stored in the 
     *                         global auth cache, otherwise not
     */
    public SVNSSHAuthentication(String userName, File keyFile, String passphrase, int portNumber, boolean storageAllowed) {
        super(ISVNAuthenticationManager.SSH, userName, storageAllowed);
        myPrivateKey = keyFile;
        myPassphrase = passphrase;
        myPortNumber = portNumber;
    }
    
    /**
     * Returns the user account's password. This is used when an  
     * ssh private key is not used. 
     * 
     * @return the user's password
     */
    public String getPassword() {
        return myPassword;
    }
    
    /**
     * Returns the password to the ssh private key. 
     * 
     * @return the password to the private key
     * @see    #getPrivateKeyFile()
     */
    public String getPassphrase() {
        return myPassphrase;
    }
    
    /**
     * Returns the File representation referring to the file with the 
     * user's ssh private key. If the private key is encrypted with a 
     * passphrase, it should have been provided to an appropriate constructor.
     * 
     * @return the user's private key file
     */
    public File getPrivateKeyFile() {
        return myPrivateKey;
    }
    
    /**
     * Returns the number of the port across which an ssh tunnel 
     * is established. 
     * 
     * @return the port number to establish an ssh tunnel over
     */
    public int getPortNumber() {
        return myPortNumber;
    }
}
