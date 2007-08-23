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

import java.util.HashSet;
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
public class DAVReportHandler extends ServletDAVHandler {

    public static final Set REPORT_ELEMENTS = new HashSet();

    static {
        REPORT_ELEMENTS.add(IDAVReportHandler.UPDATE_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.LOG_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.DATED_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.GET_LOCATIONS_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.FILE_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.GET_LOCKS_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.REPLAY_REPORT);
        REPORT_ELEMENTS.add(IDAVReportHandler.MERGEINFO_REPORT);
    }

    private DAVPropertyValuesRequest myDAVRequest;

    protected DAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    private IDAVRequest getDAVRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropertyValuesRequest();
        }
        return myDAVRequest;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            getDAVRequest().setRootElement(element);
            //TODO: Think of something better.
            //Now we put root not to forget about its namespace
            getDAVRequest().add(element);
        } else if (REPORT_ELEMENTS.contains(parent)) {
            getDAVRequest().put(element, attrs);
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (REPORT_ELEMENTS.contains(parent)) {
            getDAVRequest().put(element, cdata.toString());
        }
    }

    public void execute() throws SVNException {
        DAVResource resource = createDAVResource(false, false);

        readInput(getRequestInputStream());

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        IDAVReportHandler reportHandler = getReportHandler(resource);

        if (reportHandler.getContentLength() != -1) {
            setResponseContentLength(reportHandler.getContentLength());
        }

        reportHandler.writeTo(getResponseWriter());
        //TODO: In some cases native svn starts blame command and clean all out headers        
    }

    private IDAVReportHandler getReportHandler(DAVResource resource) throws SVNException {
        if (getDAVRequest().getRootElement() == IDAVReportHandler.DATED_REVISIONS_REPORT) {
            return new DAVDatedRevisionHandler(resource, getDAVRequest());
        } else if (getDAVRequest().getRootElement() == IDAVReportHandler.LOG_REPORT) {
            return new DAVLogHandler(resource, getDAVRequest());
        } else if (getDAVRequest().getRootElement() == IDAVReportHandler.GET_LOCATIONS_REPORT) {
            return new DAVGetLocationsHandler(resource, getDAVRequest());
        } else if (getDAVRequest().getRootElement() == IDAVReportHandler.FILE_REVISIONS_REPORT) {
            return new DAVFileRevisionsHandler(resource, getDAVRequest(), getSVNDiffVersion());
        }
        //TODO: Here should be something like NOT_SUPPORTED
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA));
        return null;
    }
}
