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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVFileRevisionsHandler implements IDAVReportHandler, ISVNFileRevisionHandler {

    private DAVResource myDAVResource;
    private IDAVRequest myDAVRequest;

    private Writer myResponseWriter;

    private boolean myWriteHeader;
    private boolean myDiffCompress;

    public DAVFileRevisionsHandler(DAVResource resource, IDAVRequest davRequest, boolean diffCompress) {
        myDAVResource = resource;
        myDAVRequest = davRequest;
        myWriteHeader = true;
        myDiffCompress = diffCompress;
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

    private boolean addWriteHeader() {
        return myWriteHeader;
    }

    private void setWriteHeader(boolean writeHeader) {
        myWriteHeader = writeHeader;
    }

    private boolean isDiffCompress() {
        return myDiffCompress;
    }

    public int getContentLength() {
        return -1;
    }

    public void writeTo(Writer out) throws SVNException {
        setResponseWriter(out);

        String path = null;
        long startRevision = DAVResource.INVALID_REVISION;
        long endRevision = DAVResource.INVALID_REVISION;

        for (Iterator iterator = getDAVRequest().entryIterator(); iterator.hasNext();) {
            IDAVRequest.Entry entry = (IDAVRequest.Entry) iterator.next();
            DAVElement element = entry.getElement();
            if (element == PATH) {
                path = entry.getFirstValue();
            } else if (element == START_REVISION) {
                startRevision = Long.parseLong(entry.getFirstValue());
            } else if (element == END_REVISION) {
                endRevision = Long.parseLong(entry.getFirstValue());
            }
        }

        writeXMLHeader();

        getDAVResource().getRepository().getFileRevisions(path, startRevision, endRevision, this);

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
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISIONS_REPORT.getName(), getDAVRequest().getElements(), xmlBuffer);
        writeString(xmlBuffer.toString());
    }

    private void writeXMLFooter() throws SVNException {
        String footer = XMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISIONS_REPORT.getName(), null).toString();
        writeString(footer);
    }

    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(PATH.getName(), fileRevision.getPath());
        attrs.put("rev", String.valueOf(fileRevision.getRevision()));
        String tagString = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", XMLUtil.XML_STYLE_NORMAL, attrs, null).toString();
        writeString(tagString);
        for (Iterator iterator = fileRevision.getRevisionProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            addPropertyTag("rev-prop", propertyName, propertyValue);
        }
        for (Iterator iterator = fileRevision.getPropertiesDelta().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            if (propertyValue != null) {
                addPropertyTag("set-prop", propertyName, propertyValue);
            } else {
                tagString = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "remove-prop", XMLUtil.XML_STYLE_SELF_CLOSING, NAME_ATTR, propertyName, null).toString();
                writeString(tagString);
            }
        }
    }

    private void addPropertyTag(String tagName, String propertyName, String propertyValue) throws SVNException {
        String tagString;
        if (SVNEncodingUtil.isXMLSafe(propertyValue)) {
            tagString = XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, propertyValue, NAME_ATTR, propertyName, null).toString();
            writeString(tagString);
        } else {
            Map attrs = new HashMap();
            attrs.put(NAME_ATTR, propertyName);
            attrs.put(ENCODING_ATTR, "base64");
            propertyValue = SVNBase64.byteArrayToBase64(propertyValue.getBytes());
            tagString = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_PROTECT_PCDATA, attrs, null).toString();
            writeString(tagString);
            writeString(propertyValue);
            tagString = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, null).toString();
            writeString(tagString);
        }

    }

    public void closeRevision(String token) throws SVNException {
        String tagString = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", null).toString();
        writeString(tagString);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        String tagString = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", XMLUtil.XML_STYLE_NORMAL, null, null).toString();
        writeString(tagString);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            diffWindow.writeTo(baos, addWriteHeader(), isDiffCompress());
            byte[] textDelta = baos.toByteArray();
            String txDelta = SVNBase64.byteArrayToBase64(textDelta);
            writeString(txDelta);
            setWriteHeader(false);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        String tagString = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", null).toString();
        writeString(tagString);
    }
}
