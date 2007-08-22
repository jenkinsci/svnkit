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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetLocationsHandler implements IDAVReportHandler, ISVNLocationEntryHandler {

    private static final DAVElement PEG_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "peg-revision");
    private static final DAVElement LOCATION_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "location-revision");

    private Map myProperties;
    private StringBuffer myBody;

    public DAVGetLocationsHandler(Map properties) {
        myProperties = properties;
    }

    private Map getProperties() {
        return myProperties;
    }


    private StringBuffer getBody() {
        if (myBody == null) {
            myBody = new StringBuffer();
        }
        return myBody;
    }

    private void setBody(StringBuffer body) {
        myBody = body;
    }

    public StringBuffer generateResponseBody(DAVResource resource, StringBuffer xmlBuffer) throws SVNException {
        String path = null;
        long pegRevision = DAVResource.INVALID_REVISION;
        long[] revisions = null;

        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            Collection values = (Collection) entry.getValue();
            if (element == PATH) {
                path = (String) values.iterator().next();
            } else if (element == PEG_REVISION) {
                String pegRevisionString = (String) values.iterator().next();
                pegRevision = Long.parseLong(pegRevisionString);
            } else if (element == LOCATION_REVISION) {
                revisions = new long[values.size()];
                int i = 0;
                for (Iterator revisionsIterator = values.iterator(); revisionsIterator.hasNext(); i++) {
                    long currentRevision = Long.parseLong((String) revisionsIterator.next());
                    revisions[i] = currentRevision;
                }
            }
        }

        //TODO: check pegRevision
        if (path == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Not all parameters passed."));
        }

        setBody(xmlBuffer);
        DAVXMLUtil.addXMLHeader(getBody());
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, GET_LOCATIONS_REPORT.getName(), getProperties().keySet(), getBody());

        resource.getRepository().getLocations(path, pegRevision, revisions, this);

        DAVXMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, GET_LOCATIONS_REPORT.getName(), getBody());
        return getBody();
    }


    public void handleLocationEntry(SVNLocationEntry locationEntry) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(PATH.getName(), locationEntry.getPath());
        attrs.put("rev", String.valueOf(locationEntry.getRevision()));
        DAVXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "location", DAVXMLUtil.XML_STYLE_SELF_CLOSING, attrs, getBody());
    }
}
