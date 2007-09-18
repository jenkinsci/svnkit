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
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVMergeInfoHandler extends DAVReportHandler {

    private DAVMergeInfoRequest myDAVRequest;

    public DAVMergeInfoHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        super(repositoryManager, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getMergeInfoRequest();
    }

    private DAVMergeInfoRequest getMergeInfoRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVMergeInfoRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(createDAVResource(false, false));

        String responseBody = generateResponseBody();

        try {
            setResponseContentLength(responseBody.getBytes(UTF_8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
        }

        write(responseBody);
    }

    private String generateResponseBody() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);

        for (int i = 0; i < getMergeInfoRequest().getTargetPaths().length; i++) {
            String currentPath = getMergeInfoRequest().getTargetPaths()[i];
            DAVPathUtil.testCanonical(currentPath);
            getMergeInfoRequest().getTargetPaths()[i] = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), currentPath);
        }

        Map mergeInfoMap = getDAVResource().getRepository().getMergeInfo(getMergeInfoRequest().getTargetPaths(), getMergeInfoRequest().getRevision(), getMergeInfoRequest().getInherit());
        if (mergeInfoMap != null && !mergeInfoMap.isEmpty()) {
            for (Iterator iterator = mergeInfoMap.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String path = (String) entry.getKey();
                SVNMergeInfo mergeInfo = (SVNMergeInfo) entry.getValue();
                addMergeInfo(path, mergeInfo, xmlBuffer);
            }
        }

        addXMLFooter(xmlBuffer);
        return xmlBuffer.toString();
    }

    private void addMergeInfo(String path, SVNMergeInfo mergeInfo, StringBuffer xmlBuffer) {
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-path", path, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-info", addSourcePathes(mergeInfo), xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", xmlBuffer);
    }

    private String addSourcePathes(SVNMergeInfo mergeInfo) {
        StringBuffer result = new StringBuffer();
        for (Iterator iterator = mergeInfo.getMergeSourcesToMergeLists().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String sourcePath = (String) entry.getKey();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) entry.getValue();
            result.append(sourcePath);
            result.append(":");
            result.append(rangeList.toString());
            if (iterator.hasNext()) {
                result.append('\n');
            }
        }
        return result.toString();
    }
}
