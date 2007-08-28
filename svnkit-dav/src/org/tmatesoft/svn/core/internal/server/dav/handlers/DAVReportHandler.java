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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReportHandler extends ServletDAVHandler {

    private DAVReportRequest myDAVRequest;

    protected DAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    private DAVReportRequest getDAVRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVReportRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        getDAVRequest().readInput(getRequestInputStream());

        DAVResource resource = createDAVResource(false, false);

        ReportHandler reportHandler = getReportHandler(resource);

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        if (reportHandler.getContentLength() != -1) {
            setResponseContentLength(reportHandler.getContentLength());
        }

        reportHandler.sendResponse();
        //TODO: In some cases native svn starts blame command and clean all out headers        
    }

    private ReportHandler getReportHandler(DAVResource resource) throws SVNException {
        if (getDAVRequest().isDatedRevisionsRequest()) {
            return new DAVDatedRevisionHandler(resource, (DAVDatedRevisionRequest) getDAVRequest().getReportRequest(), getResponseWriter());
        } else if (getDAVRequest().isFileRevisionsRequest()) {
            return new DAVFileRevisionsHandler(resource, (DAVFileRevisionsRequest) getDAVRequest().getReportRequest(), getResponseWriter(), getSVNDiffVersion());
        } else if (getDAVRequest().isGetLocationsRequest()) {
            return new DAVGetLocationsHandler(resource, (DAVGetLocationsRequest) getDAVRequest().getReportRequest(), getResponseWriter());
        } else if (getDAVRequest().isLogRequest()) {
            return new DAVLogHandler(resource, (DAVLogRequest) getDAVRequest().getReportRequest(), getResponseWriter());
        } else if (getDAVRequest().isMergeInfoRequest()) {
            return new DAVMergeInfoHandler(resource, (DAVMergeInfoRequest) getDAVRequest().getReportRequest(), getResponseWriter());
        } else if (getDAVRequest().isGetLocksRequest()) {
            return new DAVGetLocksHandler(resource, (DAVGetLocksRequest) getDAVRequest().getReportRequest(), getResponseWriter());
        }
        return null;
    }
}
