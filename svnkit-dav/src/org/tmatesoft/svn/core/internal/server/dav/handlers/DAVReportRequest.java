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
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReportRequest extends DAVRequest {

    public static Set REPORT_ELEMENTS = new HashSet();

    public static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    public static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    public static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    public static final DAVElement GET_LOCATIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    public static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    public static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    public static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    public static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    protected static final DAVElement PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "path");
    protected static final DAVElement REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "revision");
    protected static final DAVElement START_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "start-revision");
    protected static final DAVElement END_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "end-revision");

    private DAVReportRequest myCustomRequest;

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

    public DAVReportRequest() {
        super();
    }

    protected DAVReportRequest(DAVElement rootElement, Map properties) {
        super(rootElement, properties);
    }

    public DAVReportRequest getReportRequest() {
        return myCustomRequest;
    }

    protected void initialize() throws SVNException {
        if (isDatedRevisionsRequest()) {
            myCustomRequest = new DAVDatedRevisionRequest(getProperties());
        } else if (isLogRequest()) {
            myCustomRequest = new DAVLogRequest(getProperties());
        } else if (isGetLocationsRequest()) {
            myCustomRequest = new DAVGetLocationsRequest(getProperties());
        } else if (isFileRevisionsRequest()) {
            myCustomRequest = new DAVFileRevisionsRequest(getProperties());
        } else if (isMergeInfoRequest()) {
            myCustomRequest = new DAVMergeInfoRequest(getProperties());
        } else if (isGetLocksRequest()) {
            myCustomRequest = new DAVGetLocksRequest(getProperties());
        } else if (isReplayRequest()) {
            myCustomRequest = new DAVReplayRequest(getProperties());
        } else if (isUpdateRequest()) {
            myCustomRequest = new DAVUpdateRequest(getProperties(), getRootElementAttributes());
        } else {
            //TODO: Here should be something like NOT_SUPPORTED
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA));
        }
    }

    public boolean isDatedRevisionsRequest() {
        return getRootElement() == DATED_REVISIONS_REPORT;
    }

    public boolean isLogRequest() {
        return getRootElement() == LOG_REPORT;
    }

    public boolean isGetLocationsRequest() {
        return getRootElement() == GET_LOCATIONS_REPORT;
    }

    public boolean isFileRevisionsRequest() {
        return getRootElement() == FILE_REVISIONS_REPORT;
    }

    public boolean isMergeInfoRequest() {
        return getRootElement() == MERGEINFO_REPORT;
    }

    public boolean isGetLocksRequest() {
        return getRootElement() == GET_LOCKS_REPORT;
    }

    public boolean isReplayRequest() {
        return getRootElement() == REPLAY_REPORT;
    }

    public boolean isUpdateRequest() {
        return getRootElement() == UPDATE_REPORT;
    }
}
