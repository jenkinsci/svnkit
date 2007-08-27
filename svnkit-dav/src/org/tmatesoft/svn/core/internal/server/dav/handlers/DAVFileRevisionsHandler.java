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
public class DAVFileRevisionsHandler extends ReportHandler implements ISVNFileRevisionHandler {

    private boolean myWriteHeader;
    private boolean myDiffCompress;

    public DAVFileRevisionsHandler(DAVResource resource, DAVFileRevisionsRequest reportRequest, Writer responseWriter, boolean diffCompress) {
        super(resource, reportRequest, responseWriter);
        myWriteHeader = true;
        myDiffCompress = diffCompress;
    }

    private DAVFileRevisionsRequest getDAVRequest() {
        return (DAVFileRevisionsRequest) myDAVRequest;
    }

    private boolean writeHeader() {
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

    public void sendResponse() throws SVNException {
        writeXMLHeader();

        getDAVResource().getRepository().getFileRevisions(getDAVRequest().getPath(), getDAVRequest().getStartRevision(), getDAVRequest().getEndRevision(), this);

        writeXMLFooter();
    }

    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("path", fileRevision.getPath());
        attrs.put("rev", String.valueOf(fileRevision.getRevision()));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", XMLUtil.XML_STYLE_NORMAL, attrs, null);
        write(xmlBuffer);
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
                xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "remove-prop", XMLUtil.XML_STYLE_SELF_CLOSING, "name", propertyName, null);
                write(xmlBuffer);
            }
        }
    }

    private void addPropertyTag(String tagName, String propertyName, String propertyValue) throws SVNException {
        StringBuffer xmlBuffer;
        if (SVNEncodingUtil.isXMLSafe(propertyValue)) {
            xmlBuffer = XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, propertyValue, "name", propertyName, null);
            write(xmlBuffer);
        } else {
            Map attrs = new HashMap();
            attrs.put("name", propertyName);
            attrs.put("encoding", "base64");
            propertyValue = SVNBase64.byteArrayToBase64(propertyValue.getBytes());
            xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_PROTECT_PCDATA, attrs, null);
            write(xmlBuffer);
            write(propertyValue);
            xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, null);
            write(xmlBuffer);
        }

    }

    public void closeRevision(String token) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", null);
        write(xmlBuffer);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", XMLUtil.XML_STYLE_NORMAL, null, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            diffWindow.writeTo(baos, writeHeader(), isDiffCompress());
            byte[] textDelta = baos.toByteArray();
            String txDelta = SVNBase64.byteArrayToBase64(textDelta);
            write(txDelta);
            setWriteHeader(false);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", null);
        write(xmlBuffer);
    }
}
