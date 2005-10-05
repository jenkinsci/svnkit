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

import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class BasicAuthenticationManager implements ISVNAuthenticationManager, ISVNProxyManager {
    
    private List myPasswordAuthentications;
    private List mySSHAuthentications;
    private int mySSHIndex;
    private int myPasswordIndex;

    private String myProxyHost;
    private int myProxyPort;
    private String myProxyUserName;
    private String myProxyPassword;

    public BasicAuthenticationManager(SVNAuthentication[] authentications) {
        setAuthentications(authentications);
    }
    
    public void setAuthentications(SVNAuthentication[] authentications) {
        myPasswordAuthentications = new ArrayList();
        mySSHAuthentications = new ArrayList();
        myPasswordIndex = 0;
        mySSHIndex = 0;
        for (int i = 0; authentications != null && i < authentications.length; i++) {
            SVNAuthentication auth = authentications[i];
            if (auth instanceof SVNPasswordAuthentication) {
                myPasswordAuthentications.add(auth);                
            } else if (auth instanceof SVNSSHAuthentication) {
                mySSHAuthentications.add(auth);                
            }
        }
    }
    
    public void setProxy(String proxyHost, int proxyPort, String proxyUserName, String proxyPassword) {
        myProxyHost = proxyHost;
        myProxyPort = proxyPort >= 0 ? proxyPort : 80;
        myProxyUserName = proxyUserName;
        myProxyPassword = proxyPassword;
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        if (ISVNAuthenticationManager.SSH.equals(kind) && mySSHAuthentications.size() > 0) {
            mySSHIndex = 0; 
            return (SVNAuthentication) mySSHAuthentications.get(0);
        } else if (ISVNAuthenticationManager.PASSWORD.equals(kind) && myPasswordAuthentications.size() > 0) {
            myPasswordIndex = 0; 
            return (SVNAuthentication) myPasswordAuthentications.get(0);
        }
        throw new SVNAuthenticationException("svn: Authentication required for '" + realm + "'");
    } 

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        if (ISVNAuthenticationManager.SSH.equals(kind) && mySSHAuthentications.size() > mySSHIndex) {
            mySSHIndex++; 
            return (SVNAuthentication) mySSHAuthentications.get(mySSHIndex);
        } else if (ISVNAuthenticationManager.PASSWORD.equals(kind) && myPasswordAuthentications.size() > myPasswordIndex) {
            myPasswordIndex++; 
            return (SVNAuthentication) myPasswordAuthentications.get(myPasswordIndex);
        }
        throw new SVNAuthenticationException("svn: Authentication failed for '" + realm + "'");
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {        
    }

    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        return this;
    }

    public ISVNSSLManager getSSLManager(SVNURL url) throws SVNException {
        return null;
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, String errorMessage, SVNAuthentication authentication) {
    }

    public void setRuntimeStorage(ISVNAuthenticationStorage storage) {
    }

    public boolean isAuthenticationForced() {
        return false;
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

    public void acknowledgeProxyContext(boolean accepted, String errorMessage) {
    }

}
