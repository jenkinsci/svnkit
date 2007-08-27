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
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVMergeInfoHandler extends ReportHandler {

    private String myResponseBody;

    public DAVMergeInfoHandler(DAVResource resource, DAVMergeInfoRequest reportRequest, Writer responseWriter) throws SVNException {
        super(resource, reportRequest, responseWriter);
        generateResponseBody();
    }

    private String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String responseBody) {
        myResponseBody = responseBody;
    }

    private DAVMergeInfoRequest getDAVRequest() {
        return (DAVMergeInfoRequest) myDAVRequest;
    }

    public int getContentLength() {
        return getResponseBody() == null ? -1 : getResponseBody().getBytes().length;
    }

    public void sendResponse() throws SVNException {
        try {
            getResponseWriter().write(getResponseBody());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generateResponseBody() throws SVNException {

        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);

        Map mergeInfoMap = getDAVResource().getRepository().getMergeInfo(getDAVRequest().getTargetPaths(), getDAVRequest().getRevision(), getDAVRequest().getInherit());
        if (mergeInfoMap != null && !mergeInfoMap.isEmpty()) {
            for (Iterator iterator = mergeInfoMap.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                SVNMergeInfo mergeInfo = (SVNMergeInfo) entry.getValue();
                addMergeInfo(mergeInfo, xmlBuffer);
            }
        }

        addXMLFooter(xmlBuffer);
        setResponseBody(xmlBuffer.toString());
    }

    private void addMergeInfo(SVNMergeInfo mergeInfo, StringBuffer xmlBuffer) {
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-path", mergeInfo.getPath(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-info", null, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", xmlBuffer);
    }
}
