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

import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVFileRevsHandler implements IDAVReportHandler, ISVNFileRevisionHandler {

    private Map myProperties;
    private StringBuffer myBody;


    public DAVFileRevsHandler(Map properties) {
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
        String path = null;
        long startRevision = DAVResource.INVALID_REVISION;
        long endRevision = DAVResource.INVALID_REVISION;

        for (Iterator iterator = getProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            DAVElement element = (DAVElement) entry.getKey();
            Collection values = (Collection) entry.getValue();
            if (element == PATH) {
                path = (String) values.iterator().next();
            } else if (element == START_REVISION) {
                startRevision = Long.parseLong((String) values.iterator().next());
            } else if (element == END_REVISION) {
                endRevision = Long.parseLong((String) values.iterator().next());
            }
        }

        setBody(xmlBuffer);
        DAVXMLUtil.addXMLHeader(getBody());
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISIONS_REPORT.getName(), getProperties().keySet(), getBody());

        //TODO: native SVN takes SVNDiff Version from request's header sets it to generate txdelta
        resource.getRepository().getFileRevisions(path, startRevision, endRevision, this);

        DAVXMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISIONS_REPORT.getName(), getBody());

        return getBody();
    }

    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(PATH.getName(), fileRevision.getPath());
        attrs.put("rev", String.valueOf(fileRevision.getRevision()));
        DAVXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", DAVXMLUtil.XML_STYLE_NORMAL, attrs, getBody());
        for (Iterator iterator = fileRevision.getRevisionProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            //TODO: native svn checks if propertyValue is XML safe, if not applies base64 encoding.
            DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "rev-prop", propertyValue, NAME_ATTR, propertyName, getBody());
        }
        for (Iterator iterator = fileRevision.getPropertiesDelta().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            if (propertyValue != null) {
                DAVXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "set-prop", propertyValue, NAME_ATTR, propertyName, getBody());
            } else {
                DAVXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "remove-prop", DAVXMLUtil.XML_STYLE_SELF_CLOSING, NAME_ATTR, propertyName, getBody());
            }
        }
    }

    public void closeRevision(String token) throws SVNException {
        DAVXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", getBody());
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        DAVXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", DAVXMLUtil.XML_STYLE_NORMAL, null, getBody());
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        DAVXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", getBody());
    }
}
