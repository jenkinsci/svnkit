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

import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.tmatesoft.svn.core.SVNErrorMessage;

/**
 * The <b>ISVNSSLManager</b> interface is intended for 
 * creating secure SSL contexts over sockets used for data i/o. 
 * 
 * <p>
 * When accessing a repository over http:// there's a risk that 
 * passwords (in the case of a BASIC authentication they are transmitted 
 * as a plain text) may be sniffed by a malefactor. SSL manager provides 
 * a secure connection encrypting all data i/o over a socket.
 * 
 * <p>
 * To get an SSL manager to access a particular repository use the 
 * {@link ISVNAuthenticationManager#getSSLManager(SVNURL) getSSLManager()} 
 * method of an authentication manager.
 * 
 * <p>
 * A default implementation of <b>ISVNSSLManager</b> (that comes along 
 * with a default implementation of <b>ISVNAuthenticationManager</b> - <b>org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager</b>) 
 * uses ssl options from the standard <i>servers</i> file (it can be found in the 
 * Subversion runtime configuration area - read more {@link org.tmatesoft.svn.core.wc.ISVNOptions here}).  
 * That is to accept a server certificate, it first looks for the "trusted" CA sertificate in the in-memory 
 * runtime auth storage (see {@link ISVNAuthenticationStorage}). If the one is not found, it then tries to 
 * find it in the disk auth storage in the runtime config area. Also if the 
 * <span class="javastring">"ssl-trust-default-ca"</span> is set to <span class="javastring">"yes"</span>, then 
 * SVNKit will trust those CAs found in the JDK "JKS" KeyStore. User certificates are also got from the 
 * options in the <i>servers</i> file.
 * 
 * <p>
 * An SSL manager is invoked when a user tries to access a repository via the https:// protocol. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     ISVNAuthenticationManager
 */
public interface ISVNSSLManager {
    
    /**
     * Returns an SSL context for the appropriate authentiation realm. 
     * 
     * @return              an ssl context
     * @throws IOException  if an i/o error occurred
     */
    public SSLContext getSSLContext() throws IOException;
    
    /**
     * @return true if user should be prompted for client certificate
     */
    public boolean isClientCertPromptRequired();
    
    
    /**
     * Sets client authentication that will be used in SSLContext.
     * 
     *  @param sslAuthentication a client authentication
     */
    public void setClientAuthentication(SVNSSLAuthentication sslAuthentication);
    
    /**
     * Returns client authentication.
     * 
     * @return client authentication
     */
    public SVNSSLAuthentication getClientAuthentication();
    
    /**
     * Accepts this SSL context if authentication has succeeded or 
     * not if authentication failed. 
     * 
     * @param accepted      <span class="javakeyword">true</span> if 
     *                      authentication succeeded, otherwise 
     *                      <span class="javakeyword">false</span>
     * @param errorMessage  the reason of the authentication failure
     */
    public void acknowledgeSSLContext(boolean accepted, SVNErrorMessage errorMessage);
}
