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
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetLocksHandler extends ReportHandler {

    String myResponseBody;

    protected DAVGetLocksHandler(DAVResource resource, DAVGetLocksRequest reportRequest, Writer responseWriter) throws SVNException {
        super(resource, reportRequest, responseWriter);
        generateResponseBody();
    }

    public String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String responseBody) {
        myResponseBody = responseBody;
    }

    public int getContentLength() {
        try {
            return getResponseBody().getBytes(UTF_8_ENCODING).length;
        } catch (UnsupportedEncodingException e) {
        }
        return -1;
    }

    public void sendResponse() throws SVNException {
        write(getResponseBody());
    }

    private void generateResponseBody() throws SVNException {
        SVNLock[] locks = getDAVResource().getLocks();

        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);
        for (int i = 0; i < locks.length; i++) {
            addLock(locks[i], xmlBuffer);
        }
        addXMLFooter(xmlBuffer);
        setResponseBody(xmlBuffer.toString());
    }

    private void addLock(SVNLock lock, StringBuffer xmlBuffer) {
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "lock", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "path", lock.getPath(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "token", lock.getID(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "creationdate", SVNTimeUtil.formatDate(lock.getCreationDate()), xmlBuffer);
        if (lock.getExpirationDate() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "expirationdate", SVNTimeUtil.formatDate(lock.getExpirationDate()), xmlBuffer);
        }
        if (lock.getOwner() != null) {
            if (SVNEncodingUtil.isXMLSafe(lock.getOwner())) {
                XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "owner", lock.getOwner(), xmlBuffer);
            } else {
                String ownerEncoded = SVNBase64.byteArrayToBase64(lock.getOwner().getBytes());
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "owner", XMLUtil.XML_STYLE_PROTECT_PCDATA, "encoding", "base64", xmlBuffer);
                xmlBuffer.append(ownerEncoded);
                XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "owner", xmlBuffer);
            }
        }
        if (lock.getComment() != null) {
            if (SVNEncodingUtil.isXMLSafe(lock.getComment())) {
                XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", lock.getComment(), xmlBuffer);
            } else {
                String commentEncoded = SVNBase64.byteArrayToBase64(lock.getComment().getBytes());
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", XMLUtil.XML_STYLE_PROTECT_PCDATA, "encoding", "base64", xmlBuffer);
                xmlBuffer.append(commentEncoded);
                XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", xmlBuffer);
            }
        }
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "lock", xmlBuffer);
    }
}
