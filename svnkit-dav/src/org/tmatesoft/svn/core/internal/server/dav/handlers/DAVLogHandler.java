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

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVLogHandler implements IDAVReportHandler, ISVNLogEntryHandler {

    private static final DAVElement START_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "start-revision");
    private static final DAVElement END_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "end-revision");
    private static final DAVElement DISCOVER_CHANGED_PATHS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "discover-changed-paths");
    private static final DAVElement INCLUDE_MERGED_REVISIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "include-merged-revisions");
    private static final DAVElement STRICT_NODE_HISTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "strict-node-history");
    private static final DAVElement OMIT_LOG_TEXT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "omit-log-text");
    private static final DAVElement LIMIT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "limit");

    private Map myProperties;
    private StringBuffer myBody;

    public DAVLogHandler(Map properties) {
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
        boolean discoverChangedPaths = false;
        boolean strictNodeHistory = false;
        boolean includeMergedRevisions = false;
        boolean omitLogText = false;
        long startRevision = DAVResource.INVALID_REVISION;
        long endRevision = DAVResource.INVALID_REVISION;
        long limit = 0;
        String[] targetPaths = null;

        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            if (element == DISCOVER_CHANGED_PATHS) {
                discoverChangedPaths = true;
            } else if (element == STRICT_NODE_HISTORY) {
                strictNodeHistory = true;
            } else if (element == INCLUDE_MERGED_REVISIONS) {
                includeMergedRevisions = true;
            } else if (element == OMIT_LOG_TEXT) {
                omitLogText = true;
            } else if (element == START_REVISION) {
                Collection revisions = (Collection) entry.getValue();
                String revisionString = (String) revisions.iterator().next();
                startRevision = Long.parseLong(revisionString);
            } else if (element == END_REVISION) {
                Collection revisions = (Collection) entry.getValue();
                String revisionString = (String) revisions.iterator().next();
                endRevision = Long.parseLong(revisionString);
            } else if (element == LIMIT) {
                Collection limits = (Collection) entry.getValue();
                String limitString = (String) limits.iterator().next();
                limit = Integer.parseInt(limitString);
            } else if (element == PATH) {
                Collection paths = (Collection) entry.getValue();
                targetPaths = new String[paths.size()];
                targetPaths = (String[]) paths.toArray(targetPaths);
            }
        }

        setBody(xmlBuffer);
        DAVXMLUtil.addXMLHeader(getBody());
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, LOG_REPORT.getName(), getProperties().keySet(), getBody());
        resource.getRepository().log(targetPaths, startRevision, endRevision, discoverChangedPaths,
                strictNodeHistory, limit, includeMergedRevisions, omitLogText, this);
        DAVXMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, LOG_REPORT.getName(), getBody());
        return getBody();
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {

        DAVXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", DAVXMLUtil.XML_STYLE_NORMAL, null, getBody());
        DAVXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(logEntry.getRevision()), getBody());

        if (logEntry.getAuthor() != null) {
            DAVXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.CREATOR_DISPLAY_NAME.getName(), logEntry.getAuthor(), getBody());
        }

        if (logEntry.getDate() != null) {
            DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "date", SVNTimeUtil.formatDate(logEntry.getDate()), getBody());
        }

        if (logEntry.getMessage() != null) {
            DAVXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.COMMENT.getName(), logEntry.getMessage(), getBody());
        }

        if (logEntry.getNumberOfChildren() != 0) {
            DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "nbr-children", String.valueOf(logEntry.getNumberOfChildren()), getBody());
        }

        if (logEntry.getChangedPaths() != null) {
            for (Iterator iterator = logEntry.getChangedPaths().entrySet().iterator(); iterator.hasNext();) {
                Map.Entry pathEntry = (Map.Entry) iterator.next();
                String path = (String) pathEntry.getKey();
                SVNLogEntryPath logEntryPath = (SVNLogEntryPath) pathEntry.getValue();
                addChangedPathTag(path, logEntryPath);
            }
        }

        DAVXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", getBody());
    }

    private void addChangedPathTag(String path, SVNLogEntryPath logEntryPath) {
        switch (logEntryPath.getType()) {
            //TODO: check copyfrom revision
            case SVNLogEntryPath.TYPE_ADDED:
                if (logEntryPath.getCopyPath() != null) {
                    Map attrs = new HashMap();
                    attrs.put("copyfrom-path", logEntryPath.getCopyPath());
                    attrs.put("copyfrom-rev", String.valueOf(logEntryPath.getCopyRevision()));
                    DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, attrs, getBody());
                } else {
                    DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, getBody());
                }
                break;
            case SVNLogEntryPath.TYPE_REPLACED:
                if (logEntryPath.getCopyPath() != null) {
                    Map attrs = new HashMap();
                    attrs.put("copyfrom-path", logEntryPath.getCopyPath());
                    attrs.put("copyfrom-rev", String.valueOf(logEntryPath.getCopyRevision()));
                    DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "replaced-path", path, attrs, getBody());
                } else {
                    DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "replaced-path", path, getBody());
                }
                break;
            case SVNLogEntryPath.TYPE_MODIFIED:
                DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "modified-path", path, getBody());
                break;
            case SVNLogEntryPath.TYPE_DELETED:
                DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "deleted-path", path, getBody());
                break;
            default:
                break;
        }
    }
}

