/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVServlet extends HttpServlet {

    private static final String XML_CONTENT_TYPE = "text/xml; charset=\"utf-8\"";

    private DAVConfig myDAVConfig;

    private DAVConfig getDAVConfig() {
        return myDAVConfig;
    }

    public void init() {
        FSRepositoryFactory.setup();
        try {
            myDAVConfig = new DAVConfig(getServletConfig());
        } catch (SVNException e) {
            myDAVConfig = null;
        }
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            DAVRepositoryManager repositoryManager = new DAVRepositoryManager(getDAVConfig(), request);
            ServletDAVHandler handler = DAVHandlerFactory.createHandler(repositoryManager, request, response);
            handler.execute();
        } catch (Throwable th) {
            th.printStackTrace(response.getWriter());            
            if (th instanceof SVNException) {
                SVNException e = (SVNException) th;
                SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();                
                if (errorCode == SVNErrorCode.RA_DAV_MALFORMED_DATA ||
                        errorCode == SVNErrorCode.FS_NOT_DIRECTORY ||
                        errorCode == SVNErrorCode.FS_NOT_FOUND) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getLocalizedMessage());
                } else if (errorCode == SVNErrorCode.NO_AUTH_FILE_PATH) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                } else if (errorCode == SVNErrorCode.RA_NOT_AUTHORIZED) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    String errorBody = generateStandardizedErrorBody(errorCode.getCode(), null, null, e.getLocalizedMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType(XML_CONTENT_TYPE);
                    response.getWriter().print(errorBody);                    
                }
            } else {
//                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, th.getLocalizedMessage());
            }
        }
        response.flushBuffer();
    }

    private String generateStandardizedErrorBody(int errorID, String namespace, String tagName, String description) {
        StringBuffer xmlBuffer = new StringBuffer();
        XMLUtil.addXMLHeader(xmlBuffer);
        Collection namespaces = new ArrayList();
        namespaces.add(DAVElement.DAV_NAMESPACE);
        namespaces.add(DAVElement.SVN_APACHE_PROPERTY_NAMESPACE);
        if (namespace != null) {
            namespaces.add(namespace);
        }
        XMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "error", namespaces, DAVXMLUtil.PREFIX_MAP, xmlBuffer);
        String prefix = (String) DAVXMLUtil.PREFIX_MAP.get(namespace);
        if (prefix != null) {
            prefix = DAVXMLUtil.DAV_NAMESPACE_PREFIX;
        }
        if (tagName != null && tagName.length() > 0) {
            XMLUtil.openXMLTag(prefix, tagName, XMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        }
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_APACHE_PROPERTY_PREFIX, "human-readable", XMLUtil.XML_STYLE_NORMAL, "errcode", String.valueOf(errorID), xmlBuffer);
        xmlBuffer.append(SVNEncodingUtil.xmlEncodeCDATA(description));
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_APACHE_PROPERTY_PREFIX, "human-readable", xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "error", xmlBuffer);
        return xmlBuffer.toString();
    }
}