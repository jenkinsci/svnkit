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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVLogHandler implements IDAVReportHandler, ISVNLogEntryHandler {

    private static final DAVElement DISCOVER_CHANGED_PATHS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "discover-changed-paths");
    private static final DAVElement INCLUDE_MERGED_REVISIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "include-merged-revisions");
    private static final DAVElement STRICT_NODE_HISTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "strict-node-history");
    private static final DAVElement OMIT_LOG_TEXT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "omit-log-text");
    private static final DAVElement LIMIT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "limit");

    private DAVResource myDAVResource;
    private IDAVRequest myDAVRequest;

    private Writer myResponseWriter;

    public DAVLogHandler(DAVResource resource, IDAVRequest davRequest) {
        myDAVResource = resource;
        myDAVRequest = davRequest;
    }

    private DAVResource getDAVResource() {
        return myDAVResource;
    }

    private IDAVRequest getDAVRequest() {
        return myDAVRequest;
    }

    private Writer getResponseWriter() {
        return myResponseWriter;
    }

    private void setResponseWriter(Writer responseWriter) {
        myResponseWriter = responseWriter;
    }

    public int getContentLength() {
        return -1;
    }

    public void writeTo(Writer out) throws SVNException {
        setResponseWriter(out);

        boolean discoverChangedPaths = false;
        boolean strictNodeHistory = false;
        boolean includeMergedRevisions = false;
        boolean omitLogText = false;
        long startRevision = DAVResource.INVALID_REVISION;
        long endRevision = DAVResource.INVALID_REVISION;
        long limit = 0;
        String[] targetPaths = null;

        for (Iterator iterator = getDAVRequest().entryIterator(); iterator.hasNext();) {
            IDAVRequest.Entry entry = (IDAVRequest.Entry) iterator.next();
            DAVElement element = entry.getElement();
            if (element == DISCOVER_CHANGED_PATHS) {
                discoverChangedPaths = true;
            } else if (element == STRICT_NODE_HISTORY) {
                strictNodeHistory = true;
            } else if (element == INCLUDE_MERGED_REVISIONS) {
                includeMergedRevisions = true;
            } else if (element == OMIT_LOG_TEXT) {
                omitLogText = true;
            } else if (element == START_REVISION) {
                String revisionString = entry.getFirstValue();
                startRevision = Long.parseLong(revisionString);
            } else if (element == END_REVISION) {
                String revisionString = entry.getFirstValue();
                endRevision = Long.parseLong(revisionString);
            } else if (element == LIMIT) {
                String limitString = entry.getFirstValue();
                limit = Integer.parseInt(limitString);
            } else if (element == PATH) {
                Collection paths = entry.getValues();
                targetPaths = new String[paths.size()];
                targetPaths = (String[]) paths.toArray(targetPaths);
            }
        }

        writeXMLHeader();

        getDAVResource().getRepository().log(targetPaths, startRevision, endRevision, discoverChangedPaths,
                strictNodeHistory, limit, includeMergedRevisions, omitLogText, this);

        writeXMLFooter();

    }

    private void writeString(String stringToWrite) throws SVNException {
        try {
            getResponseWriter().write(stringToWrite);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void writeXMLHeader() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, LOG_REPORT.getName(), getDAVRequest().getElements(), xmlBuffer);
        writeString(xmlBuffer.toString());
    }

    private void writeXMLFooter() throws SVNException {
        String footer = XMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, LOG_REPORT.getName(), null).toString();
        writeString(footer);
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(logEntry.getRevision()), xmlBuffer);

        if (logEntry.getAuthor() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.CREATOR_DISPLAY_NAME.getName(), logEntry.getAuthor(), xmlBuffer);
        }

        if (logEntry.getDate() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "date", SVNTimeUtil.formatDate(logEntry.getDate()), xmlBuffer);
        }

        if (logEntry.getMessage() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.COMMENT.getName(), logEntry.getMessage(), xmlBuffer);
        }

        if (logEntry.getNumberOfChildren() != 0) {
            XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "nbr-children", String.valueOf(logEntry.getNumberOfChildren()), xmlBuffer);
        }

        writeString(xmlBuffer.toString());

        if (logEntry.getChangedPaths() != null) {
            for (Iterator iterator = logEntry.getChangedPaths().entrySet().iterator(); iterator.hasNext();) {
                Map.Entry pathEntry = (Map.Entry) iterator.next();
                String path = (String) pathEntry.getKey();
                SVNLogEntryPath logEntryPath = (SVNLogEntryPath) pathEntry.getValue();
                addChangedPathTag(path, logEntryPath);
            }
        }

        String closingTag = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", null).toString();
        writeString(closingTag);
    }

    private void addChangedPathTag(String path, SVNLogEntryPath logEntryPath) throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        switch (logEntryPath.getType()) {
            //TODO: check copyfrom revision
            case SVNLogEntryPath.TYPE_ADDED:
                if (logEntryPath.getCopyPath() != null) {
                    Map attrs = new HashMap();
                    attrs.put("copyfrom-path", logEntryPath.getCopyPath());
                    attrs.put("copyfrom-rev", String.valueOf(logEntryPath.getCopyRevision()));
                    XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, attrs, xmlBuffer);
                } else {
                    XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, xmlBuffer);
                }
                break;
            case SVNLogEntryPath.TYPE_REPLACED:
                if (logEntryPath.getCopyPath() != null) {
                    Map attrs = new HashMap();
                    attrs.put("copyfrom-path", logEntryPath.getCopyPath());
                    attrs.put("copyfrom-rev", String.valueOf(logEntryPath.getCopyRevision()));
                    XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "replaced-path", path, attrs, xmlBuffer);
                } else {
                    XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "replaced-path", path, xmlBuffer);
                }
                break;
            case SVNLogEntryPath.TYPE_MODIFIED:
                XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "modified-path", path, xmlBuffer);
                break;
            case SVNLogEntryPath.TYPE_DELETED:
                XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "deleted-path", path, xmlBuffer);
                break;
            default:
                break;
        }
        writeString(xmlBuffer.toString());
    }
}

