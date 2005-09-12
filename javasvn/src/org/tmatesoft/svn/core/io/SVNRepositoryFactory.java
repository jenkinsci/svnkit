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

package org.tmatesoft.svn.core.io;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * <b>SVNRepositoryFactory</b> is an abstract class that is responsible
 * for creating an appropriate <b>SVNRepository</b>-extansion that 
 * will be used to interact with a Subversion repository.
 * 
 * <p>
 * Depending on what protocol a user exactly would like to use
 * to access the repository he should first of all set up an 
 * appropriate extension of this factory. So, if the user is going to
 * work with the repository via the custom <i>svn</i>-protocol (or 
 * <i>svn+ssh</i>) he initially calls:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * ...		
 *     <span class="javacomment">//do it once in your application prior to using the library</span>
 *     <span class="javacomment">//via the SVN-protocol (over svn and svn+ssh)</span>
 *     SVNRepositoryFactoryImpl.setup();
 * ...</pre><br />
 * That <b>setup()</b> method registers an 
 * <b>SVNRepositoryFactoryImpl</b> instance in the factory (calling
 * {@link #registerRepositoryFactory(String, SVNRepositoryFactory) registerRepositoryFactory}). From 
 * this point the <b>SVNRepositoryFactory</b> knows how to create
 * <b>SVNRepository</b> instances specific for the <i>svn</i>-protocol.
 * And further the user can create an <b>SVNRepository</b> instance:
 * <pre class="javacode">
 *     ...
 *     <span class="javacomment">//the user gets an SVNRepository not caring</span>
 *     <span class="javacomment">//how it's implemented for the svn-protocol</span>
 *     SVNRepository repository = SVNRepositoryFactory.create(location);
 *     ...</pre><br />
 * All that was previously said about the <i>svn</i>-protocol is similar for
 * the <i>WebDAV</i>-protocol:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
 * ...
 * 
 *     <span class="javacomment">//do it once in your application prior to using the library</span>
 *     <span class="javacomment">//via the DAV-protocol (over http and https)</span>
 *     DAVRepositoryFactory.setup();
 * ...</pre>
 * <p>
 * <b>NOTE:</b> unfortunately, at present the JavaSVN library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		SVNRepository
 */
public abstract class SVNRepositoryFactory {
    
    private static final Map myFactoriesMap = new HashMap();
    
    /**
     * Registers a protocol dependent factory (extending this factory class) that
     * will be further used to create protocol dependent instances of 
     * <code>SVNRepository</code>.
     * 
     * @param protocol	a string describing the protocol to be used
     * @param factory	a factory to be registered for creating instances of 
     * 					<code>SVNRepository</code> specialized for the 
     * 					specified <code>protocol</code>
     * @see 			SVNRepository
     */
    protected static void registerRepositoryFactory(String protocol, SVNRepositoryFactory factory) {
        if (protocol != null && factory != null) {
            if (!myFactoriesMap.containsKey(protocol)) {
                myFactoriesMap.put(protocol, factory);
            }
        }
    }
    
    protected static boolean hasRepositoryFactory(String protocol) {
        if (protocol != null) {
            return myFactoriesMap.get(protocol) != null;
        }
        return false;
    }
    
    /**
     * Creates an <code>SVNRepository</code> according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is defined as the beginning part of the URL schema (used to connect to the 
     * repository) incapsulated in the <code>url</code> parameter.
     * 
     * <p>
     * In fact, this method doesn't create an <code>SVNRepository</code> instance but
     * calls the {@link #createRepositoryImpl(SVNURL)} of the registered
     * factory (protocol specific extension of this class) that essentially creates the
     * instance; then this routine simply returns it to the caller.
     *  
     * @param  url				a url (to connect to a repository) 
     * 							as an <code>SVNURL</code> object
     * @return					a new instance of <code>SVNRepository</code> to interact
     * 							with the repository
     * @throws SVNException		if there's no implementation for the specified protocol
     * 							(the user may have forgotten to register a specific 
     * 							factory that creates <code>SVNRepository</code>
     * 							instances for that protocol or the <i>JavaSVN</i> 
     * 							library does not support that protocol at all)
     * @see						#createRepositoryImpl(SVNURL)
     * @see 					SVNRepository
     */
    public static SVNRepository create(SVNURL url) throws SVNException {
        return create(url, null);
        
    }
    public static SVNRepository create(SVNURL url, ISVNSession options) throws SVNException {
        String urlString = url.toString();
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, urlString)) {
    			return ((SVNRepositoryFactory) myFactoriesMap.get(key)).createRepositoryImpl(url, options);
    		}
    	}
    	SVNErrorManager.error("svn: Unable to open an ra_local session to URL '" + url + "'\nsvn: No connection protocol implementation for " + url.getProtocol());
        return null;
    }

    protected abstract SVNRepository createRepositoryImpl(SVNURL url, ISVNSession session);

}
