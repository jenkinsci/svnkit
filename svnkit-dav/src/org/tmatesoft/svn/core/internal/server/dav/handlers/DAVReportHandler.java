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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReportHandler extends ServletDAVHandler {

    public static Set REPORT_ELEMENTS = new HashSet();

    protected static final String UTF_8_ENCODING = "UTF-8";

    public static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    public static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    public static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    public static final DAVElement GET_LOCATIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    public static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    public static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    public static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    public static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    private DAVRepositoryManager myRepositoryManager;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;

    private DAVReportHandler myReportHandler;
    private DAVResource myDAVResource;

    private boolean myWriteTextDeltaHeader = true;

    private boolean isWriteTextDeltaHeader() {
        return myWriteTextDeltaHeader;
    }

    protected void setWriteTextDeltaHeader(boolean writeTextDeltaHeader) {
        myWriteTextDeltaHeader = writeTextDeltaHeader;
    }

    static {
        REPORT_ELEMENTS.add(UPDATE_REPORT);
        REPORT_ELEMENTS.add(LOG_REPORT);
        REPORT_ELEMENTS.add(DATED_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCATIONS);
        REPORT_ELEMENTS.add(FILE_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCKS_REPORT);
        REPORT_ELEMENTS.add(REPLAY_REPORT);
        REPORT_ELEMENTS.add(MERGEINFO_REPORT);
    }

    protected DAVReportHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
    }

    protected DAVRequest getDAVRequest() {
        return getReportHandler().getDAVRequest();
    }

    private DAVReportHandler getReportHandler() {
        return myReportHandler;
    }

    private void setReportHandler(DAVReportHandler reportHandler) {
        myReportHandler = reportHandler;
    }

    protected DAVResource getDAVResource() {
        return myDAVResource;
    }

    protected void setDAVResource(DAVResource DAVResource) {
        myDAVResource = DAVResource;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == null) {
            initReportHandler(element);
        }
        getReportHandler().handleAttributes(parent, element, attrs);
    }

    protected void handleAttributes(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        getDAVRequest().startElement(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getReportHandler().handleCData(parent, element, cdata);
    }

    protected void handleCData(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getDAVRequest().endElement(parent, element, cdata);
    }

    public void execute() throws SVNException {
        readInput();

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(HttpServletResponse.SC_OK);

        getReportHandler().execute();
        //TODO: In some cases native svn starts blame command and clean all out headers
    }

    private void initReportHandler(DAVElement rootElement) throws SVNException {
        if (rootElement == DATED_REVISIONS_REPORT) {
            setReportHandler(new DAVDatedRevisionHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == FILE_REVISIONS_REPORT) {
            setReportHandler(new DAVFileRevisionsHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == GET_LOCATIONS) {
            setReportHandler(new DAVGetLocationsHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == LOG_REPORT) {
            setReportHandler(new DAVLogHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == MERGEINFO_REPORT) {
            setReportHandler(new DAVMergeInfoHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == GET_LOCKS_REPORT) {
            setReportHandler(new DAVGetLocksHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == REPLAY_REPORT) {
            setReportHandler(new DAVReplayHandler(myRepositoryManager, myRequest, myResponse));
        } else if (rootElement == UPDATE_REPORT) {
            setReportHandler(new DAVUpdateHandler(myRepositoryManager, myRequest, myResponse));
        }
    }

    protected void write(String string) throws SVNException {
        try {
            getResponseWriter().write(string);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    protected void write(StringBuffer stringBuffer) throws SVNException {
        write(stringBuffer.toString());
    }

    protected void writeXMLHeader() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);
        write(xmlBuffer);
    }

    protected void writeXMLFooter() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        addXMLFooter(xmlBuffer);
        write(xmlBuffer);
    }

    protected void addXMLHeader(StringBuffer xmlBuffer) {
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, getDAVRequest().getRootElement().getName(), getDAVRequest().getElements(), xmlBuffer);
    }

    protected void addXMLFooter(StringBuffer xmlBuffer) {
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, getDAVRequest().getRootElement().getName(), xmlBuffer);
    }

    protected void writeTextDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            diffWindow.writeTo(baos, isWriteTextDeltaHeader(), getSVNDiffVersion());
            byte[] textDelta = baos.toByteArray();
            String txDelta = SVNBase64.byteArrayToBase64(textDelta);
            write(txDelta);
            setWriteTextDeltaHeader(false);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    protected void writePropertyTag(String tagName, String propertyName, String propertyValue) throws SVNException {
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
}
