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

import javax.net.ssl.TrustManager;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>ISVNAuthenticationManager</b> is implemented by manager 
 * classes used by <b>SVNRepository</b> drivers for user authentication purposes. 
 * 
 * <p>
 * When an <b>SVNRepository</b> driver is created, you should provide an 
 * authentication manager via a call to:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepository;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
 * ...
 *     SVNRepository repository;
 *     ISVNAuthenticationManager authManger;
 *     ...
 *     
 *     repository.setAuthenticationManager(authManager);
 *     ...</pre>
 * 
 * <p>
 * SVNKit provides a default authentication manager implementation - <b>org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager</b>. 
 * This manager has got the following features:
 * <ul>
 * <li> uses the auth storage from the default Subversion runtime configuration area;
 * <li> may use the auth storage in the directory you specify;
 * <li> uses the ssh, ssl & proxy options from the standard <i>config</i> and <i>servers</i> files;
 * <li> stores credentials in the in-memory cache during runtime;
 * </ul>
 * You may also specify your own auth provider (<b>ISVNAuthenticationProvider</b>) to this default manager, it 
 * will be used along with those default ones, that implement the features listed above.   
 * 
 * <p>
 * If using the https:// protocol and if no user's authentication provider implementation is set to the 
 * default manager, server certificates are accepted temporarily and therefore are not cached on the disk. 
 * To enable server CAs caching, a user should set an authentication provider implementation which 
 * {@link ISVNAuthenticationProvider#acceptServerAuthentication(SVNURL, String, Object, boolean) acceptServerAuthentication()} 
 * method must return {@link ISVNAuthenticationProvider#ACCEPTED}. That will switch on certificate on-the-disk caching. 
 * 
 * <p>
 * How to get a default auth manager instance see {@link org.tmatesoft.svn.core.wc.SVNWCUtil}. 
 *
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     org.tmatesoft.svn.core.io.SVNRepository
 */
public interface ISVNAuthenticationManager {
    /**
     * A simple password credential kind (<span class="javastring">"svn.simple"</span>)
     */
    public static final String PASSWORD = "svn.simple";
    /**
     * An ssh credential kind (<span class="javastring">"svn.ssh"</span>)
     */
    public static final String SSH = "svn.ssh";
    /**
     * An ssl credential kind (<span class="javastring">"svn.ssl"</span>)
     */
    public static final String SSL = "svn.ssl";

    /**
     * A simple username credential kind (<span class="javastring">"svn.username"</span>). 
     * Only usernames are cached/provided matched against an appropriate 
     * realms (which are repository UUIDs in this case). In particular this kind is 
     * used in <code>file:///</code> and <code>svn+ssh://</code> access schemes.
     */
    public static final String USERNAME = "svn.username";
    
    /**
     * Sets a custom authentication provider that will provide user 
     * credentials for authentication.  
     * 
     * @param provider an authentication provider
     */
    public void setAuthenticationProvider(ISVNAuthenticationProvider provider);
    
    /**
     * Returns a proxy manager that keeps settings for that proxy 
     * server over which HTTP requests are send to a repository server.  
     * 
     * <p>
     * A default auth manager uses proxy settings from the standard <i>servers</i> 
     * file.
     * 
     * @param  url            a repository location that will be accessed 
     *                        over the proxy server for which a manager is needed
     * @return                a proxy manager
     * @throws SVNException   
     */
    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException;

    /**
     * Returns a manager which handles trust data for the specified <code>url</code>.
     *   
     * <p/>
     * Note: in pre-1.2.0 versions <code>ISVNAuthenticationManager</code> used to provide <code>ISVNSSLManager</code> 
     * via a method <code>getSSLManager()</code> which is now replaced by this one. <code>ISVNSSLManager</code> 
     * is no longer used (replaced by <code>TrustManager</code>).
     * 
     * @param  url          repository url                
     * @return              trust manager
     * @throws SVNException
     * @since               1.2.0 
     */
	public TrustManager getTrustManager(SVNURL url) throws SVNException;		

    /**
     * Enumerates {@link SVNAuthentication} to be tried on to access the given repository.
     */
    public Iterable<SVNAuthAttempt> getAuthentications(String kind, String realm, SVNURL url) throws SVNException;

    /**
     * Acknowledges the specified trust manager. This method is called only when a secure connection is 
     * successfully established with the specified <code>manager</code>. 
     * 
     * @param manager trust manager to acknowledge (one returned by {@link #getTrustManager(SVNURL)})
     * @since         1.2.0
     */
	public void acknowledgeTrustManager(TrustManager manager);

    /**
     * Checks whether client should send authentication credentials to 
     * a repository server not waiting for the server's challenge. 
     * 
     * <p>
     * In some cases it may be necessary to send credentials beforehand, 
     * not waiting until the server asks to do it itself. To achieve 
     * such behaviour an implementor should return <span class="javakeyword">true</span> 
     * from this routine.
     * 
     * @return <span class="javakeyword">true</span> if authentication 
     *         credentials are forced to be sent;<span class="javakeyword">false</span> 
     *         when credentials are to be sent only in response to a server challenge    
     */
    public boolean isAuthenticationForced();

    /**
     * Returns the read timeout value in milliseconds which <code>repository</code> should use in
     * socket read operations. Socket read operations will block only for this amount of time. 
     * 
     * @param   repository a repository access driver
     * @return             connection timeout value
     * @since   1.2.0
     */
    public int getReadTimeout(SVNRepository repository);
    
    /**
     * Returns the connection timeout value in milliseconds which <code>repository</code>
     * should use in network connection operations.
     *  
     * @param  repository  repository access object
     * @return             connection timeout value in milliseconds which will be set 
     *                     to a socket
     * @since 1.2.0
     */
    public int getConnectTimeout(SVNRepository repository);
}
