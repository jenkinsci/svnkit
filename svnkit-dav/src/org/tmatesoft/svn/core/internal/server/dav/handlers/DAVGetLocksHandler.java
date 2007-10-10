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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetLocksHandler extends DAVReportHandler {

    private DAVGetLocksRequest myDAVRequest;

    protected DAVGetLocksHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }


    protected DAVRequest getDAVRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVGetLocksRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        String responseBody = generateResponseBody();

        try {
            setResponseContentLength(responseBody.getBytes(UTF8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
        }

        write(responseBody);
    }

    private String generateResponseBody() throws SVNException {
        if (getDAVResource().getResourceURI().getPath() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "get-locks-report run on resource which doesn't represent a path within a repository."));
        }

        SVNLock[] locks = getDAVResource().getLocks();

        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);
        for (int i = 0; i < locks.length; i++) {
            addLock(locks[i], xmlBuffer);
        }
        addXMLFooter(xmlBuffer);
        return xmlBuffer.toString();
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
                String ownerEncoded = null;
                try {
                    ownerEncoded = SVNBase64.byteArrayToBase64(lock.getOwner().getBytes(UTF8_ENCODING));
                } catch (UnsupportedEncodingException e) {
                    ownerEncoded = SVNBase64.byteArrayToBase64(lock.getOwner().getBytes());
                }
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "owner", XMLUtil.XML_STYLE_PROTECT_PCDATA, ENCODING_ATTR, BASE64_ENCODING, xmlBuffer);
                xmlBuffer.append(ownerEncoded);
                XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "owner", xmlBuffer);
            }
        }
        if (lock.getComment() != null) {
            if (SVNEncodingUtil.isXMLSafe(lock.getComment())) {
                XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", lock.getComment(), xmlBuffer);
            } else {
                String commentEncoded = null;
                try {
                    commentEncoded = SVNBase64.byteArrayToBase64(lock.getComment().getBytes(UTF8_ENCODING));
                } catch (UnsupportedEncodingException e) {
                    commentEncoded = SVNBase64.byteArrayToBase64(lock.getComment().getBytes());
                }
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", XMLUtil.XML_STYLE_PROTECT_PCDATA, ENCODING_ATTR, BASE64_ENCODING, xmlBuffer);
                xmlBuffer.append(commentEncoded);
                XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "comment", xmlBuffer);
            }
        }
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "lock", xmlBuffer);
    }
}
