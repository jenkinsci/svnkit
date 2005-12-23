/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVResponse;
import org.tmatesoft.svn.core.internal.io.dav.DAVStatus;
import org.tmatesoft.svn.core.internal.io.dav.IDAVResponseHandler;
import org.xml.sax.Attributes;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVPropertiesHandler extends BasicDAVHandler {
	
	public static StringBuffer generatePropertiesRequest(StringBuffer body, DAVElement[] properties) {
        body = body == null ? new StringBuffer() : body;
        body.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\">");
        if (properties != null) {
            body.append("<prop>");
            for (int i = 0; i < properties.length; i++) {
                body.append("<");
                body.append(properties[i].getName());
                body.append(" xmlns=\"");
                body.append(properties[i].getNamespace());
                body.append("\"/>");
            }
            body.append("</prop></propfind>");
        } else {
            body.append("<allprop/></propfind>");
        }
        return body;

	}
    
    private DAVResponse myResponse;
    private IDAVResponseHandler myResponseHandler;
    
    public DAVPropertiesHandler(IDAVResponseHandler handler) {
    	setResponsesHandler(handler);
    }
    
    public void setResponsesHandler(IDAVResponseHandler handler) {
    	init();
        myResponseHandler = handler;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) {
        if (element == DAVElement.RESPONSE) {
            myResponse = new DAVResponse();            
        }        
	}

	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.HREF) {
            if (parent == DAVElement.RESPONSE) {
                myResponse.setHref(cdata.toString());
            } else {
                myResponse.putPropertyValue(parent, cdata.toString());
            }
        } else if (element == DAVElement.RESPONSE) {
            myResponseHandler.handleDAVResponse(myResponse);
            myResponse = null;
        } else if (element == DAVElement.COLLECTION || element == DAVElement.BASELINE) {
            myResponse.putPropertyValue(parent, element);
        } else if (element == DAVElement.STATUS) {
            myResponse.setStatus(DAVStatus.parse(cdata.toString()));
        } else if (cdata != null && cdata.length() > 0 && myResponse != null) {
            myResponse.putPropertyValue(element, cdata.toString());
        }
	}

}