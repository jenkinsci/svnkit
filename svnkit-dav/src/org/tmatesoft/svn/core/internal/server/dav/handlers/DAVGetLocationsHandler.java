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
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetLocationsHandler extends ReportHandler implements ISVNLocationEntryHandler {

    public DAVGetLocationsHandler(DAVResource resource, DAVGetLocationsRequest reportRequest, Writer responseWriter) {
        super(resource, reportRequest, responseWriter);
    }

    private DAVGetLocationsRequest getDAVRequest() {
        return (DAVGetLocationsRequest) myDAVRequest;
    }

    public int getContentLength() {
        return -1;
    }

    public void sendResponse() throws SVNException {
        writeXMLHeader();

        getDAVResource().getRepository().getLocations(getDAVRequest().getPath(), getDAVRequest().getPegRevision(), getDAVRequest().getRevisions(), this);

        writeXMLFooter();
    }

    public void handleLocationEntry(SVNLocationEntry locationEntry) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("path", locationEntry.getPath());
        attrs.put("rev", String.valueOf(locationEntry.getRevision()));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "location", XMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }
}
