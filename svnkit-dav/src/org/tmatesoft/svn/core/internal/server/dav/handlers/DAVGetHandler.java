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
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Iterator;
import java.io.IOException;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetHandler extends ServletDAVHandler {

    public DAVGetHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }

    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestContext(), getRequestURI(), label, false);

        if (!resource.exists()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PATH_NOT_FOUND, "Path ''{0}'' you requested not found", resource.getParameterPath()));
        }

        gatherRequestHeadersInformation(resource);
        setDefaultResponseHeaders(resource);

        if (resource.isCollection()) {
            StringBuffer body = new StringBuffer();
            generateResponseBody(resource, body);
            try {
                getResponseWriter().write(body.toString());
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
            }
        } else {
            resource.output(getResponseOutputStream());
        }


    }

    private void generateResponseBody(DAVResource resource, StringBuffer buffer) throws SVNException {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR && resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION
                && resource.getType() != DAVResource.DAV_RESOURCE_TYPE_WORKING) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Cannot GET this type of resource."));
        }
        startBody(resource.getParameterPath(), resource.getRevision(), buffer);
        addUpperDirectoryLink(resource.getParameterPath(), buffer);
        addDirectoryEntries(resource.getEntries(), buffer);
        finishBody(buffer);
    }

    private void startBody(String parameterPath, long revision, StringBuffer buffer) {
        buffer.append("<html><head><title> Revision ");
        buffer.append(String.valueOf(revision));
        buffer.append(" : ");
        buffer.append(parameterPath);
        buffer.append("</title></head>\n");
        buffer.append("<body>\n <h2> Revision ");
        buffer.append(String.valueOf(revision));
        buffer.append(" : ");
        buffer.append(parameterPath);
        buffer.append("</h2>\n <ul>\n");
    }

    private void addUpperDirectoryLink(String parameterPath, StringBuffer buffer) {
        if (parameterPath.length() > 0 || !"/".equals(parameterPath)) {
            buffer.append("<li><a href=\"../\">..</a></li>\n");
        }
    }

    private void addDirectoryEntries(Collection entries, StringBuffer buffer) {
        for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            boolean isDir = entry.getKind() == SVNNodeKind.DIR;
            buffer.append("<li><a href=\"");
            buffer.append(entry.getName());
            buffer.append(isDir ? "/" : "");
            buffer.append("\">");
            buffer.append(entry.getName());
            buffer.append(isDir ? "/" : "");
            buffer.append("</a></li>\n");
        }
    }

    private void finishBody(StringBuffer buffer) {
        buffer.append("</ul><hr noshade><em>");
        buffer.append("Powered by <a href=\"http://svnkit.com/\">SVNKit</a> ");
        buffer.append("pure Java Subversion client & server library");
        buffer.append("</em>\n</body></html>");
    }

}
