/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
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
import java.util.Collections;

import javax.net.ssl.TrustManager;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
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
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNAuthenticationProvider
 */
public class BasicAuthenticationManager implements ISVNAuthenticationManager, ISVNProxyManager {
    
    private List myPasswordAuthentications;
    private List mySSHAuthentications;
    private List myUserNameAuthentications;
    private List mySSLAuthentications;
    
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
        mySSLAuthentications = new ArrayList();
        for (int i = 0; authentications != null && i < authentications.length; i++) {
            SVNAuthentication auth = authentications[i];
            if (auth instanceof SVNPasswordAuthentication) {
                myPasswordAuthentications.add(auth);                
            } else if (auth instanceof SVNSSHAuthentication) {
                mySSHAuthentications.add(auth);                
            } else if (auth instanceof SVNUserNameAuthentication) {
                myUserNameAuthentications.add(auth);                
            } else if (auth instanceof SVNSSLAuthentication) {
                mySSLAuthentications.add(auth);                
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

    public Iterable<SVNAuthAttempt> getAuthentications(final String kind, final String realm, final SVNURL url) throws SVNException {
        if (ISVNAuthenticationManager.SSH.equals(kind))
            return SVNAuthAttempt.wrap(mySSHAuthentications);
        if (ISVNAuthenticationManager.PASSWORD.equals(kind))
            return SVNAuthAttempt.wrap(myPasswordAuthentications);
        if (ISVNAuthenticationManager.USERNAME.equals(kind))
            return SVNAuthAttempt.wrap(myUserNameAuthentications);
        if (ISVNAuthenticationManager.SSL.equals(kind))
            return SVNAuthAttempt.wrap(mySSLAuthentications);
        if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            if (url.getUserInfo() != null && !"".equals(url.getUserInfo())) {
                return Collections.singleton(new SVNAuthAttempt(new SVNUserNameAuthentication(url.getUserInfo(), false)));
            }
            // client will use default.
            return Collections.singleton(new SVNAuthAttempt(new SVNUserNameAuthentication(null, false)));
        }
        return Collections.emptyList();
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
     * @param  url             repository url
     * @return                 <span class="javakeyword">null</span>
     * @throws SVNException
     * @since                  1.2.0       
     */
	public TrustManager getTrustManager(SVNURL url) throws SVNException {
		return null;
	}

    /**
     * Does nothing.
     * 
     * @param manager
     * @since          1.2.0 
     */
	public void acknowledgeTrustManager(TrustManager manager) {
	}

	/**
	 * Tells whether authentication should be tried despite not being challenged from the server yet.
	 * 
	 * <p/>
	 * By default the return value is always <span class="javakeyword">false</span> until this behavior is 
	 * changed via a call to {@link #setAuthenticationForced(boolean)}. 
	 * 
	 * @return  authentication force flag 
	 */
	public boolean isAuthenticationForced() {
        return myIsAuthenticationForced;
    }
    
	/**
	 * Sets whether authentication should be forced or not.
	 * 
	 * @param forced  authentication force flag
	 * @see           #isAuthenticationForced() 
	 */
    public void setAuthenticationForced(boolean forced) {
        myIsAuthenticationForced = forced;
    }

    /**
     * Returns the proxy host name.
     * 
     * @return the proxy host argument value specified via the {@link #setProxy(String, int, String, String)} 
     *         method 
     */
    public String getProxyHost() {
        return myProxyHost;
    }

    /**
     * Returns the proxy port number.
     * @return the proxy port argument value specified via the {@link #setProxy(String, int, String, String)} 
     *         method 
     */
    public int getProxyPort() {
        return myProxyPort;
    }

    /**
     * Returns the proxy user name.
     * @return the proxy user name argument value specified via the {@link #setProxy(String, int, String, String)} 
     *         method 
     */
    public String getProxyUserName() {
        return myProxyUserName;
    }

    /**
     * Returns the password to authenticate against the proxy server.
     * @return the proxy password argument value specified via the {@link #setProxy(String, int, String, String)} 
     *         method 
     */
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

    /**
     * Returns connection timeout value.
     * 
     * <p/>
     * This implementation returns a read timeout value equal to 3600 seconds for <code>http</code> 
     * or <code>https</code> access operations. If <code>repository</code> uses a different access protocol,
     * the return value will be 0.
     * 
     * @param  repository  repository access object 
     * @return             read timeout value in milliseconds
     * @since              1.2.0
     */
    public int getReadTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return 3600*1000;
        }
        return 0;
    }

    /**
     * Returns connection timeout value.
     * 
     * <p/>
     * This implementation returns a connection timeout value equal to 60 seconds for <code>http</code> 
     * or <code>https</code> access operations. If <code>repository</code> uses a different access protocol,
     * the return value will be 0.
     * 
     * @param  repository  repository access object 
     * @return             connection timeout value in milliseconds
     * @since              1.2.0
     */
    public int getConnectTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return 60*1000;
        }
        return 0; 
    }

}
