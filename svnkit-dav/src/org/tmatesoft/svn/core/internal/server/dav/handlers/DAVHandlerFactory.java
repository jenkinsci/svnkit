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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVHandlerFactory {

    public static final String METHOD_PROPFIND = "PROPFIND";
    public static final String METHOD_OPTIONS = "OPTIONS";
    public static final String METHOD_REPORT = "REPORT";
    public static final String METHOD_GET = "GET";
    public static final String METHOD_HEAD = "HEAD";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_DELETE = "DELETE";
    public static final String METHOD_TRACE = "TRACE";
    public static final String METHOD_PROPPATCH = "PROPPATCH";
    public static final String METHOD_COPY = "COPY";
    public static final String METHOD_MOVE = "MOVE";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_LOCK = "LOCK";
    public static final String METHOD_UNLOCK = "UNLOCK";
    public static final String METHOD_MKCOL = "MKCOL";
    public static final String METHOD_VERSION_CONTROL = "VERSION-CONTROL";
    public static final String METHOD_MKWORKSPACE = "MKWORKSPACE";
    public static final String METHOD_MKACTIVITY = "MKACTIVITY";
    public static final String METHOD_CHECKIN = "CHECKIN";
    public static final String METHOD_CHECKOUT = "CHECKOUT";

    public static ServletDAVHandler createHandler(DAVRepositoryManager manager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        if (METHOD_PROPFIND.equals(request.getMethod())) {
            return new DAVPropfindHanlder(manager, request, response);
        }
        if (METHOD_OPTIONS.equals(request.getMethod())) {
            return new DAVOptionsHandler(manager, request, response);
        }
        if (METHOD_GET.equals(request.getMethod())) {
            return new DAVGetHandler(manager, request, response);
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unknown request method ''{0}''", request.getMethod()));
        return null;
    }

}