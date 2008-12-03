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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVCopyMoveHandler extends ServletDAVHandler {

    private boolean myIsMove;
    
    protected DAVCopyMoveHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response, boolean isMove) {
        super(connector, request, response);
        myIsMove = isMove;
    }

    public void execute() throws SVNException {
        DAVResource resource = getRequestedDAVResource(!myIsMove, false);
        if (!resource.exists()) {
            setResponseStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        if (resource.getType() != DAVResourceType.REGULAR) {
            String body = "Cannot COPY/MOVE resource " + SVNEncodingUtil.xmlEncodeCDATA(getURI()) + ".";
            response(body, DAVServlet.getStatusLine(HttpServletResponse.SC_METHOD_NOT_ALLOWED), HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        String destination = getRequestHeader(HTTPHeader.DESTINATION_HEADER);
        if (destination == null) {
            String netScapeHost = getRequestHeader(HTTPHeader.HOST_HEADER);
            String netScapeNewURI = getRequestHeader(HTTPHeader.NEW_URI_HEADER);
            if (netScapeHost != null && netScapeNewURI != null) {
                String path = SVNPathUtil.append(netScapeHost, netScapeNewURI);
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                destination = "http://" + path;
            }
        }
        
        if (destination == null) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK, "The request is missing a Destination header.");
            setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        
        
    }

    protected DAVRequest getDAVRequest() {
        return null;
    }

}
