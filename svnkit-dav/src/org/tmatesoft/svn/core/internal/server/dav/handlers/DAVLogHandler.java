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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVLogHandler extends DAVReportHandler implements ISVNLogEntryHandler {

    private DAVLogRequest myDAVRequest;
    private int myDepth = 0;

    public DAVLogHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getLogRequest();
    }

    private DAVLogRequest getLogRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVLogRequest();
        }
        return myDAVRequest;
    }

    private int getDepth() {
        return myDepth;
    }

    private void increaseDepth() {
        myDepth++;
    }

    private void decreaseDepth() {
        myDepth--;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        writeXMLHeader();

        for (int i = 0; i < getLogRequest().getTargetPaths().length; i++) {
            String currentPath = getLogRequest().getTargetPaths()[i];
            DAVPathUtil.testCanonical(currentPath);
            getLogRequest().getTargetPaths()[i] = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), currentPath);
        }

        getDAVResource().getRepository().log(getLogRequest().getTargetPaths(),
                getLogRequest().getStartRevision(),
                getLogRequest().getEndRevision(),
                getLogRequest().isDiscoverChangedPaths(),
                getLogRequest().isStrictNodeHistory(),
                getLogRequest().getLimit(),
                getLogRequest().isIncludeMergedRevisions(),
                getLogRequest().getRevisionProperties(),
                this);

        writeXMLFooter();
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        if (logEntry.getRevision() == DAVResource.INVALID_REVISION) {
            if (getDepth() == 0) {
                return;
            }
            decreaseDepth();
        }

        StringBuffer xmlBuffer = new StringBuffer();
        SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        SVNXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), String.valueOf(logEntry.getRevision()), xmlBuffer);

        boolean noCustomProperties = getLogRequest().isCustomPropertyRequested();
        for (Iterator iterator = logEntry.getRevisionProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String property = (String) entry.getKey();
            Object value = entry.getValue();
            if (property.equals(SVNRevisionProperty.AUTHOR)) {
                SVNXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.CREATOR_DISPLAY_NAME.getName(), (String) value, xmlBuffer);
            } else if (property.equals(SVNRevisionProperty.DATE)) {
                SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "date", SVNDate.formatDate((Date) value), xmlBuffer);
            } else if (property.equals(SVNRevisionProperty.LOG)) {
                SVNXMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.COMMENT.getName(), (String) value, xmlBuffer);
            } else {
                noCustomProperties = false;
                SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "revprop", (String) value, NAME_ATTR, property, xmlBuffer);
            }
        }

        if (noCustomProperties) {
            SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "no-custom-revprops", SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        }

        if (logEntry.hasChildren()) {
            SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "has-children", SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
            increaseDepth();
        }

        write(xmlBuffer);

        if (logEntry.getChangedPaths() != null) {
            for (Iterator iterator = logEntry.getChangedPaths().entrySet().iterator(); iterator.hasNext();) {
                Map.Entry pathEntry = (Map.Entry) iterator.next();
                String path = (String) pathEntry.getKey();
                SVNLogEntryPath logEntryPath = (SVNLogEntryPath) pathEntry.getValue();
                addChangedPathTag(path, logEntryPath);
            }
        }

        xmlBuffer = SVNXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "log-item", null);
        write(xmlBuffer);
    }

    private void addChangedPathTag(String path, SVNLogEntryPath logEntryPath) throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        switch (logEntryPath.getType()) {
            case SVNLogEntryPath.TYPE_ADDED:
                if (logEntryPath.getCopyPath() != null && DAVResource.isValidRevision(logEntryPath.getCopyRevision())) {
                    Map attrs = new HashMap();
                    attrs.put(COPYFROM_PATH_ATTR, logEntryPath.getCopyPath());
                    attrs.put(COPYFROM_REVISION_ATTR, String.valueOf(logEntryPath.getCopyRevision()));
                    SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, attrs, xmlBuffer);
                } else {
                    SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "added-path", path, xmlBuffer);
                }
                break;
            case SVNLogEntryPath.TYPE_REPLACED:
                Map attrs = null;
                if (logEntryPath.getCopyPath() != null && DAVResource.isValidRevision(logEntryPath.getCopyRevision())) {
                    attrs = new HashMap();
                    attrs.put(COPYFROM_PATH_ATTR, logEntryPath.getCopyPath());
                    attrs.put(COPYFROM_REVISION_ATTR, String.valueOf(logEntryPath.getCopyRevision()));
                }
                SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "replaced-path", path, attrs, xmlBuffer);

                break;
            case SVNLogEntryPath.TYPE_MODIFIED:
                SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "modified-path", path, xmlBuffer);
                break;
            case SVNLogEntryPath.TYPE_DELETED:
                SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "deleted-path", path, xmlBuffer);
                break;
            default:
                break;
        }
        write(xmlBuffer);
    }
}

