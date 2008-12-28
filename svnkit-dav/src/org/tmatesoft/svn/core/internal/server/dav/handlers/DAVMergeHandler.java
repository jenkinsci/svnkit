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

import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVMergeHandler extends ServletDAVHandler {
    private DAVMergeRequest myDAVRequest;

    protected DAVMergeHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    public void execute() throws SVNException {
        long readLength = readInput(false);
        if (readLength <= 0) {
            getMergeRequest().invalidXMLRoot();
        }

        DAVMergeRequest requestXMLObject = getMergeRequest();  
        DAVElementProperty rootElement = requestXMLObject.getRoot();
        DAVElementProperty sourceElement = rootElement.getChild(DAVElement.SOURCE);
        if (sourceElement == null) {
            throw new DAVException("The DAV:merge element must contain a DAV:source element.", HttpServletResponse.SC_BAD_REQUEST, 0);
        }
        
        DAVElementProperty hrefElement = sourceElement.getChild(DAVElement.HREF);
        if (hrefElement == null) {
            throw new DAVException("The DAV:source element must contain a DAV:href element.", HttpServletResponse.SC_BAD_REQUEST, 0);
        }
        
        String source = hrefElement.getFirstValue(false);
        URI uri = null; 
        try {
            uri = DAVServletUtil.lookUpURI(source, getRequest(), false);
        } catch (DAVException dave) {
            if (dave.getResponseCode() == HttpServletResponse.SC_BAD_REQUEST) {
                throw dave;
            }
            response(dave.getMessage(), DAVServlet.getStatusLine(dave.getResponseCode()), dave.getResponseCode());
        }

    }

    protected DAVRequest getDAVRequest() {
        return getMergeRequest();
    }

    private DAVMergeRequest getMergeRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVMergeRequest();
        }
        return myDAVRequest;
    }

}
