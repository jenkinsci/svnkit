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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public interface IDAVReportHandler {

    static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    static final DAVElement GET_LOCATIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    static final DAVElement PATH = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "path");

    public StringBuffer generateResponseBody(DAVResource resource, StringBuffer xmlBuffer) throws SVNException;

}
