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

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVGetLockHandler extends BasicDAVHandler {

    public static StringBuffer generateGetLockRequest(StringBuffer body) {
        return DAVPropertiesHandler.generatePropertiesRequest(body, new DAVElement[] {DAVElement.LOCK_DISCOVERY});
    }

    public static StringBuffer generateSetLockRequest(StringBuffer body, String comment) {
        body = body == null ? new StringBuffer() : body;
        body.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        body.append("<lockinfo xmlns=\"DAV:\" >");
        body.append("<lockscope><exclusive/></lockscope>");
        body.append("<locktype><write/></locktype><owner>");
        comment = comment == null ? "" : SVNEncodingUtil.xmlEncodeAttr(comment);
        body.append(comment);
        body.append("</owner></lockinfo>");
        return body;
    }

    private boolean myIsHandlingToken;
    private String myID;
    private String myComment;
    private String myExpiration;

    public DAVGetLockHandler() {
        init();        
    }    
    public String getComment() {
        return myComment;
    }
    public String getExpiration() {
        return myExpiration;
    }
    public String getID() {
        return myID;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) {
        if (element == DAVElement.LOCK_TOKEN) {
            myIsHandlingToken = true;
        }
    }
    
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) {
        if (element == DAVElement.HREF && myIsHandlingToken && cdata != null) {
            myID = cdata.toString();
        } else if (element == DAVElement.LOCK_TOKEN) {
            myIsHandlingToken = false;
        } else if (element == DAVElement.LOCK_OWNER && cdata != null) {
            myComment = cdata.toString();
        } else if (element == DAVElement.LOCK_TIMEOUT && cdata != null) {
            myExpiration = cdata.toString();
        } 
    }}
