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

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceState;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPutHandler extends ServletDAVHandler {

    public DAVPutHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    public void execute() throws SVNException {
        DAVResource resource = getRequestedDAVResource(false, false);
        
        if (resource.getType() != DAVResourceType.REGULAR && resource.getType() != DAVResourceType.WORKING) {
            String body = "Cannot create resource " + SVNEncodingUtil.xmlEncodeCDATA(getURI()) + " with PUT.";
            response(body, DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), HttpServletResponse.SC_CONFLICT);
            return;
        }
        
        if (resource.isCollection()) {
            response("Cannot PUT to a collection.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), HttpServletResponse.SC_CONFLICT);
            return;
        }
        
        DAVResourceState resourceState = getResourceState(resource);
        
    }

    protected DAVRequest getDAVRequest() {
        return null;
    }
    
}
