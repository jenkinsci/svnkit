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

/**
 * <code>SVNRepositoryFactory</code> is an abstract class that is responsible
 * for creating an appropriate <code>SVNRepository</code>-extansion that 
 * will be used to interact with a Subversion repository.
 * 
 * <p>
 * Depending on what protocol a user exactly would like to use
 * to access the repository he should first of all register an 
 * appropriate extension of this factory. So, if the user is going to
 * work with the repository via the custom <i>svn</i>-protocol (or 
 * <i>svn+ssh</i>) he initially calls:
 * <blockquote><pre>
 * 		import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * 		
 * 		<i>//do it once in your application prior to using the library</i>
 * 		<i>//via the SVN-protocol (over svn and svn+ssh)</i>
 * 		SVNRepositoryFactoryImpl.setup();
 * </pre></blockquote>
 * That <code>setup()</code> method registers a 
 * <code>SVNRepositoryFactoryImpl</code> instance in the factory (calling
 * {@link #registerRepositoryFactory(String, SVNRepositoryFactory)}). From 
 * this point the <code>SVNRepositoryFactory</code> knows how to create
 * <code>SVNRepository</code> instancies specific for the <i>svn</i>-protocol.
 * And further the user can create an <code>SVNRepository</code> instance:
 * <blockquote><pre>
 * 		<i>//the user gets an <code>SVNRepository</code> not caring</i>
 * 		<i>//how it's implemented for the svn-protocol</i>
 * 		SVNRepository repository = SVNRepositoryFactory.create(location);
 * </pre></blockquote>
 * All that was previously said about the <i>svn</i>-protocol is similar for
 * the <i>WebDAV</i>-protocol:
 * <blockquote><pre>
 * 		import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
 * 		<i>//do it once in your application prior to using the library</i>
 * 		<i>//via the DAV-protocol (over http and https)</i>
 * 		DAVRepositoryFactory.setup();
 * </pre></blockquote>
 * 
 * <p>
 * <b>NOTE:</b> unfortunately, at present the <i>JavaSVN</i> library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		SVNRepository
 * @see		org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl
 * @see		org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
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
     * Determines if the factory can create a protocol dependent 
     * <code>SVNRepository</code> instance.
     * 
     * <p>
     * The routine mathches the <code>URL</code> (incapsulated in <code>location</code>)
     * with each factory registered and if there's such factory that can create an
     * instance of <code>SVNRepository</code> for the protocol the routine returns 
     * <code>true</code>.
     * 
     * @param location		a <code>URL</code> (to connect to a repository) 
     * 						as an <code>SVNRepositoryLocation</code> object
     * @return				<code>true</code> if there's a factory that produces
     * 						implementations of <code>SVNRepository</code> for
     * 						the specified protocol; <code>false</code> otherwise.
     */
    public static boolean canCreate(SVNRepositoryLocation location) {
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, location.toString())) {
    			return myFactoriesMap.get(key) instanceof SVNRepositoryFactory;
    		}
    	}
    	return false;
    }

    /**
     * Creates an <code>SVNRepository</code> according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is a part of the <code>URL</code> (used to connect to the 
     * repository) incapsulated in the <code>location<code> parameter.
     * 
     * <p>
     * In fact, this method doesn't create an <code>SVNRepository</code> instance but
     * calls the {@link #createRepositoryImpl(SVNRepositoryLocation)} of the registered
     * factory (protocol specific extension of this class) that essentially creates the
     * instance; then this routine simply returns it to the caller.
     *  
     * @param  location			a <code>URL</code> (to connect to a repository) 
     * 							as an <code>SVNRepositoryLocation</code> object
     * @return					a new instance of <code>SVNRepository</code> to interact
     * 							with the repository
     * @throws SVNException		if there's no implementation for the specified protocol
     * 							(the user may have forgotten to register a specific 
     * 							factory that creates <code>SVNRepository</code>
     * 							instances for that protocol or the <i>JavaSVN</i> 
     * 							library does not support that protocol at all)
     * @see						#createRepositoryImpl(SVNRepositoryLocation)
     * @see 					SVNRepository
     * @see						SVNRepositoryLocation 
     */
    public static SVNRepository create(SVNRepositoryLocation location) throws SVNException {
        if (!canCreate(location)) {
            throw new SVNException("svn: Unable to open an ra_local session to URL '" + location + "'\nsvn: No connection protocol implementation for " + location.toString());
        }
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, location.toString())) {
    			return ((SVNRepositoryFactory) myFactoriesMap.get(key)).createRepositoryImpl(location);
    		}
    	}
    	return null;
    }
    
    /**
     * Creates an implementation of <code>SVNRepository</code> (specific for the used
     * protocol).
     * 
     * <p>
     * When a user calls the {@link #create(SVNRepositoryLocation) create()} method
     * the <code>SVNRepositoryFactory</code> determines if it has got a factory 
     * (extending this factory class) that can create an appropriate 
     * <code>SVNRepository</code> instance and, if it does, calls its
     * <code>createRepositoryImpl()</code> method that actually creates the instance.
     *    
     * @param location		a <code>URL</code> (to connect to a repository) 
     * 						as an <code>SVNRepositoryLocation</code> object
     * @return				a new instance of <code>SVNRepository</code> to interact
     * 						with the repository
     * @see					#create(SVNRepositoryLocation)
     */
    public abstract SVNRepository createRepositoryImpl(SVNRepositoryLocation location);

}
