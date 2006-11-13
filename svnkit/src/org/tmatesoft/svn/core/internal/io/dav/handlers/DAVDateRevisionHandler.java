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

import java.util.Date;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVDateRevisionHandler extends BasicDAVHandler {
	
	public static StringBuffer generateDateRevisionRequest(StringBuffer body, Date date) {
        body = body == null ? new StringBuffer() : body;
        body.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        body.append("<S:dated-rev-report xmlns:S=\"svn:\" ");
        body.append("xmlns:D=\"DAV:\">");
        body.append("<D:creationdate>");
        SVNTimeUtil.formatDate(date, body);
        body.append("</D:creationdate>");
        body.append("</S:dated-rev-report>");
        return body;
	}
	
	private long myRevisionNumber;

	public DAVDateRevisionHandler() {
		init();
		myRevisionNumber = -1;
	}
	
	public long getRevisionNumber() {
		return myRevisionNumber;
	}

	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) {
		if (element == DAVElement.VERSION_NAME && cdata != null) {
			myRevisionNumber = Long.parseLong(cdata.toString());
		}
	}

	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) {
	}

}
