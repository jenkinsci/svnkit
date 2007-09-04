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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReportHandler extends ServletDAVHandler {

    public static Set REPORT_ELEMENTS = new HashSet();    

    public static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    public static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    public static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    public static final DAVElement GET_LOCATIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    public static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    public static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    public static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    public static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    private ReportHandler myReportHandler;
    private DAVResource myDAVResource;
         
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

    protected DAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getReportHandler().getDAVRequest();
    }

    private ReportHandler getReportHandler() {
        return myReportHandler;
    }

    private void setReportHandler(ReportHandler reportHandler) {
        myReportHandler = reportHandler;
    }

    private DAVResource getDAVResource() {
        return myDAVResource;
    }

    private void setDAVResource(DAVResource DAVResource) {
        myDAVResource = DAVResource;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            initReportHandler(element);
        }
        getReportHandler().startElement(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getReportHandler().endElement(parent, element, cdata);
    }

    public void execute() throws SVNException {
        setDAVResource(createDAVResource(false, false));

        readInput();

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        if (getReportHandler().getContentLength() > 0) {
            setResponseContentLength(getReportHandler().getContentLength());
        }

        getReportHandler().sendResponse();
        //TODO: In some cases native svn starts blame command and clean all out headers        
    }

    private void initReportHandler(DAVElement rootElement) throws SVNException {
        if (rootElement == DATED_REVISIONS_REPORT) {
            setReportHandler(new DAVDatedRevisionHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == FILE_REVISIONS_REPORT) {
            setReportHandler(new DAVFileRevisionsHandler(getDAVResource(), getResponseWriter(), getSVNDiffVersion()));
        } else if (rootElement == GET_LOCATIONS_REPORT) {
            setReportHandler(new DAVGetLocationsHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == LOG_REPORT) {
            setReportHandler(new DAVLogHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == MERGEINFO_REPORT) {
            setReportHandler(new DAVMergeInfoHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == GET_LOCKS_REPORT) {
            setReportHandler(new DAVGetLocksHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == REPLAY_REPORT) {
            setReportHandler(new DAVReplayHandler(getDAVResource(), getResponseWriter()));
        } else if (rootElement == UPDATE_REPORT) {
            setReportHandler(new DAVUpdateHandler(getDAVResource(), getResponseWriter()));
        }
    }
}
