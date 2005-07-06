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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.util.Base64;
import org.tmatesoft.svn.util.TimeUtil;
import org.xml.sax.Attributes;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVGetLocksHandler extends BasicDAVHandler {
    
    public static StringBuffer generateGetLocksRequest(StringBuffer body) {
        body = body == null ? new StringBuffer() : body;

        body.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        body.append("<S:get-locks-report xmlns:S=\"svn:\" xmlns:D=\"DAV:\">");
        body.append("</S:get-locks-report>");
        
        return body;
    }
    
    private Collection myLocks;
    
    private String myPath;
    private String myToken;
    private String myComment;
    private String myOwner;
    private Date myExpirationDate;
    private Date myCreationDate;
    
    private boolean myIsBase64;
    
    public DAVGetLocksHandler() {
        myLocks = new ArrayList();
        init();
    }
    
    public SVNLock[] getLocks() {
        return (SVNLock[]) myLocks.toArray(new SVNLock[myLocks.size()]);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        myIsBase64 = false;
        if (attrs != null) {
            myIsBase64 = "base64".equals(attrs.getValue("encoding"));
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.SVN_LOCK) {
            if (myPath != null && myToken != null) {
                SVNLock lock = new SVNLock(myPath, myToken, myOwner, myComment, myCreationDate, myExpirationDate);
                myLocks.add(lock);
            }
            myPath = null;
            myOwner = null;
            myToken = null;
            myComment = null;
            myCreationDate = null;
            myExpirationDate = null;
        } else if (element == DAVElement.SVN_LOCK_PATH && cdata != null) {
            myPath = DAVUtil.xmlDecode(cdata.toString());
        } else if (element == DAVElement.SVN_LOCK_TOKEN && cdata != null) {
            myToken = DAVUtil.xmlDecode(cdata.toString());
        } else if (element == DAVElement.SVN_LOCK_OWNER && cdata != null) {
            myOwner = DAVUtil.xmlDecode(cdata.toString());
            if (myIsBase64) {
                myOwner = new String(Base64.base64ToByteArray(new StringBuffer(myComment), null));
            }
        } else if (element == DAVElement.SVN_LOCK_COMMENT && cdata != null) {
            myComment = DAVUtil.xmlDecode(cdata.toString());
            if (myIsBase64) {
                myComment = new String(Base64.base64ToByteArray(new StringBuffer(myComment), null));
            }
        } else if (element == DAVElement.SVN_LOCK_CREATION_DATE && cdata != null) {
            String dateStr = DAVUtil.xmlDecode(cdata.toString());
            myCreationDate = TimeUtil.parseDate(dateStr);
        } else if (element == DAVElement.SVN_LOCK_EXPIRATION_DATE && cdata != null) {
            String dateStr = DAVUtil.xmlDecode(cdata.toString());
            myExpirationDate = TimeUtil.parseDate(dateStr);
        }
        myIsBase64 = false;
    }

}
