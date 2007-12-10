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
package org.tmatesoft.svn.core.internal.io.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLocationSegmentHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.xml.sax.Attributes;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVLocationSegmentsHandler extends BasicDAVHandler {
    private static final DAVElement LOCATION_SEGMENTS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, 
            "get-location-segments-report");

    private ISVNLocationSegmentHandler myLocationSegmentHandler;
    private int myCount;

    public static StringBuffer generateGetLocationSegmentsRequest(StringBuffer xmlBuffer, String path, 
            long pegRevision, long startRevision, long endRevision) {
        xmlBuffer = xmlBuffer == null ? new StringBuffer() : xmlBuffer;
        SVNXMLUtil.addXMLHeader(xmlBuffer);
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "get-location-segments", 
                SVN_DAV_NAMESPACES_LIST, SVNXMLUtil.PREFIX_MAP, xmlBuffer);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "path", path, xmlBuffer);
        if (SVNRevision.isValidRevisionNumber(pegRevision)) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "peg-revision", 
                    String.valueOf(pegRevision), xmlBuffer);
        }
        if (SVNRevision.isValidRevisionNumber(startRevision)) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "start-revision", 
                    String.valueOf(startRevision), xmlBuffer);
        }
        if (SVNRevision.isValidRevisionNumber(endRevision)) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "end-revision", 
                    String.valueOf(endRevision), xmlBuffer);
        }
        SVNXMLUtil.addXMLFooter(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "get-location-segments", xmlBuffer);
        return xmlBuffer;
    }
    
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }

}
