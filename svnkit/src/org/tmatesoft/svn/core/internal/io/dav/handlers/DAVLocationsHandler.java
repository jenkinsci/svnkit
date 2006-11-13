/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVLocationsHandler extends BasicDAVHandler {
	
	public static StringBuffer generateLocationsRequest(StringBuffer buffer, String path, long pegRevision, long[] revisions) {
		buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        buffer.append("<S:get-locations xmlns:S=\"svn:\" xmlns:D=\"DAV:\" >");
        buffer.append("<S:path>");
        buffer.append(SVNEncodingUtil.xmlEncodeCDATA(path));
        buffer.append("</S:path>");
        buffer.append("<S:peg-revision>");
        buffer.append(pegRevision);
        buffer.append("</S:peg-revision>");
        for(int i = 0; i < revisions.length; i++) {
            buffer.append("<S:location-revision>");
            buffer.append(revisions[i]);
            buffer.append("</S:location-revision>");
        }
        buffer.append("</S:get-locations>");
        return buffer;
	}
	
    private static final DAVElement LOCATION_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations-report");
    private static final DAVElement LOCATION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "location");
	
	private ISVNLocationEntryHandler myLocationEntryHandler;
	private int myCount;


	public DAVLocationsHandler(ISVNLocationEntryHandler handler) {
		myLocationEntryHandler = handler;
		init();
	}
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == LOCATION_REPORT && element == LOCATION) {
            String revStr = attrs.getValue("rev");
            if (revStr != null) {
                String path = attrs.getValue("path");
                if (path != null && myLocationEntryHandler != null) {
                    myLocationEntryHandler.handleLocationEntry(new SVNLocationEntry(Long.parseLong(revStr), path));
                    myCount++;
                }
            }
        }
	}
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }
	
	public int getEntriesCount() {
		return myCount;
	}
}
