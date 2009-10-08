/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVGetLocationsHandler extends DAVReportHandler {

    private static final String GET_LOCATIONS_REPORT = "get-locations-report";
    
    private DAVGetLocationsRequest myDAVRequest;

    public DAVGetLocationsHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getGetLocationsRequest();
    }

    private DAVGetLocationsRequest getGetLocationsRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVGetLocationsRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        String path = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), getGetLocationsRequest().getPath());
        Collection locations = getDAVResource().getRepository().getLocations(path, (Collection)null, getGetLocationsRequest().getPegRevision(), 
                getGetLocationsRequest().getRevisions());

        Map attrs = new SVNHashMap();
        writeXMLHeader(GET_LOCATIONS_REPORT);
        for (Iterator locationsIter = locations.iterator(); locationsIter.hasNext();) {
            SVNLocationEntry locationEntry = (SVNLocationEntry) locationsIter.next();
            attrs.clear();
            attrs.put(PATH_ATTR, locationEntry.getPath());
            attrs.put(REVISION_ATTR, String.valueOf(locationEntry.getRevision()));
            StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "location", SVNXMLUtil.XML_STYLE_SELF_CLOSING, 
                    attrs, null);
            write(xmlBuffer);
        }
        writeXMLFooter(GET_LOCATIONS_REPORT);
    }

}
