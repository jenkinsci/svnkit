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
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.xml.sax.helpers.DefaultHandler;

public interface IHTTPConnection {

    public HTTPStatus request(String method, String path, Map header, StringBuffer body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException;

    public HTTPStatus request(String method, String path, Map header, InputStream body, int ok1, int ok2, OutputStream dst, DefaultHandler handler) throws SVNException;

    public SVNAuthentication getLastValidCredentials();

    public void clearAuthenticationCache();

    public void close();
}
