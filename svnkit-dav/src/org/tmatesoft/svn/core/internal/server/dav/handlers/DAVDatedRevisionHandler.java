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

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVDatedRevisionHandler implements IDAVReportHandler {

    private Map myProperties;

    public DAVDatedRevisionHandler(Map properties) {
        myProperties = properties;
    }


    private Map getProperties() {
        return myProperties;
    }

    public StringBuffer generateResponseBody(DAVResource resource, StringBuffer xmlBuffer) throws SVNException {
        xmlBuffer = xmlBuffer == null ? new StringBuffer() : xmlBuffer;
        long revision = getDatedRevision(resource);
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, DATED_REVISIONS_REPORT.getName(), myProperties.keySet(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(revision), xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, DATED_REVISIONS_REPORT.getName(), xmlBuffer);
        return xmlBuffer;
    }

    private long getDatedRevision(DAVResource resource) throws SVNException {
        Collection cdata = (Collection) getProperties().get(DAVElement.CREATION_DATE);
        String dateString = (String) cdata.iterator().next();
        Date date = SVNTimeUtil.parseDate(dateString);
        return resource.getRepository().getDatedRevision(date);
    }

}
