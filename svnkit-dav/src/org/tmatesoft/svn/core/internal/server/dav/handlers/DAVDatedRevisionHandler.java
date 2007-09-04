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

import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVDatedRevisionHandler extends ReportHandler {

    private String myResponseBody = null;
    private DAVDatedRevisionRequest myDAVRequest;

    public DAVDatedRevisionHandler(DAVResource resource, Writer responseWriter) throws SVNException {
        super(resource, responseWriter);
    }

    public DAVRequest getDAVRequest() {
        return getDatedRevisionRequest();
    }

    private DAVDatedRevisionRequest getDatedRevisionRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVDatedRevisionRequest();
        }
        return myDAVRequest;
    }

    private String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String body) {
        myResponseBody = body;
    }

    public int getContentLength() throws SVNException {
        generateResponseBody();
        try {
            return getResponseBody().getBytes(UTF_8_ENCODING).length;
        } catch (UnsupportedEncodingException e) {
        }
        return -1;
    }

    public void sendResponse() throws SVNException {
        generateResponseBody();
        write(getResponseBody());
    }

    private void generateResponseBody() throws SVNException {
        if (getResponseBody() == null) {
            StringBuffer xmlBuffer = new StringBuffer();
            long revision = getDatedRevision();
            addXMLHeader(xmlBuffer);
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(revision), xmlBuffer);
            addXMLFooter(xmlBuffer);
            setResponseBody(xmlBuffer.toString());
        }
    }

    private long getDatedRevision() throws SVNException {
        return getDAVResource().getRepository().getDatedRevision(getDatedRevisionRequest().getDate());
    }
}
