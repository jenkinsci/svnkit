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

public class SVNSSLAuthentication extends SVNAuthentication {

    private File myCertificate;
    private String myPassword;
    
    public SVNSSLAuthentication(File certFile, String password, boolean storageAllowed) {
        super(null, storageAllowed);
        myCertificate = certFile;
        myPassword = password;
    }

    public String getPassword() {
        return myPassword;
    }

    public File getCertificateFile() {
        return myCertificate;
    }
}
