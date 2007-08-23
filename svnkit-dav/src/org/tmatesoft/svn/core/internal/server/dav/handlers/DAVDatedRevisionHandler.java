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

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVDatedRevisionHandler implements IDAVReportHandler {

    DAVResource myDAVResource;
    IDAVRequest myDAVRequest;

    String myResponseBody;

    public DAVDatedRevisionHandler(DAVResource resource, IDAVRequest davRequest) throws SVNException {
        myDAVResource = resource;
        myDAVRequest = davRequest;
        generateResponseBody();
    }

    private DAVResource getDAVResource() {
        return myDAVResource;
    }

    private IDAVRequest getDAVRequest() {
        return myDAVRequest;
    }

    private String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String body) {
        myResponseBody = body;
    }

    public int getContentLength() {
        return getResponseBody() == null ? -1 : getResponseBody().getBytes().length;
    }

    public void writeTo(Writer out) throws SVNException {
        try {
            out.write(getResponseBody());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generateResponseBody() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        long revision = getDatedRevision();
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, DATED_REVISIONS_REPORT.getName(), getDAVRequest().getElements(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(revision), xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, DATED_REVISIONS_REPORT.getName(), xmlBuffer);
        setResponseBody(xmlBuffer.toString());
    }

    private long getDatedRevision() throws SVNException {
        String dateString = getDAVRequest().getFirstValue(DAVElement.CREATION_DATE);
        Date date = SVNTimeUtil.parseDate(dateString);
        return getDAVResource().getRepository().getDatedRevision(date);
    }

}
