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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.internal.server.dav.DAVErrorCode;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPropertiesProvider {
    private boolean myIsDeferred;
    private boolean myIsOperative;
    private DAVResource myResource;
    private DAVException myError;
    
    private DAVPropertiesProvider() {
    }
    
    public static DAVPropertiesProvider createPropertiesProvider(DAVResource resource, List namespaceXLate) throws DAVException {
        DAVResourceURI resourceURI = resource.getResourceURI();
        if (resourceURI.getURI() == null) {
            throw new DAVException("INTERNAL DESIGN ERROR: resource must define its URI.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
        }
        
        DAVPropertiesProvider provider = new DAVPropertiesProvider();
        return provider;
    }

    public void open(DAVResource resource, boolean readOnly) throws DAVException {
        myIsDeferred = false;
        try {
            doOpen(resource, readOnly);
        } catch (DAVException dave) {
            throw new DAVException("Could not open the property database.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, DAVErrorCode.PROP_OPENING);
        }
    }

    public boolean isOperative() {
        return myIsOperative;
    }

    public boolean isDeferred() {
        return myIsDeferred;
    }

    public void setDeferred(boolean isDeferred) {
        myIsDeferred = isDeferred;
    }

    public DAVException getError() {
        return myError;
    }
    
    public void setError(DAVException error) {
        myError = error;
    }

    private void doOpen(DAVResource resource, boolean readOnly) throws DAVException {
        DAVResourceType resType = resource.getType();
        if (resType == DAVResourceType.HISTORY || resType == DAVResourceType.ACTIVITY || resType == DAVResourceType.PRIVATE) {
            myIsOperative = false;
            return;
        }
        
        if (!readOnly && resType != DAVResourceType.WORKING) {
            if (!(resource.isBaseLined() && resType == DAVResourceType.VERSION)) {
                throw new DAVException("Properties may only be changed on working resources.", HttpServletResponse.SC_CONFLICT, 0);
            }
        }
        
        myResource = resource;
    }
}
