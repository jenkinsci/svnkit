/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVGetLockHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVGetLocksHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVOptionsHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.IMeasurable;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.xml.sax.helpers.DefaultHandler;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVConnection {
    
    private IHTTPConnection myHttpConnection;
    private String myActivityCollectionURL;
    private Map myLocks;
    private boolean myKeepLocks;
    private IHTTPConnectionFactory myConnectionFactory;
    private SVNRepository myRepository;
    
    public DAVConnection(IHTTPConnectionFactory connectionFactory, SVNRepository repository) {
        myRepository = repository;
        myConnectionFactory = connectionFactory;
    }
    
    public SVNURL getLocation() {
        return myRepository.getLocation();
    }
    
    public void updateLocation() {
        myActivityCollectionURL = null;
    }

    public void open(DAVRepository repository) throws SVNException {
        if (myHttpConnection == null) {
        	myHttpConnection = myConnectionFactory.createHTTPConnection(repository);
        }
    }

    public void fetchRepositoryRoot(DAVRepository repository) throws SVNException {
        if (!repository.hasRepositoryRoot()) {
            String rootPath = repository.getLocation().getURIEncodedPath();
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, repository, rootPath, -1, false, false, null);
            // remove relative part from the path
            rootPath = rootPath.substring(0, rootPath.length() - info.baselinePath.length());
            SVNURL location = repository.getLocation();
            SVNURL url = location.setPath(rootPath, true);
            repository.setRepositoryRoot(url);
        }
    }

    public void fetchRepositoryUUID(DAVRepository repository) throws SVNException {
        if (!repository.hasRepositoryUUID()) {
            DAVUtil.findStartingProperties(this, repository, repository.getLocation().getURIEncodedPath());
            if (!repository.hasRepositoryUUID()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NO_REPOS_UUID, "Please upgrade to server 0.19 or later");
                SVNErrorManager.error(err);
            }
        }
    }    
    
    public HTTPStatus doPropfind(String path, Map header, StringBuffer body, DefaultHandler handler) throws SVNException {
        return myHttpConnection.request("PROPFIND", path, header, body, -1, 0, null, handler);
    }
    
    public SVNLock doGetLock(String path, DAVRepository repos) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, repos, path, -1, false, true, null);
        StringBuffer body = DAVGetLockHandler.generateGetLockRequest(null);
        DAVGetLockHandler handler = new DAVGetLockHandler();
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Failed to fetch lock information");
        HTTPStatus rc = myHttpConnection.request("PROPFIND", path, null, body, 200, 207, null, handler, context);
        
        String id = handler.getID();
        if (id == null) {
            return null;
        }
        String comment = handler.getComment();
        String owner = (String) rc.getHeader().get("X-SVN-Lock-Owner");
        String created = (String) rc.getHeader().get("X-SVN-Creation-Date");
        Date createdDate = created != null ? SVNTimeUtil.parseDate(created) : null;
        path = SVNEncodingUtil.uriDecode(info.baselinePath);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return new SVNLock(path, id, owner, comment, createdDate, null);
    }

    public SVNLock[] doGetLocks(String path) throws SVNException {
        DAVGetLocksHandler handler = new DAVGetLocksHandler();
        HTTPStatus status = null;
        try {
            status = doReport(path, DAVGetLocksHandler.generateGetLocksRequest(null), handler);
        } catch (SVNException e) {
            SVNDebugLog.logInfo("error message: " + e.getErrorMessage());
            SVNDebugLog.logInfo("error code: " + e.getErrorMessage().getErrorCode());
            if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.UNSUPPORTED_FEATURE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
                SVNErrorManager.error(err, e.getErrorMessage());
            } else if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                return new SVNLock[0];
            }
            throw e;
        }

        if (status.getCode() == 501) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
            SVNErrorManager.error(err, status.getError());
        } else if (status.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return new SVNLock[0];
        } else if (status.getError() != null && status.getError().getErrorCode() == SVNErrorCode.UNSUPPORTED_FEATURE) { 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
            SVNErrorManager.error(err, status.getError());
        } else if (status.getError() != null) {
            SVNErrorManager.error(status.getError());
        }
        return handler.getLocks();
    }

    public SVNLock doLock(String path, DAVRepository repos, String comment, boolean force, long revision) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, repos, path, -1, false, true, null);

        StringBuffer body = DAVGetLockHandler.generateSetLockRequest(null, comment);
        Map header = null;
        if (revision >= 0) {
            header = new HashMap();
            header.put("X-SVN-Version-Name", Long.toString(revision));
        }
        if (force) {
            if (header == null) {
                header = new HashMap();
            }
            header.put("X-SVN-Options", "lock-steal");
        }
        DAVGetLockHandler handler = new DAVGetLockHandler();
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Lock request failed");
        HTTPStatus status = myHttpConnection.request("LOCK", path, header, body, -1, 0, null, handler, context);
        if (status.getError() != null) {
            SVNErrorManager.error(status.getError());
        }
        if (status != null) {
            String userName = myHttpConnection.getLastValidCredentials() != null ? myHttpConnection.getLastValidCredentials().getUserName() : null; 
            String created = (String) status.getHeader().get("X-SVN-Creation-Date");
            if (userName == null || created == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Incomplete lock data returned");
                SVNErrorManager.error(err);
            }
            Date createdDate = created != null ? SVNTimeUtil.parseDate(created) : null;            
            return new SVNLock(info.baselinePath, handler.getID(), userName, comment, createdDate, null);
        }
        return null;
    }

    public void doUnlock(String path, DAVRepository repos, String id, boolean force) throws SVNException {
        if (id == null) {
            SVNLock lock = doGetLock(path, repos);
            if (lock != null) {
                id = lock.getID();
            } 
            if (id == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_LOCKED, "''{0}'' is not locked in the repository", path);
                SVNErrorManager.error(err);
            }
        }
        Map header = new HashMap();
        header.put("Lock-Token", "<" + id + ">");
        if (force) {
            header.put("X-SVN-Options", "lock-break");
        }
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unlock request failed");
        myHttpConnection.request("UNLOCK", path, header, (StringBuffer) null, 204, 0, null, null, context);
    }

	public void doGet(String path, OutputStream os) throws SVNException {
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "GET request failed for ''{0}''", path);
		myHttpConnection.request("GET", path, null, (StringBuffer) null, 200, 226, os, null, context);
    }
	
    public HTTPStatus doReport(String path, StringBuffer requestBody, DefaultHandler handler) throws SVNException {
        return doReport(path, requestBody, handler, false);
    }

    public HTTPStatus doReport(String path, StringBuffer requestBody, DefaultHandler handler, boolean spool) throws SVNException {
        myHttpConnection.setSpoolResponse(spool);
        try {
            return myHttpConnection.request("REPORT", path, null, requestBody, -1, 0, null, handler);
        } finally {
            myHttpConnection.setSpoolResponse(false);
        }
	}

    public void doProppatch(String repositoryPath, String path, StringBuffer requestBody, DefaultHandler handler, SVNErrorMessage context) throws SVNException {
        Map header = null;
        if (myLocks != null && repositoryPath != null && myLocks.containsKey(repositoryPath)) {
            header = new HashMap();
            header.put("If", "(<" + myLocks.get(repositoryPath) + ">)");
        }
        try {
            myHttpConnection.request("PROPPATCH", path, header, requestBody, 200, 207, null, handler, context);
        } catch (SVNException e) {
            if (context == null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPPATCH_FAILED, "At least one property change failed; repository is unchanged");
                SVNErrorManager.error(err);
            }
            // handler error.
            throw e;
        }
    }
    
    public String doMakeActivity() throws SVNException {
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        String url = getActivityCollectionURL(locationPath, false) + generateUUID();
        HTTPStatus status = myHttpConnection.request("MKACTIVITY", url, null, (StringBuffer) null, 201, 404, null, null);
        if (status.getCode() == 404) {
            // refetch
            url = getActivityCollectionURL(locationPath, true) + generateUUID();
            myHttpConnection.request("MKACTIVITY", url, null, (StringBuffer) null, 201, 0, null, null);
        }
        return url;
    }
    
    public HTTPStatus doDelete(String path) throws SVNException {
        return myHttpConnection.request("DELETE", path, null, (StringBuffer) null, 404, 204, null, null);
    }

    public HTTPStatus doDelete(String repositoryPath, String path, long revision) throws SVNException {
        Map header = new HashMap();
        if (revision >= 0) {
            header.put("X-SVN-Version-Name", Long.toString(revision));
        }
        header.put("Depth", "infinity");
        StringBuffer request = null;
        if (myLocks != null && DAVMergeHandler.hasChildPaths(repositoryPath, myLocks)) {
            if (myLocks.containsKey(repositoryPath)) {
                header.put("If", "<" + repositoryPath + "> (<" + myLocks.get(repositoryPath) + ">)");
            }
            if (myKeepLocks) {
                header.put("X-SVN-Options", "keep-locks");
            }
            request = new StringBuffer();
            request.append("<?xml version=\"1.0\" encoding=\"utf-8\"?> ");
            String locationPath = getLocation().getPath();
            locationPath = SVNEncodingUtil.uriEncode(locationPath);
            request = DAVMergeHandler.generateLockDataRequest(request, locationPath, repositoryPath, myLocks);
        }
        return myHttpConnection.request("DELETE", path, header, request, 204, 404, null, null);
    }
    
    public HTTPStatus doMakeCollection(String path) throws SVNException {
        return myHttpConnection.request("MKCOL", path, null, (StringBuffer) null, 201, 0, null, null);
    }
    
    public HTTPStatus doPutDiff(String repositoryPath, String path, InputStream data) throws SVNException {        
        Map headers = new HashMap();
        headers.put("Content-Type", "application/vnd.svn-svndiff");
        if (!(data instanceof ByteArrayInputStream || data instanceof IMeasurable)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                while(true) {
                    int b = data.read();
                    if (b < 0) {
                        break;
                    }
                    bos.write(b);
                }
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(err, e);
            } finally {
                try {
                    data.close();
                } catch (IOException e) {
                    //
                }
            }
            data = new ByteArrayInputStream(bos.toByteArray());
        } 
        if (myLocks != null && myLocks.containsKey(repositoryPath)) {
            headers.put("If", "<" + repositoryPath + "> (<" + myLocks.get(repositoryPath) + ">)");
        }
        return myHttpConnection.request("PUT", path, headers, data, 201, 204, null, null);
    }
    
    public HTTPStatus doMerge(String activityURL, boolean response, DefaultHandler handler) throws SVNException {
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        StringBuffer request = DAVMergeHandler.generateMergeRequest(null, locationPath, activityURL, myLocks);
        Map header = null;
        if (!response || (myLocks != null && !myKeepLocks)) {
            header = new HashMap();
            String value = "";
            if (!response) {
                value += "no-merge-response";
            }
            if (myLocks != null && !myKeepLocks) {
                value += " release-locks";
            }
            header.put("X-SVN-Options", value);
        }
        return myHttpConnection.request("MERGE", getLocation().getURIEncodedPath(), header, request, -1, 0, null, handler);
    }
    
    public HTTPStatus doCheckout(String activityPath, String repositoryPath, String path, boolean allow404) throws SVNException {
        StringBuffer request = new StringBuffer();
        request.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        request.append("<D:checkout xmlns:D=\"DAV:\">");
        request.append("<D:activity-set>");
        request.append("<D:href>");
        request.append(activityPath);
        request.append("</D:href>");
        request.append("</D:activity-set></D:checkout>");
        Map header = null;
        if (myLocks != null && repositoryPath != null && myLocks.containsKey(repositoryPath)) {
            header = new HashMap();
            header.put("If", "(<" + myLocks.get(repositoryPath) + ">)");
        }
        HTTPStatus status = myHttpConnection.request("CHECKOUT", path, header, request, 201, allow404 ? 404 : 0, null, null);
        // update location to be a path!
        if (status.getHeader().containsKey("Location")) {
            SVNURL location = SVNURL.parseURIEncoded((String) status.getHeader().get("Location"));
            status.getHeader().put("Location", location.getURIEncodedPath());
        }
        return status;
    }

    public void doCopy(String src, String dst, int depth) throws SVNException {
        Map header = new HashMap();
        header.put("Destination", dst);
        header.put("Depth", depth > 0 ? "infinity" : "0");
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "COPY of {0}", src);
        HTTPStatus status = myHttpConnection.request("COPY", src, header, (StringBuffer) null, -1, 0, null, null, context);
        if (status.getCode() >= 300 && status.getError() != null) {
            SVNErrorMessage err = status.getError();
            SVNErrorManager.error(err);
        }        
    }

    public void close()  {
        if (myHttpConnection != null) {
            myHttpConnection.close();
            myHttpConnection = null;
            myLocks = null;
            myKeepLocks = false;
        }
    }
    
    private String getActivityCollectionURL(String path, boolean force) throws SVNException {
        if (!force && myActivityCollectionURL != null) {
            return myActivityCollectionURL;
        }
        DAVOptionsHandler handler = new DAVOptionsHandler();
        myHttpConnection.request("OPTIONS", path, null, DAVOptionsHandler.OPTIONS_REQUEST, -1, 0, null, handler);
        myActivityCollectionURL = handler.getActivityCollectionURL();
        if (myActivityCollectionURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_OPTIONS_REQ_FAILED, 
                    "The OPTIONS request did not include the requested activity-collection-set; this often means that the URL is not WebDAV-enabled");
            SVNErrorManager.error(err);
        }
        return myActivityCollectionURL;
    }
    
    private static String generateUUID() {
        long time = System.currentTimeMillis();
        String uuid = Long.toHexString(time);
        int zeroes = 16 - uuid.length();
        for(int i = 0; i < zeroes; i++) {
            uuid = "0" + uuid;
        }
        return uuid;
    }

    public void setLocks(Map locks, boolean keepLocks) {
        myLocks = locks;
        myKeepLocks = keepLocks;
    }

    public void clearAuthenticationCache() {
        if (myHttpConnection != null) {
            myHttpConnection.clearAuthenticationCache();
        }
    }
}