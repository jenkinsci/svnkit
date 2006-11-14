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

import org.tmatesoft.svn.core.SVNErrorMessage;
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
 * @version 1.1.0
 * @author  TMate Software Ltd.
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
     * Returns the SSL manager for secure interracting with a 
     * repository.
     * 
     * <p>
     * A default implementation of <b>ISVNAuthenticationManager</b> returns an 
     * SSL manager that uses CA and user certificate files specified in the 
     * standard <i>servers</i> file.
     * 
     * <p>     
     * Even if the default manager's <b>getSSLManager()</b> method returns 
     * <span class="javakeyword">null</span> for the given <code>url</code>, a secure 
     * context will be created anymore, but, of course no user certificate files are provided 
     * to a server as well as server's certificates are not checked.  
     * 
     * @param  url            a repository location to access 
     * @return                an appropriate SSL manager
     * @throws SVNException
     */
    public ISVNSSLManager getSSLManager(SVNURL url) throws SVNException;
    
    /**
     * Retrieves the first user credential.
     * 
     * The scheme of retrieving credentials:
     * <ul>
     * <li>For the first try to authenticate a user to a repository (using the 
     *     specifed realm) an <b>SVNRepository</b> driver calls 
     *     <b>getFirstAuthentication()</b> and sends the retrieved credential.
     * <li>If the credential is accepted, it may be stored. If not, the driver 
     *     calls {@link #getNextAuthentication(String, String, SVNURL) getNextAuthentication()} 
     *     and sends the next credential.
     * <li>If the last credential was not accepted, the driver still tries to get the next 
     *     credential for the same realm.   
     * </ul>
     * 
     * <p>
     * For each credential <code>kind</code> an implementor should return a kind-specific 
     * credential. The following table matches kinds to proper credential classes:
     * 
     * <table cellpadding="3" cellspacing="1" border="0" width="60%" bgcolor="#999933">
     * <tr bgcolor="#ADB8D9" align="left">
     * <td><b>Credential Kind</b></td>
     * <td><b>Credential Class</b></td>
     * </tr>   
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #PASSWORD}</td><td>{@link SVNPasswordAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #SSH}</td><td>{@link SVNSSHAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #SSL}</td><td>{@link SVNSSLAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #USERNAME}</td><td>{@link SVNUserNameAuthentication}</td>
     * </tr>
     * </table>
     * 
     * @param  kind              a credential kind 
     * @param  realm             a repository authentication realm 
     * @param  url               a repository location that is to be accessed
     * @return                   the first try user credential
     * @throws SVNException
     */
    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException;
    
    /**
     * Retrieves the next user credential if the first try failed.
     * 
     * The scheme of retrieving credentials:
     * <ul>
     * <li>For the first try to authenticate a user to a repository (using the 
     *     specifed realm) an <b>SVNRepository</b> driver calls 
     *     {@link #getFirstAuthentication(String, String, SVNURL) getFirstAuthentication()} and 
     *     sends the retrieved credential.
     * <li>If the credential is accepted, it may be stored. If not, the driver 
     *     calls <b>getNextAuthentication()</b> and sends the next credential.
     * <li>If the last credential was not accepted, the driver still tries to get the next 
     *     credential for the same realm.   
     * </ul>
     * 
     * <p>
     * For each credential <code>kind</code> an implementor should return a kind-specific 
     * credential. The following table matches kinds to proper credential classes:
     * 
     * <table cellpadding="3" cellspacing="1" border="0" width="60%" bgcolor="#999933">
     * <tr bgcolor="#ADB8D9" align="left">
     * <td><b>Credential Kind</b></td>
     * <td><b>Credential Class</b></td>
     * </tr>   
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #PASSWORD}</td><td>{@link SVNPasswordAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #SSH}</td><td>{@link SVNSSHAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #SSL}</td><td>{@link SVNSSLAuthentication}</td>
     * </tr>
     * <tr bgcolor="#EAEAEA" align="left">
     * <td>{@link #USERNAME}</td><td>{@link SVNUserNameAuthentication}</td>
     * </tr>
     * </table>
     * 
     * @param  kind              a credential kind 
     * @param  realm             a repository authentication realm 
     * @param  url               a repository location that is to be accessed
     * @return                   the next try user credential
     * @throws SVNException
     */
    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException;
    
    /**
     * Accepts the given authentication if it was successfully accepted by a 
     * repository server, or not if authentication failed. As a result the 
     * provided credential may be cached (authentication succeeded) or deleted 
     * from the cache (authentication failed).
     * 
     * @param accepted       <span class="javakeyword">true</span> if 
     *                       the credential was accepted by the server, 
     *                       otherwise <span class="javakeyword">false</span>
     * @param kind           a credential kind ({@link #PASSWORD} or {@link #SSH} or {@link #USERNAME})
     * @param realm          a repository authentication realm 
     * @param errorMessage   the reason of the authentication failure 
     * @param authentication a user credential to accept/drop
     * @throws SVNException 
     */
    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException;
    
    /**
     * Sets a specific runtime authentication storage manager. This storage 
     * manager will be asked by this auth manager for cached credentials as 
     * well as used to cache new ones accepted recently.
     * 
     * @param storage a custom auth storage manager
     */
    public void setRuntimeStorage(ISVNAuthenticationStorage storage);
    
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
     * 
     * @see    #setAuthenticationForced(boolean)
     */
    public boolean isAuthenticationForced();

    /**
     * Specifies the way how credentials are to be supplied to a 
     * repository server.
     * 
     * @param forced  <span class="javakeyword">true</span> to force 
     *                credentials sending; <span class="javakeyword">false</span> 
     *                to put off sending credentials till a server challenge
     * @see           #isAuthenticationForced()
     */
    public void setAuthenticationForced(boolean forced);
    
    /**
     * Returns a connection timeout value.
     * 
     * @param   repository a repository access driver
     * @return             connection timeout value
     * @since   1.1
     */
    public long getHTTPTimeout(SVNRepository repository);
}
