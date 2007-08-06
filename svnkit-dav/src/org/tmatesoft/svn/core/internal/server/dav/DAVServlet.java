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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVServlet extends HttpServlet {
    private static final int SC_NOT_FOUND = 404;
    private static final int SC_SERVER_INTERNAL_ERROR = 500;

    private static final String NOT_FOUND_STATUS_LINE = "404 Not Found";

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            DAVRepositoryManager connector = new DAVRepositoryManager(getServletConfig());
            ServletDAVHandler handler = DAVHandlerFactory.createHandler(connector, request, response);
            handler.execute();
        } catch (SVNException e) {
            String errorBody;
            SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();
            if (errorCode == SVNErrorCode.RA_SVN_MALFORMED_DATA || errorCode == SVNErrorCode.FS_NOT_DIRECTORY
                    || errorCode == SVNErrorCode.FS_NOT_FOUND) {
                response.setStatus(SC_NOT_FOUND);
                errorBody = generateErrorBody(NOT_FOUND_STATUS_LINE, e.getErrorMessage().getFullMessage());
                response.setStatus(SC_NOT_FOUND);
                response.setContentType("text/html; charset=\"utf-8\"");
            } else {
                errorBody = generateStandardizedErrorBody(errorCode.getCode(), null, null, e.getErrorMessage().getFullMessage());
                response.setStatus(SC_SERVER_INTERNAL_ERROR);
                response.setContentType("text/xml; charset=\"utf-8\"");
            }
            response.getWriter().print(errorBody);
        }
        response.flushBuffer();
    }

    private String generateStandardizedErrorBody(int errorID, String namespace, String tagName, String description) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        buffer.append("<D:error xmlns:D=\"DAV:\"");
        buffer.append(" xmlns:m=\"http://apache.org/dav/xmlns\"");        
        if (namespace != null && !"".equals(namespace)) {
            buffer.append(" xmlns:C=\"");
            buffer.append(namespace);
            buffer.append(">");
            buffer.append(" <C:");
            buffer.append(tagName);
        } else if (tagName != null && !"".equals(tagName)) {
            buffer.append(">\n");
            buffer.append("<D:");
            buffer.append(tagName);
        }
        buffer.append(">\n");
        buffer.append("<m:human-readable errcode=\"");
        buffer.append(errorID);
        buffer.append(">");
        buffer.append(SVNEncodingUtil.xmlEncodeCDATA(description));
        buffer.append("</m:human-readable>\n");
        buffer.append("</D:error>");
        return buffer.toString();
    }

    private String generateErrorBody(String statusLine, String description) throws IOException {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html><head>\n<title>");
        buffer.append(statusLine);
        buffer.append("</title>\n</head><body>\n<h1>");
        buffer.append(statusLine);
        buffer.append("</h1>\n<p>");
        buffer.append(description);
        buffer.append("</p>\n</body></html>");
        return buffer.toString();
    }
}