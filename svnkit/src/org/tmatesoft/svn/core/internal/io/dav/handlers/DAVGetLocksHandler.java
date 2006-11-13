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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.xml.sax.Attributes;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVGetLocksHandler extends BasicDAVHandler {
    
    private static final String LOCK_COMMENT_SUFFIX = "</ns0:owner>";
    private static final String LOCK_COMMENT_PREFIX = "<ns0:owner xmlns:ns0=\"DAV:\">";
    private static final String EMPTY_LOCK_COMMENT = "<ns0:owner xmlns:ns0=\"DAV:\"/>";

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
            myPath = cdata.toString();
        } else if (element == DAVElement.SVN_LOCK_TOKEN && cdata != null) {
            myToken = cdata.toString();
        } else if (element == DAVElement.SVN_LOCK_OWNER && cdata != null) {
            myOwner = cdata.toString();
            if (myIsBase64) {
                byte[] buffer = allocateBuffer(myComment.trim().length());
                int length = SVNBase64.base64ToByteArray(new StringBuffer(myComment.trim()), buffer);
                try {
                    myOwner = new String(buffer, 0, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    myComment = new String(buffer, 0, length);
                }
            }
        } else if (element == DAVElement.SVN_LOCK_COMMENT && cdata != null) {
            myComment = cdata.toString();            
            if (myComment != null && myComment.trim().startsWith(LOCK_COMMENT_PREFIX) && myComment.trim().endsWith(LOCK_COMMENT_SUFFIX)) {
                myComment = myComment.trim().substring(LOCK_COMMENT_PREFIX.length(), myComment.trim().length() - LOCK_COMMENT_SUFFIX.length());
            } else if (myComment.trim().equals(EMPTY_LOCK_COMMENT)) {
                myComment = "";
            }
            if (myIsBase64) {
                byte[] buffer = allocateBuffer(myComment.trim().length());
                int length = SVNBase64.base64ToByteArray(new StringBuffer(myComment.trim()), buffer);
                try {
                    myComment = new String(buffer, 0, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    myComment = new String(buffer, 0, length);
                }
            }
        } else if (element == DAVElement.SVN_LOCK_CREATION_DATE && cdata != null) {
            myCreationDate = SVNTimeUtil.parseDate(cdata.toString());
        } else if (element == DAVElement.SVN_LOCK_EXPIRATION_DATE && cdata != null) {
            myExpirationDate = SVNTimeUtil.parseDate(cdata.toString());
        }
        myIsBase64 = false;
    }

}
