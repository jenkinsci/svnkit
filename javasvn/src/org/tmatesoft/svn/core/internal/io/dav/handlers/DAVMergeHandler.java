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

import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 */
public class DAVMergeHandler extends BasicDAVHandler {
    
    public static StringBuffer generateMergeRequest(StringBuffer request, String activityURL) {
        request = request == null ? new StringBuffer() : request;
        request.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        request.append("<D:merge xmlns:D=\"DAV:\">");
        request.append("<D:source><D:href>");
        request.append(activityURL);
        request.append("</D:href></D:source>");
        request.append("<D:no-auto-merge/><D:no-checkout/>");
        request.append("<D:prop>");
        request.append("<D:checked-in/><D:version-name/><D:resourcetype/>");
        request.append("<D:creationdate/><D:creator-displayname/>");
        request.append("</D:prop>");
        request.append("</D:merge>");
        return request;

    }
    
    private ISVNWorkspaceMediator myMediator;
    private Map myPathsMap;

    private static final DAVElement RESPONSE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "response");

    private String myAuthor;
    private Date myCommitDate;
    private long myRevision;
    
    private String myRepositoryPath;
    private String myVersionPath;
    
    private DAVElement myResourceType;
    private SVNCommitInfo myCommitInfo;    
    
    public DAVMergeHandler(ISVNWorkspaceMediator mediator, Map pathsMap) {
        myMediator = mediator;
        myPathsMap = pathsMap;
        
        init();
    }
    
    public SVNCommitInfo getCommitInfo() {
        return myCommitInfo;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == RESPONSE) {
            myResourceType = null;
            myRepositoryPath = null;
            myVersionPath = null;

            myAuthor = null;
            myCommitDate = null;
            myRevision = -1;
        }
    }

    protected void endElement(DAVElement parent, DAVElement element,  StringBuffer cdata) throws SVNException {
        if (element == DAVElement.HREF) {
            if (parent == RESPONSE) {
                myRepositoryPath = cdata.toString();
                myRepositoryPath = PathUtil.decode(myRepositoryPath);
            } else if (parent == DAVElement.CHECKED_IN) {
                myVersionPath = cdata.toString();
            } 
        } else if (parent == DAVElement.RESOURCE_TYPE && element == DAVElement.BASELINE) {
            myResourceType = element;
        } else if (parent == DAVElement.RESOURCE_TYPE && element == DAVElement.COLLECTION) {
            myResourceType = element;
        } else if (element == RESPONSE) {
            // all resource info is collected, do something.
            if (myResourceType == DAVElement.BASELINE) {
                myCommitInfo = new SVNCommitInfo(myRevision, myAuthor, myCommitDate);
            } else {
                String path = (String) myPathsMap.get(myRepositoryPath);
                if (path != null && myMediator != null) {
                    myMediator.setWorkspaceProperty(path, "svn:wc:ra_dav:version-url", myVersionPath);
                } 
            }
        } else if (element == DAVElement.CREATION_DATE) {
            myCommitDate = TimeUtil.parseDate(cdata.toString());
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME) {
            myAuthor = cdata.toString();
        } else if (element == DAVElement.VERSION_NAME) {
            myRevision = Long.parseLong(cdata.toString());
        } else if (parent == DAVElement.PROPSTAT && element == DAVElement.STATUS) {
            // should be 200
        }
    }

}
