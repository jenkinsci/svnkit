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
package org.tmatesoft.svn.core.auth;

import java.io.File;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNSSHAuthentication extends SVNAuthentication {

    private String myPassword;
    private String myPassphrase;
    private File myPrivateKey;
    private int myPortNumber;

    public SVNSSHAuthentication(String userName, String password, int portNumber, boolean storageAllowed) {
        super(userName, storageAllowed);
        myPassword = password;
        myPortNumber = portNumber;
    }

    public SVNSSHAuthentication(String userName, File keyFile, String passphrase, int portNumber, boolean storageAllowed) {
        super(userName, storageAllowed);
        myPrivateKey = keyFile;
        myPassphrase = passphrase;
        myPortNumber = portNumber;
    }

    public String getPassword() {
        return myPassword;
    }
    
    public String getPassphrase() {
        return myPassphrase;
    }
    
    public File getPrivateKeyFile() {
        return myPrivateKey;
    }
    
    public int getPortNumber() {
        return myPortNumber;
    }

}
