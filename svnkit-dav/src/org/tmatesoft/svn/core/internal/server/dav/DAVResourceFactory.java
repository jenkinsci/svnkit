/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public abstract class DAVResourceFactory {

    private static final Map ourResourceFactories = new HashMap();
   
    protected abstract DAVResource createDAVResourceImpl(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, 
            String deltaBase, long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, 
            File activitiesDB) throws SVNException;

    protected abstract DAVResource createDAVResourceChildImpl(SVNRepository repository, DAVResourceURI resourceURI, long revision, 
            boolean isSVNClient, String deltaBase, long version, String clientOptions, String baseChecksum, String resultChecksum);
    
    public static DAVResource createDAVResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws DAVException {
        DAVResourceType resourceType = resourceURI.getType();
        DAVResourceFactory factoryImpl = getFactory(resourceType);
        
        try {
            return factoryImpl.createDAVResourceImpl(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, 
                    resultChecksum, userName, activitiesDB);
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create resource.", 
                    null);
        }
    }
    
    public static DAVResource createDAVResourceChild(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, 
            String deltaBase, long version, String clientOptions, String baseChecksum, String resultChecksum) throws DAVException {
        DAVResourceType resourceType = resourceURI.getType();
        DAVResourceFactory factoryImpl = getFactory(resourceType);
        return factoryImpl.createDAVResourceChildImpl(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, 
                baseChecksum, resultChecksum);
    }
    
    private static DAVResourceFactory getFactory(DAVResourceType resourceType) throws DAVException {
        DAVResourceFactory factoryImpl = (DAVResourceFactory) ourResourceFactories.get(resourceType);
        if (factoryImpl == null) {
            throw new DAVException("DESIGN FAILURE: unknown resource type", null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, 
                    SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
        }
        return factoryImpl;
    }
    
    protected synchronized static void registerFactory(DAVResourceType resourceType, DAVResourceFactory factoryImpl) {
        ourResourceFactories.put(resourceType, factoryImpl);
    }
}
