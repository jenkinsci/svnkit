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

import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPropertiesProvider {

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
}
