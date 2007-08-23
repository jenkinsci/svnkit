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
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
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
public class DAVGetLocationsHandler implements IDAVReportHandler, ISVNLocationEntryHandler {

    private static final DAVElement PEG_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "peg-revision");
    private static final DAVElement LOCATION_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "location-revision");

    private StringBuffer myBody;

    private StringBuffer getBody() {
        if (myBody == null) {
            myBody = new StringBuffer();
        }
        return myBody;
    }

    public void writeTo(Writer out, DAVResource resource, IDAVRequest davRequest) throws SVNException {
        generateResponseBody(resource, davRequest);

        try {
            out.write(getBody().toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generateResponseBody(DAVResource resource, IDAVRequest davRequest) throws SVNException {
        String path = null;
        long pegRevision = DAVResource.INVALID_REVISION;
        long[] revisions = null;

        for (Iterator iterator = davRequest.entryIterator(); iterator.hasNext();) {
            IDAVRequest.Entry entry = (IDAVRequest.Entry) iterator.next();
            DAVElement element = entry.getElement();
            if (element == PATH) {
                path = entry.getFirstValue();
            } else if (element == PEG_REVISION) {
                String pegRevisionString = entry.getFirstValue();
                pegRevision = Long.parseLong(pegRevisionString);
            } else if (element == LOCATION_REVISION) {
                revisions = new long[entry.getValues().size()];
                int i = 0;
                for (Iterator revisionsIterator = entry.getValues().iterator(); revisionsIterator.hasNext(); i++) {
                    long currentRevision = Long.parseLong((String) revisionsIterator.next());
                    revisions[i] = currentRevision;
                }
            }
        }

        //TODO: check pegRevision
        if (path == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Not all parameters passed."));
        }

        XMLUtil.addXMLHeader(getBody());
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, GET_LOCATIONS_REPORT.getName(), davRequest.getElements(), getBody());

        resource.getRepository().getLocations(path, pegRevision, revisions, this);

        XMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, GET_LOCATIONS_REPORT.getName(), getBody());
    }


    public void handleLocationEntry(SVNLocationEntry locationEntry) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(PATH.getName(), locationEntry.getPath());
        attrs.put("rev", String.valueOf(locationEntry.getRevision()));
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "location", XMLUtil.XML_STYLE_SELF_CLOSING, attrs, getBody());
    }
}
