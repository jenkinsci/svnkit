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
 * @author Alexander Kitaev
 */
public abstract class SVNRepositoryFactory {
    
    private static final Map myFactoriesMap = new HashMap();
    
    protected static void registerRepositoryFactory(String protocol, SVNRepositoryFactory factory) {
        if (protocol != null && factory != null) {
            if (!myFactoriesMap.containsKey(protocol)) {
                myFactoriesMap.put(protocol, factory);
            }
        }
    }
    
    public static boolean canCreate(SVNRepositoryLocation location) {
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, location.toString())) {
    			return myFactoriesMap.get(key) instanceof SVNRepositoryFactory;
    		}
    	}
    	return false;
    }
    
    public static SVNRepository create(SVNRepositoryLocation location) throws SVNException {
        if (!canCreate(location)) {
            throw new SVNException("no connection protocol implementation for " + location.toString());
        }
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, location.toString())) {
    			return ((SVNRepositoryFactory) myFactoriesMap.get(key)).createRepositoryImpl(location);
    		}
    	}
    	return null;
    }
    
    public abstract SVNRepository createRepositoryImpl(SVNRepositoryLocation location);

}
