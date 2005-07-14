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

import org.tmatesoft.svn.core.wc.SVNAuthentication;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNAuthenticationManager {
    
    public static final String PASSWORD = "password";
    public static final String SSH = "ssh";
    public static final String USERNAME = "username";

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider);
    
    public ISVNProxyManager getProxyManager(String url);
    
    public ISVNSSLManager getSSLManager(String url);
    
    public SVNAuthentication getFirstAuthentication(String kind, String realm);

    public SVNAuthentication getNextAuthentication(String kind, String realm);
    
    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, String errorMessage, SVNAuthentication authentication);

}
