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

import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVMergeInfoHandler extends ReportHandler {

    private DAVMergeInfoRequest myDAVRequest;
    private String myResponseBody;

    public DAVMergeInfoHandler(DAVResource resource, Writer responseWriter) throws SVNException {
        super(resource, responseWriter);
        generateResponseBody();
    }

    private String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String responseBody) {
        myResponseBody = responseBody;
    }


    public DAVRequest getDAVRequest() {
        return getMergeInfoRequest();
    }

    private DAVMergeInfoRequest getMergeInfoRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVMergeInfoRequest();
        }
        return myDAVRequest;
    }

    public int getContentLength() throws SVNException {
        generateResponseBody();
        try {
            return getResponseBody().getBytes(UTF_8_ENCODING).length;
        } catch (UnsupportedEncodingException e) {
        }
        return -1;
    }

    public void sendResponse() throws SVNException {
        generateResponseBody();
        write(getResponseBody());
    }

    private void generateResponseBody() throws SVNException {
        if (getResponseBody() == null) {
            StringBuffer xmlBuffer = new StringBuffer();
            addXMLHeader(xmlBuffer);

            Map mergeInfoMap = getDAVResource().getRepository().getMergeInfo(getMergeInfoRequest().getTargetPaths(), getMergeInfoRequest().getRevision(), getMergeInfoRequest().getInherit());
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
    }

    private void addMergeInfo(SVNMergeInfo mergeInfo, StringBuffer xmlBuffer) {
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-path", mergeInfo.getPath(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-info", null, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", xmlBuffer);
    }
}
