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
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * The <b>BasicAuthenticationManager</b> is a simple implementation of
 * <b>ISVNAuthenticationManager</b> for storing and providing credentials without
 * using auth providers. A basic manager simply keeps the user credentials provided. 
 * Also this manager may store a single proxy server options context (for HHTP requests 
 * to go through a particular proxy server).
 * 
 * <p>
 * This manager does not use authentication providers (<b>ISVNAuthenticationProvider</b>) but only 
 * those credentials that was supplied to its constructor. Also this manager never 
 * caches credentials.
 * 
 * <p>
 * This manager is not used in SVNKit internals. You may use a default 
 * manager (how to get it read javadoc for {@link ISVNAuthenticationManager}), 
 * this basic manager or implement your own one.  
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNAuthenticationProvider
 */
public class BasicAuthenticationManager implements ISVNAuthenticationManager, ISVNProxyManager {
    
    private List myPasswordAuthentications;
    private List mySSHAuthentications;
    private List myUserNameAuthentications;
    private int mySSHIndex;
    private int myPasswordIndex;
    private int myUserNameIndex;

    private String myProxyHost;
    private int myProxyPort;
    private String myProxyUserName;
    private String myProxyPassword;
    private boolean myIsAuthenticationForced;
    
    /**
     * Creates an auth manager given a user credential - a username 
     * and password. 
     * 
     * @param userName  a username
     * @param password  a password
     */
    public BasicAuthenticationManager(String userName, String password) {
        setAuthentications(new SVNAuthentication[] {
                new SVNPasswordAuthentication(userName, password, false),
                new SVNSSHAuthentication(userName, password, -1, false),
                new SVNUserNameAuthentication(userName, false),
        });        
    }
    
    /**
     * Creates an auth manager given a user credential - a username and 
     * an ssh private key.  
     * 
     * @param userName    a username
     * @param keyFile     a private key file
     * @param passphrase  a password to the private key
     * @param portNumber  a port number over which an ssh tunnel is established
     */
    public BasicAuthenticationManager(String userName, File keyFile, String passphrase, int portNumber) {
        setAuthentications(new SVNAuthentication[] {
                new SVNSSHAuthentication(userName, keyFile, passphrase, portNumber, false),
                new SVNUserNameAuthentication(userName, false),
        });        
    }
    
    /**
     * Creates an auth manager given user credentials to use.
     * 
     * @param authentications user credentials
     */
    public BasicAuthenticationManager(SVNAuthentication[] authentications) {
        setAuthentications(authentications);
    }
    
    /**
     * Sets the given user credentials to this manager.
     * 
     * @param authentications user credentials
     */
    public void setAuthentications(SVNAuthentication[] authentications) {
        myPasswordAuthentications = new ArrayList();
        mySSHAuthentications = new ArrayList();
        myUserNameAuthentications = new ArrayList();
        myPasswordIndex = 0;
        mySSHIndex = 0;
        for (int i = 0; authentications != null && i < authentications.length; i++) {
            SVNAuthentication auth = authentications[i];
            if (auth instanceof SVNPasswordAuthentication) {
                myPasswordAuthentications.add(auth);                
            } else if (auth instanceof SVNSSHAuthentication) {
                mySSHAuthentications.add(auth);                
            } else if (auth instanceof SVNUserNameAuthentication) {
                myUserNameAuthentications.add(auth);                
            }
        }
    }
    
    /**
     * Sets a proxy server context to this manager.
     * 
     * @param proxyHost        a proxy server hostname
     * @param proxyPort        a proxy server port
     * @param proxyUserName    a username to supply to a proxy machine
     * @param proxyPassword    a password to supply to a proxy machine
     */
    public void setProxy(String proxyHost, int proxyPort, String proxyUserName, String proxyPassword) {
        myProxyHost = proxyHost;
        myProxyPort = proxyPort >= 0 ? proxyPort : 3128;
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
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind) && myUserNameAuthentications.size() > 0) {
            myUserNameIndex = 0; 
            return (SVNAuthentication) myUserNameAuthentications.get(0);
        }
        if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            if (url.getUserInfo() != null && !"".equals(url.getUserInfo())) {
                return new SVNUserNameAuthentication(url.getUserInfo(), false);
            }
            // client will use default.
            return new SVNUserNameAuthentication(null, false);
        }
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    } 

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        if (ISVNAuthenticationManager.SSH.equals(kind) && mySSHIndex + 1 < mySSHAuthentications.size()) {
            mySSHIndex++; 
            return (SVNAuthentication) mySSHAuthentications.get(mySSHIndex);
        } else if (ISVNAuthenticationManager.PASSWORD.equals(kind) && myPasswordIndex + 1 < myPasswordAuthentications.size()) {
            myPasswordIndex++; 
            return (SVNAuthentication) myPasswordAuthentications.get(myPasswordIndex);
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind) && myUserNameIndex + 1 < myUserNameAuthentications.size()) {
            myUserNameIndex++; 
            return (SVNAuthentication) myUserNameAuthentications.get(myUserNameIndex);
        } 
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    }
    
    /**
     * Does nothing.
     *  
     * @param provider
     */
    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {        
    }
    
    /**
     * Returns itself as a proxy manager.
     * 
     * @param  url            a repository location that will be accessed 
     *                        over the proxy server for which a manager is needed
     * @return                a proxy manager
     * @throws SVNException   
     */
    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        return this;
    }
    
    /**
     * Returns <span class="javakeyword">null</span>.
     * 
     * @param  url
     * @return <span class="javakeyword">null</span>
     * @throws SVNException
     */
    public ISVNSSLManager getSSLManager(SVNURL url) throws SVNException {
        return null;
    }
    
    /**
     * Does nothing.
     * 
     * @param accepted
     * @param kind
     * @param realm
     * @param errorMessage
     * @param authentication
     */
    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) {
    }
    
    /**
     * Does nothing.
     * 
     * @param storage
     */
    public void setRuntimeStorage(ISVNAuthenticationStorage storage) {
    }

    public boolean isAuthenticationForced() {
        return myIsAuthenticationForced;
    }
    
    public void setAuthenticationForced(boolean forced) {
        myIsAuthenticationForced = forced;
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
    
    /**
     * Does nothing.
     * 
     * @param accepted
     * @param errorMessage
     */
    public void acknowledgeProxyContext(boolean accepted, SVNErrorMessage errorMessage) {
    }

    public long getHTTPTimeout(SVNRepository repository) {
        return 3600*1000;
    }

}
