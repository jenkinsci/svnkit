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

import java.io.Writer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Collection;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVMergeInfoHandler implements IDAVReportHandler {

    private static final DAVElement INHERIT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "inherit");

    private DAVResource myDAVResource;
    private IDAVRequest myDAVRequest;

    private String myResponseBody;

    public DAVMergeInfoHandler(DAVResource resource, IDAVRequest davRequest) throws SVNException {
        myDAVResource = resource;
        myDAVRequest = davRequest;
        generateResponseBody();
    }

    private DAVResource getDAVResource() {
        return myDAVResource;
    }

    private IDAVRequest getDAVRequest() {
        return myDAVRequest;
    }

    private String getResponseBody() {
        return myResponseBody;
    }

    private void setResponseBody(String responseBody) {
        myResponseBody = responseBody;
    }


    public int getContentLength() {
        return getResponseBody() == null ? -1 : getResponseBody().getBytes().length;
    }

    public void writeTo(Writer out) throws SVNException {
        try {
            out.write(getResponseBody());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generateResponseBody() throws SVNException {

        long revision = DAVResource.INVALID_REVISION;
        SVNMergeInfoInheritance inherit = SVNMergeInfoInheritance.EXPLICIT;
        String[] targetPaths = null;


        for (Iterator iterator = getDAVRequest().entryIterator(); iterator.hasNext();) {
            IDAVRequest.Entry entry = (IDAVRequest.Entry) iterator.next();
            if (entry.getElement() == REVISION) {
                revision = Long.parseLong(entry.getFirstValue());
            } else if (entry.getElement() == INHERIT) {
                SVNMergeInfoInheritance requestedInherit = parseInheritance(entry.getFirstValue());
                if (requestedInherit != null) {
                    inherit = requestedInherit;
                }
            } else if (entry.getElement() == PATH) {
                Collection paths = entry.getValues();
                targetPaths = new String[paths.size()];
                targetPaths = (String[]) paths.toArray(targetPaths);
            }
        }

        StringBuffer xmlBuffer = new StringBuffer();
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, MERGEINFO_REPORT.getName(), getDAVRequest().getElements(), xmlBuffer);

        Map mergeInfoMap = getDAVResource().getRepository().getMergeInfo(targetPaths, revision, inherit);
        if (mergeInfoMap != null && !mergeInfoMap.isEmpty()) {
            for (Iterator iterator = mergeInfoMap.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                SVNMergeInfo mergeInfo = (SVNMergeInfo) entry.getValue();
                addMergeInfo(mergeInfo, xmlBuffer);
            }
        }
        XMLUtil.addXMLFooter(DAVXMLUtil.SVN_NAMESPACE_PREFIX, MERGEINFO_REPORT.getName(), xmlBuffer);
        setResponseBody(xmlBuffer.toString());
    }

    private void addMergeInfo(SVNMergeInfo mergeInfo, StringBuffer xmlBuffer) {
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-path", mergeInfo.getPath(), xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-info", null, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-item", xmlBuffer);
    }


    //TODO: move this method to SVNMergeInfoInheritance
    private SVNMergeInfoInheritance parseInheritance(String inheritance) {
        if (SVNMergeInfoInheritance.EXPLICIT.toString().equals(inheritance)) {
            return SVNMergeInfoInheritance.EXPLICIT;
        } else if (SVNMergeInfoInheritance.INHERITED.toString().equals(inheritance)) {
            return SVNMergeInfoInheritance.INHERITED;
        } else if (SVNMergeInfoInheritance.NEAREST_ANCESTOR.toString().equals(inheritance)) {
            return SVNMergeInfoInheritance.NEAREST_ANCESTOR;
        }
        return null;
    }

}
