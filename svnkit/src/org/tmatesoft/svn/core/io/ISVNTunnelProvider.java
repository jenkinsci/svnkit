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
package org.tmatesoft.svn.core.io;


/**
 * The <b>ISVNTunnelProvider</b> is the interface for 
 * providers of tunnel command lines matching a specific 
 * <code>"svn+xxx"</code> tunnel scheme. 
 * 
 * <p>
 * With Subversion you may put your own URL scheme into the 
 * <code>config</code> file under the <code>tunnels</code> 
 * section like this:
 * <pre class="javacode">
 * ssh = $SVN_SSH ...
 * rsh = $SVN_RSH ...
 * ...</pre>
 * The idea of this tunnel provider interface is the same: 
 * given a subprotocol name (a string following <code>svn+</code>, 
 * like <code>ssh</code>) a provider returns a command string 
 * (like <code>$SVN_SSH ...</code>).
 * 
 * <p>
 * A tunnel provider is passed to an <b>SVNRepository</b> driver 
 * that is expected to work through a tunnel (see {@link SVNRepository#setTunnelProvider(ISVNTunnelProvider) 
 * SVNRepository.setTunnelProvider()}). Just as you instantiate an <b>SVNRepository</b> object 
 * set it to use your tunnel provider.  
 * 
 * <p>
 * If you would like to use tunnel scheme definitions from the 
 * standard Subversion <code>config</code> file, you may use 
 * a default provider implementation which is a default options 
 * driver you get calling a <b>createDefaultOptions()</b> method 
 * of the {@link org.tmatesoft.svn.core.wc.SVNWCUtil} class.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNTunnelProvider {
    
    /**
     * Returns a tunnel comand line matching the given subprotocol 
     * name. 
     * 
     * @param  subProtocolName an svn protocol extension 
     *                         (like <code>ssh</code>) 
     * @return                 a tunnel command line
     */
    public String getTunnelDefinition(String subProtocolName);

}
