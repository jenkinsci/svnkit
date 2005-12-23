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

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVGetLockHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVGetLocksHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVOptionsHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVPropertiesHandler;
import org.tmatesoft.svn.core.internal.util.IMeasurable;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
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

    public void open(DAVRepository repository) {
        if (myHttpConnection == null) {
        	myHttpConnection = myConnectionFactory.createHTTPConnection(repository);
        }
    }
    
    public String getVCCPath(DAVRepository repository, String path) throws SVNException {
        DAVResponse responce = findStartingProperties(repository, path);
        String vcc = null;
        if (responce != null) {
            vcc = (String) responce.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        }
        if (vcc == null) {
            SVNErrorManager.error("svn: The VCC property was not found on the resource");
        }
        return vcc;
    }
    
    private DAVResponse findStartingProperties(DAVRepository repository, String fullPath) throws SVNException {
        DAVResponse props = null;
        String originalPath = fullPath;
        while(true) {
            props = getStartingProperties(fullPath);
            if (props != null) {
                if (props.getPropertyValue(DAVElement.REPOSITORY_UUID) != null) {
                    repository.setRepositoryUUID((String) props.getPropertyValue(DAVElement.REPOSITORY_UUID));
                }
                break;
            }
            int length = fullPath.length();
            fullPath = SVNPathUtil.removeTail(fullPath);
            if (length == fullPath.length()) {
                SVNErrorManager.error("svn: The path '" + originalPath + "' is not part of repository");
            }
        }
        return props;
    }

    private DAVResponse getStartingProperties(String fullPath) throws SVNException {
        final DAVResponse[] result = new DAVResponse[1];
        doPropfind(fullPath, 0, null, DAVElement.STARTING_PROPERTIES, new IDAVResponseHandler() {
            public void handleDAVResponse(DAVResponse response) {
                result[0] = response;
            }
        }, new int[] {200, 207, 404});
        return result[0];
    }
    
    public void fetchRepositoryRoot(DAVRepository repository) throws SVNException {
        if (!repository.hasRepositoryRoot()) {
            String rootPath = repository.getLocation().getURIEncodedPath();
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, rootPath, -1, false, false, null);
            // remove relative part from the path
            rootPath = rootPath.substring(0, rootPath.length() - info.baselinePath.length());
            SVNURL location = repository.getLocation();
            SVNURL url = location.setPath(rootPath, true);
            repository.setRepositoryRoot(url);
        }
    }

    public void fetchRepositoryUUID(DAVRepository repository) throws SVNException {
        if (!repository.hasRepositoryUUID()) {
            findStartingProperties(repository, repository.getLocation().getURIEncodedPath());
        }
    }    

    public void doPropfind(String path, int depth, String label, DAVElement[] properties, IDAVResponseHandler handler) throws SVNException {
    	doPropfind(path, depth, label, properties, handler, new int[] {200, 207});
    }

    public void doPropfind(String path, int depth, String label, DAVElement[] properties, IDAVResponseHandler handler, int[] okCodes) throws SVNException {
        StringBuffer body = DAVPropertiesHandler.generatePropertiesRequest(null, properties);
        myHttpConnection.request("PROPFIND", path, depth, label, body, new DAVPropertiesHandler(handler), okCodes);
    }
    
    public SVNLock doGetLock(String path) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, path, -1, false, true, null);
        StringBuffer body = DAVGetLockHandler.generateGetLockRequest(null);
        DAVGetLockHandler handler = new DAVGetLockHandler();
        DAVStatus rc = myHttpConnection.request("PROPFIND", path, 0, null, body, handler, new int[] {200, 207});
        
        String id = handler.getID();
        if (id == null) {
            return null;
        }
        String comment = handler.getComment();
        String owner = (String) rc.getResponseHeader().get("X-SVN-Lock-Owner");
        String created = (String) rc.getResponseHeader().get("X-SVN-Creation-Date");
        Date createdDate = created != null ? SVNTimeUtil.parseDate(created) : null;
        path = SVNEncodingUtil.uriDecode(info.baselinePath);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return new SVNLock(path, id, owner, comment, createdDate, null);
    }

    public SVNLock[] doGetLocks(String path) throws SVNException {
        DAVGetLocksHandler handler = new DAVGetLocksHandler();
        doReport(path, DAVGetLocksHandler.generateGetLocksRequest(null), handler);
        return handler.getLocks();
    }

    public SVNLock doLock(String path, String comment, boolean force, long revision) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, path, -1, false, true, null);

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
        DAVStatus status = myHttpConnection.request("LOCK", path, header, body, handler, null);
        if (status != null) {
            String userName = myHttpConnection.getLastValidCredentials() != null ? myHttpConnection.getLastValidCredentials().getUserName() : null; 
            String created = (String) status.getResponseHeader().get("X-SVN-Creation-Date");
            Date createdDate = created != null ? SVNTimeUtil.parseDate(created) : null;            
            return new SVNLock(info.baselinePath, handler.getID(), userName, comment, createdDate, null);
        }
        return null;
    }

    public void doUnlock(String path, String id, boolean force) throws SVNException {
        if (id == null) {
            SVNLock lock = doGetLock(path);
            if (lock != null) {
                id = lock.getID();
            } 
            if (id == null) {
                throw new SVNException("repository path '" + path + "' is not locked.");
            }
        }
        Map header = new HashMap();
        header.put("Lock-Token", "<" + id + ">");
        if (force) {
            header.put("X-SVN-Options", "lock-break");
        }
        myHttpConnection.request("UNLOCK", path, header, (StringBuffer) null, null, new int[] {204});
    }

	public void doGet(String path, OutputStream os) throws SVNException {
		myHttpConnection.request("GET", path, 0, null, new StringBuffer(), os, null);
    }
	
	public void doReport(String path, StringBuffer requestBody, DefaultHandler handler) throws SVNException {
		myHttpConnection.request("REPORT", path, 0, null, requestBody, handler, new int[] {200, 207});
	}

    public void doProppatch(String repositoryPath, String path, StringBuffer requestBody, DefaultHandler handler) throws SVNException {
        Map header = null;
        if (myLocks != null && repositoryPath != null && myLocks.containsKey(repositoryPath)) {
            header = new HashMap();
            header.put("If", "(<" + myLocks.get(repositoryPath) + ">)");
        }
        myHttpConnection.request("PROPPATCH", path, header, requestBody, handler, new int[] {200, 207});

    }
    
    public String doMakeActivity() throws SVNException {
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        String url = getActivityCollectionURL(locationPath, false) + generateUUID();
        DAVStatus status = myHttpConnection.request("MKACTIVITY", url, 0, null, null, (OutputStream) null, new int[] {201, 404});
        if (status.getResponseCode() == 404) {
            // refetch
            url = getActivityCollectionURL(locationPath, true) + generateUUID();
            status = myHttpConnection.request("MKACTIVITY", url, 0, null, null, (OutputStream) null, new int[] {201});
        }
        return url;
    }
    
    public DAVStatus doDelete(String path) throws SVNException {
        return myHttpConnection.request("DELETE", path, null, (StringBuffer) null, null, null);
    }

    public DAVStatus doDelete(String repositoryPath, String path, long revision) throws SVNException {
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
        return myHttpConnection.request("DELETE", path, header, request, null, null);
    }
    
    public DAVStatus doMakeCollection(String path) throws SVNException {
        return myHttpConnection.request("MKCOL", path, null, (StringBuffer) null, null, null);
    }
    
    public DAVStatus doPutDiff(String repositoryPath, String path, InputStream data) throws SVNException {
        
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
                throw new SVNException(e);
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
        DAVStatus status = myHttpConnection.request("PUT", path, headers, data, null, null);
        return status;
    }
    
    public DAVStatus doMerge(String activityURL, boolean response, DefaultHandler handler) throws SVNException {
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        StringBuffer request = DAVMergeHandler.generateMergeRequest(null, locationPath, activityURL, myLocks);
        Map header = null;
        if (!response || (myLocks != null && !myKeepLocks)) {
            header = new HashMap();
            String value = "";
            if (!response) {
                value += "no-merge-response ";
            }
            if (myLocks != null && !myKeepLocks) {
                value += "release-locks";
            }
            value = value.trim();
            header.put("X-SVN-Options", value);
        }
        return myHttpConnection.request("MERGE", getLocation().getURIEncodedPath(), header, request, handler, null);
    }
    
    public DAVStatus doCheckout(String activityPath, String repositoryPath, String path) throws SVNException {
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
        DAVStatus status = myHttpConnection.request("CHECKOUT", path, header, request, null, null);
        // update location to be a path!
        if (status.getResponseHeader().containsKey("Location")) {
            String location = (String) status.getResponseHeader().get("Location");
            location = location.substring(location.indexOf("://") + "://".length());
            location = location.substring(location.indexOf("/"));
            status.getResponseHeader().put("Location", location);
        }
        return status;
    }

    public DAVStatus doCopy(String src, String dst) throws SVNException {
        Map header = new HashMap();
        header.put("Destination", dst);
        header.put("Depth", "infinity");
        return myHttpConnection.request("COPY", src, header, (StringBuffer) null, null, null);
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
        myHttpConnection.request("OPTIONS", path, 0, null, DAVOptionsHandler.OPTIONS_REQUEST, handler, null);
        myActivityCollectionURL = handler.getActivityCollectionURL();

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