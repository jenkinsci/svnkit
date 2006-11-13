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
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVMergeHandler extends BasicDAVHandler {
    
    public static StringBuffer generateMergeRequest(StringBuffer request, String path, String activityURL, Map locks) {
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
        if (locks != null) {
            request = generateLockDataRequest(request, path, null, locks);
        }
        request.append("</D:merge>");
        return request;

    }

    public static StringBuffer generateLockDataRequest(StringBuffer target, String root, String path, Map locks) {
        target = target == null ? new StringBuffer() : target;
        target.append("<S:lock-token-list xmlns:S=\"svn:\">");
        for (Iterator paths = locks.keySet().iterator(); paths.hasNext();) {
            String lockPath = (String) paths.next();
            if (path == null || isChildPath(path, lockPath)) {
                String token = (String) locks.get(lockPath);
                target.append("<S:lock><S:lock-path>");
                lockPath = getRelativePath(lockPath, root);
                
                target.append(SVNEncodingUtil.xmlEncodeCDATA(SVNEncodingUtil.uriDecode(lockPath)));
                target.append("</S:lock-path><S:lock-token>");
                target.append(token);
                target.append("</S:lock-token></S:lock>");
            }
        }
        target.append("</S:lock-token-list>");
        return target;
    }
    
    // both paths shouldn't end with '/'
    private static String getRelativePath(String path, String root) {
        if (path.length() <= root.length()) {
            return "";
        }
        return path.substring(root.length() + 1);
    }
    
    public static boolean hasChildPaths(String path, Map locks) {
        for (Iterator paths = locks.keySet().iterator(); paths.hasNext();) {
            String lockPath = (String) paths.next();
            if (isChildPath(path, lockPath)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isChildPath(String path, String childPath) {
        if (path.equals(childPath)) {
            return true;
        }
        return childPath.startsWith(path + "/");
    }
    
    private ISVNWorkspaceMediator myMediator;
    private Map myPathsMap;

    private static final DAVElement RESPONSE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "response");
    private static final DAVElement POST_COMMIT_ERROR = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "post-commit-err");

    private String myAuthor;
    private Date myCommitDate;
    private long myRevision;
    
    private String myRepositoryPath;
    private String myVersionPath;
    
    private DAVElement myResourceType;
    private SVNCommitInfo myCommitInfo;
    private SVNErrorMessage myPostCommitError;    
    
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
        if (element == POST_COMMIT_ERROR) {
            myPostCommitError = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, cdata.toString(), SVNErrorMessage.TYPE_WARNING);
        } else if (element == DAVElement.HREF) {
            if (parent == RESPONSE) {
                myRepositoryPath = cdata.toString();
                myRepositoryPath = SVNEncodingUtil.uriDecode(myRepositoryPath);
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
                myCommitInfo = new SVNCommitInfo(myRevision, myAuthor, myCommitDate, myPostCommitError);
            } else {
                String reposPath = SVNEncodingUtil.uriEncode(myRepositoryPath);
                String path = (String) myPathsMap.get(reposPath);
                if (path != null && myMediator != null) {
                    myMediator.setWorkspaceProperty(SVNEncodingUtil.uriDecode(path), "svn:wc:ra_dav:version-url", myVersionPath);
                } 
            }
        } else if (element == DAVElement.CREATION_DATE) {
            myCommitDate = SVNTimeUtil.parseDate(cdata.toString());
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME) {
            myAuthor = cdata.toString();
        } else if (element == DAVElement.VERSION_NAME) {
            myRevision = Long.parseLong(cdata.toString());
        } else if (parent == DAVElement.PROPSTAT && element == DAVElement.STATUS) {
            // should be 200
        }
    }

}
