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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReportHandler extends ServletDAVHandler implements IDAVReportHandler {

    public static final Set REPORT_ELEMENTS = new HashSet();

    static {
        REPORT_ELEMENTS.add(UPDATE_REPORT);
        REPORT_ELEMENTS.add(LOG_REPORT);
        REPORT_ELEMENTS.add(DATED_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCATIONS_REPORT);
        REPORT_ELEMENTS.add(FILE_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCKS_REPORT);
        REPORT_ELEMENTS.add(REPLAY_REPORT);
        REPORT_ELEMENTS.add(MERGEINFO_REPORT);
    }

    private DAVElement myRootElement;
    private Map myDAVProperties;
    private Map myDAVAttributes;

    protected DAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    private Map getDAVProperties() {
        if (myDAVProperties == null) {
            myDAVProperties = new HashMap();
        }
        return myDAVProperties;
    }

    private Map getDAVAttributes() {
        if (myDAVAttributes == null) {
            myDAVAttributes = new HashMap();
        }
        return myDAVAttributes;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            myRootElement = element;
            //TODO: Think of something better.
            //Now we put root not to forget about its namespace
            getDAVProperties().put(element, null);
        } else if (REPORT_ELEMENTS.contains(parent)) {
            getDAVAttributes().put(element, attrs);
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (REPORT_ELEMENTS.contains(parent)) {
            Collection tagsCData = (Collection) getDAVProperties().get(element);
            if (tagsCData == null) {
                Collection currentCData = new ArrayList();
                currentCData.add(cdata.toString());
                getDAVProperties().put(element, currentCData);
            } else {
                tagsCData.add(cdata.toString());
            }
        }
    }

    public void execute() throws SVNException {
        DAVResource resource = createDAVResource(false, false);

        readInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();
        generateResponseBody(resource, body);

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        try {
            getResponseWriter().write(body.toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    public StringBuffer generateResponseBody(DAVResource resource, StringBuffer xmlBuffer) throws SVNException {
        IDAVReportHandler reportHandler = getHandler();
        return reportHandler.generateResponseBody(resource, xmlBuffer);
    }

    private IDAVReportHandler getHandler() throws SVNException {
        if (myRootElement == DATED_REVISIONS_REPORT) {
            return new DAVDatedRevisionHandler(getDAVProperties());
        } else if (myRootElement == LOG_REPORT) {
            return new DAVLogHandler(getDAVProperties());
        } else if (myRootElement == GET_LOCATIONS_REPORT){
            return new DAVGetLocationsReport(getDAVProperties());
        }
        //TODO: Here should be something like NOT_SUPPORTED
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA));
        return null;
    }
}
