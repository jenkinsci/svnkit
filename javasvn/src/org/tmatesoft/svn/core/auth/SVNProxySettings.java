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

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNProxySettings {
    
    private String myProxyHost;
    private int myProxyPort;
    private String myProxyUserName;
    private String myProxyPassword;
    
    public SVNProxySettings(String host, int port, String userName, String password) {
        myProxyHost = host;
        myProxyPort = port;
        myProxyUserName = userName;
        myProxyPassword = password;
    }

    public String getProxyHost() {
        return myProxyHost;
    }
    
    public int getProxyPort() {
        return myProxyPort;
    }
    
    public String getProxyUserName() {
        return myProxyUserName;
    }
    
    public String getProxyPassword() {
        return myProxyPassword;
    }
}
