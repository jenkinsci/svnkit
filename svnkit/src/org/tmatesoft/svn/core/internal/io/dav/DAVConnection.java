/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVGetLocksHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLockHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVOptionsHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.helpers.DefaultHandler;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DAVConnection {

    protected static final String DAV_CAPABILITY_YES = "yes";
    protected static final String DAV_CAPABILITY_NO = "no";
    protected static final String DAV_CAPABILITY_SERVER_YES = "server-yes";

    private IHTTPConnection myHttpConnection;
    private String myActivityCollectionURL;
    private SVNRepository myRepository;
    private boolean myIsSpoolReport;

    protected boolean myKeepLocks;
    protected Map myLocks;
    protected Map myCapabilities;
    protected IHTTPConnectionFactory myConnectionFactory;

    public DAVConnection(IHTTPConnectionFactory connectionFactory, SVNRepository repository) {
        myRepository = repository;
        myConnectionFactory = connectionFactory;
    }
    
    public boolean isReportResponseSpooled() {
        return myIsSpoolReport;
    }
    
    public void setReportResponseSpooled(boolean spool) {
        myIsSpoolReport = spool;
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
            exchangeCapabilities();
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
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
        }
    }    
    
    public HTTPStatus doPropfind(String path, HTTPHeader header, StringBuffer body, DefaultHandler handler) throws SVNException {
        IHTTPConnection httpConnection = getConnection();
        return httpConnection.request("PROPFIND", path, header, body, -1, 0, null, handler);
    }
    
    public SVNLock doGetLock(String path, DAVRepository repos) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, repos, path, -1, false, true, null);
        StringBuffer body = DAVLockHandler.generateGetLockRequest(null);
        DAVLockHandler handler = new DAVLockHandler();
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Failed to fetch lock information");
        IHTTPConnection httpConnection = getConnection();
        HTTPStatus rc = httpConnection.request("PROPFIND", path, null, body, 200, 207, null, handler, context);
        
        String id = handler.getID();
        if (id == null) {
            return null;
        }
        String comment = handler.getComment();
        String owner = rc.getHeader().getFirstHeaderValue(HTTPHeader.LOCK_OWNER_HEADER);
        String created = rc.getHeader().getFirstHeaderValue(HTTPHeader.CREATION_DATE_HEADER);
        Date createdDate = created != null ? SVNDate.parseDate(created) : null;
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
            if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.UNSUPPORTED_FEATURE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
                SVNErrorManager.error(err, e.getErrorMessage(), SVNLogType.NETWORK);
            } else if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                return new SVNLock[0];
            }
            throw e;
        }

        if (status.getCode() == 501) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
            SVNErrorManager.error(err, status.getError(), SVNLogType.NETWORK);
        } else if (status.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return new SVNLock[0];
        } else if (status.getError() != null && status.getError().getErrorCode() == SVNErrorCode.UNSUPPORTED_FEATURE) { 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "Server does not support locking features");
            SVNErrorManager.error(err, status.getError(), SVNLogType.NETWORK);
        } else if (status.getError() != null) {
            SVNErrorManager.error(status.getError(), SVNLogType.NETWORK);
        }
        return handler.getLocks();
    }

    public SVNLock doLock(String path, DAVRepository repos, String comment, boolean force, long revision) throws SVNException {
        DAVBaselineInfo info = DAVUtil.getBaselineInfo(this, repos, path, -1, false, true, null);

        StringBuffer body = DAVLockHandler.generateSetLockRequest(null, comment);
        HTTPHeader header = new HTTPHeader();
        header.setHeaderValue(HTTPHeader.DEPTH_HEADER, "0");
        header.setHeaderValue(HTTPHeader.TIMEOUT_HEADER, "Infinite");
        header.setHeaderValue(HTTPHeader.CONTENT_TYPE_HEADER, "text/xml; charset=\"utf-8\"");

        if (revision >= 0) {
            header.setHeaderValue(HTTPHeader.SVN_VERSION_NAME_HEADER, Long.toString(revision));
        }
        if (force) {
            header.setHeaderValue(HTTPHeader.SVN_OPTIONS_HEADER, "lock-steal");
        }
        DAVLockHandler handler = new DAVLockHandler();
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Lock request failed");
        IHTTPConnection httpConnection = getConnection();
        HTTPStatus status = httpConnection.request("LOCK", path, header, body, -1, 0, null, handler, context);
        if (status != null) {
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError(), SVNLogType.NETWORK);
            }
            String userName = httpConnection.getLastValidCredentials() != null ? httpConnection.getLastValidCredentials().getUserName() : null; 
            String created = status.getHeader().getFirstHeaderValue(HTTPHeader.CREATION_DATE_HEADER);
            if (userName == null || created == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Incomplete lock data returned");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            Date createdDate = created != null ? SVNDate.parseDate(created) : null;
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
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
        }
        HTTPHeader header = new HTTPHeader();
        header.setHeaderValue(HTTPHeader.LOCK_TOKEN_HEADER, "<" + id + ">");
        if (force) {
            header.setHeaderValue(HTTPHeader.SVN_OPTIONS_HEADER, "lock-break");
        }
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unlock request failed");
        IHTTPConnection httpConnection = getConnection();
        httpConnection.request("UNLOCK", path, header, (StringBuffer) null, 204, 0, null, null, context);
    }

	public void doGet(String path, OutputStream os) throws SVNException {
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "GET request failed for ''{0}''", path);
        IHTTPConnection httpConnection = getConnection();
        httpConnection.request("GET", path, null, (StringBuffer) null, 200, 226, os, null, context);
    }

	public void doGet(String path, String deltaBaseVersionURL, OutputStream os) throws SVNException {
        HTTPHeader header = null;
        if (deltaBaseVersionURL != null) {
            header = new HTTPHeader();
            header.addHeaderValue(HTTPHeader.SVN_DELTA_BASE_HEADER, deltaBaseVersionURL);
        }

	    SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, 
	            "GET request failed for ''{0}''", path);
	    IHTTPConnection httpConnection = getConnection();
	    httpConnection.request("GET", path, header, (StringBuffer) null, 200, 226, os, null, context);
    }
	
    public HTTPStatus doReport(String path, StringBuffer requestBody, DefaultHandler handler) throws SVNException {
        return doReport(path, requestBody, handler, false);
    }

    public HTTPStatus doReport(String path, StringBuffer requestBody, DefaultHandler handler, boolean spool) throws SVNException {
        IHTTPConnection httpConnection = getConnection();
        httpConnection.setSpoolResponse(spool || isReportResponseSpooled());
        try {
            HTTPHeader header = new HTTPHeader();
            header.addHeaderValue(HTTPHeader.ACCEPT_ENCODING_HEADER, "svndiff1;q=0.9,svndiff;q=0.8");
            return httpConnection.request("REPORT", path, header, requestBody, -1, 0, null, handler);
        } finally {
            httpConnection.setSpoolResponse(false);
        }
	}

    public void doProppatch(String repositoryPath, String path, StringBuffer requestBody, DefaultHandler handler, SVNErrorMessage context) throws SVNException {
        HTTPHeader header = null;
        if (myLocks != null && repositoryPath != null && myLocks.containsKey(repositoryPath)) {
            header = new HTTPHeader();
            header.setHeaderValue(HTTPHeader.IF_HEADER, "(<" + myLocks.get(repositoryPath) + ">)");
        }
        
        IHTTPConnection httpConnection = getConnection();
        try {
            httpConnection.request("PROPPATCH", path, header, requestBody, 200, 207, null, handler, context);
        } catch (SVNException e) {
            if (context == null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPPATCH_FAILED, "At least one property change failed; repository is unchanged");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            // handler error.
            throw e;
        }
    }
    
    public String doMakeActivity(ISVNWorkspaceMediator mediator) throws SVNException {
        String url = null;
        if (mediator != null) {
            SVNPropertyValue property = mediator.getWorkspaceProperty("", SVNProperty.ACTIVITY_URL);
            if (property != null && property.isString()) {
                url = property.getString();
            }
        }
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        if (url == null) {
            url = getActivityCollectionURL(locationPath, false);
        }
        
        IHTTPConnection httpConnection = getConnection();
        String activityURL = SVNPathUtil.append(url, generateUUID());
        HTTPStatus status = httpConnection.request("MKACTIVITY", activityURL, null, (StringBuffer) null, 201, 404, null, null);
        
        if (status.getCode() == 404) {
            url = getActivityCollectionURL(locationPath, true);
            activityURL = SVNPathUtil.append(url, generateUUID());
            status = httpConnection.request("MKACTIVITY", activityURL, null, (StringBuffer) null, 201, 0, null, null);
        }
        
        if (url != null && mediator != null) {
            mediator.setWorkspaceProperty("", SVNProperty.ACTIVITY_URL, SVNPropertyValue.create(url));
        }
        return activityURL;
    }
    
    public HTTPStatus doDelete(String path) throws SVNException {
        IHTTPConnection httpConnection = getConnection();
        return httpConnection.request("DELETE", path, null, (StringBuffer) null, 404, 204, null, null);
    }

    public HTTPStatus doDelete(String repositoryPath, String path, long revision) throws SVNException {
        HTTPHeader header = new HTTPHeader();
        if (revision >= 0) {
            header.setHeaderValue(HTTPHeader.SVN_VERSION_NAME_HEADER, Long.toString(revision));
        }
        header.setHeaderValue(HTTPHeader.DEPTH_HEADER, "infinity");
        StringBuffer request = null;
        if (myLocks != null) {
            if (myLocks.containsKey(repositoryPath)) {
                header.setHeaderValue(HTTPHeader.IF_HEADER, "<" + repositoryPath + "> (<" + myLocks.get(repositoryPath) + ">)");
            }
            if (myKeepLocks) {
                header.setHeaderValue(HTTPHeader.SVN_OPTIONS_HEADER, "keep-locks");
            }
            request = new StringBuffer();
            SVNXMLUtil.addXMLHeader(request);
            String locationPath = getLocation().getPath();
            locationPath = SVNEncodingUtil.uriEncode(locationPath);
            request = DAVMergeHandler.generateLockDataRequest(request, locationPath, repositoryPath, myLocks);
        }
        
        IHTTPConnection httpConnection = getConnection();
        HTTPStatus status = httpConnection.request("DELETE", path, header, request, 204, 0, null, null);
        if (status.getError() != null) {
            SVNErrorCode errCode = status.getError().getErrorCode();
            if (errCode == SVNErrorCode.FS_BAD_LOCK_TOKEN || errCode == SVNErrorCode.FS_NO_LOCK_TOKEN || 
                    errCode == SVNErrorCode.FS_LOCK_OWNER_MISMATCH || errCode == SVNErrorCode.FS_PATH_ALREADY_LOCKED) {
                Map childTokens = null;
                if (myLocks != null) {
                    childTokens = new SVNHashMap();
                    for (Iterator locksIter = myLocks.keySet().iterator(); locksIter.hasNext();) {
                        String lockPath = (String) locksIter.next();
                        if (lockPath.startsWith(path)) {
                            childTokens.put(lockPath, myLocks.get(lockPath));
                        }
                    }
                }
                
                if (childTokens == null || childTokens.isEmpty()) {
                    SVNErrorManager.error(status.getError(), SVNLogType.NETWORK);
                } else {
                    status.setError(null);
                }
                
                String token = myLocks != null ? (String) myLocks.get(path) : null;
                if (token != null) {
                    childTokens.put(path, token);
                }
                
                request = new StringBuffer();
                String locationPath = getLocation().getPath();
                locationPath = SVNEncodingUtil.uriEncode(locationPath);
                
                request = DAVMergeHandler.generateLockDataRequest(request, locationPath, repositoryPath, childTokens);
                HTTPStatus status2 = httpConnection.request("DELETE", path, header, request, 204, 404, null, null);
                if (status2.getError() != null) {
                    SVNErrorManager.error(status2.getError(), SVNLogType.NETWORK);
                }
                return status2;
            } 
            SVNErrorManager.error(status.getError(), SVNLogType.NETWORK);    
        }
        
        return status;
    }

    public HTTPStatus doMakeCollection(String path) throws SVNException {
        IHTTPConnection httpConnection = getConnection();
        return httpConnection.request("MKCOL", path, null, (StringBuffer) null, 201, 0, null, null);
    }
    
    public HTTPStatus doPutDiff(String repositoryPath, String path, InputStream data, long size, String baseChecksum, String textChecksum) throws SVNException {        
        HTTPHeader headers = new HTTPHeader();
        headers.setHeaderValue(HTTPHeader.CONTENT_TYPE_HEADER, HTTPHeader.SVNDIFF_MIME_TYPE);
        headers.setHeaderValue(HTTPHeader.CONTENT_LENGTH_HEADER, size + "");
        if (myLocks != null && myLocks.containsKey(repositoryPath)) {
            headers.setHeaderValue(HTTPHeader.IF_HEADER, "<" + repositoryPath + "> (<" + myLocks.get(repositoryPath) + ">)");
        }
        if (baseChecksum != null) {
            headers.setHeaderValue(HTTPHeader.BASE_MD5, baseChecksum);
        }
        if (textChecksum != null) {
            headers.setHeaderValue(HTTPHeader.TEXT_MD5, textChecksum);
        }
        IHTTPConnection httpConnection = getConnection();
        return httpConnection.request("PUT", path, headers, data, 201, 204, null, null);
    }
    
    public HTTPStatus doMerge(String activityURL, boolean response, DefaultHandler handler) throws SVNException {
        String locationPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
        StringBuffer request = DAVMergeHandler.generateMergeRequest(null, locationPath, activityURL, myLocks);
        HTTPHeader header = null;
        if (!response || (myLocks != null && !myKeepLocks)) {
            header = new HTTPHeader();
            String value = "";
            if (!response) {
                value += "no-merge-response";
            }
            if (myLocks != null && !myKeepLocks) {
                value += " release-locks";
            }
            header.setHeaderValue(HTTPHeader.SVN_OPTIONS_HEADER, value);
        }
        IHTTPConnection httpConnection = getConnection();
        return httpConnection.request("MERGE", getLocation().getURIEncodedPath(), header, request, -1, 0, null, handler);
    }
    
    public HTTPStatus doCheckout(String activityPath, String repositoryPath, String path, boolean allow404) throws SVNException {
        StringBuffer request = new StringBuffer();
        Collection namespaces = new LinkedList();
        namespaces.add(DAVElement.DAV_NAMESPACE);
        SVNXMLUtil.addXMLHeader(request);
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "checkout", namespaces, 
                SVNXMLUtil.PREFIX_MAP, request);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "activity-set", SVNXMLUtil.XML_STYLE_NORMAL, 
                null, request);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "href", activityPath, request);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "activity-set", request);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "checkout", request);
        HTTPHeader header = null;
        if (myLocks != null && repositoryPath != null && myLocks.containsKey(repositoryPath)) {
            header = new HTTPHeader();
            header.setHeaderValue(HTTPHeader.IF_HEADER, "(<" + myLocks.get(repositoryPath) + ">)");
        }
        
        IHTTPConnection httpConnection = getConnection(); 
        HTTPStatus status = httpConnection.request("CHECKOUT", path, header, request, 201, allow404 ? 404 : 0, 
                null, null);
        if (allow404 && status.getCode() == 404 && status.getError() != null) {
            status.setError(null);
        }
        // update location to be a path!
        if (status.getHeader().hasHeader(HTTPHeader.LOCATION_HEADER)) {
            SVNURL location = SVNURL.parseURIEncoded(status.getHeader().getFirstHeaderValue(HTTPHeader.LOCATION_HEADER));
            status.getHeader().setHeaderValue(HTTPHeader.LOCATION_HEADER, location.getURIEncodedPath());
        }
        return status;
    }

    public void doCopy(String src, String dst, int depth) throws SVNException {
        HTTPHeader header = new HTTPHeader();
        header.setHeaderValue(HTTPHeader.DESTINATION_HEADER, dst);
        header.setHeaderValue(HTTPHeader.DEPTH_HEADER, depth > 0 ? "infinity" : "0");
        SVNErrorMessage context = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "COPY of {0}", src);
        IHTTPConnection httpConnection = getConnection();
        HTTPStatus status = httpConnection.request("COPY", src, header, (StringBuffer) null, -1, 0, null, null, context);
        if (status.getCode() >= 300 && status.getError() != null) {
            SVNErrorMessage err = status.getError();
            SVNErrorManager.error(err, SVNLogType.NETWORK);
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
    
    public void setLocks(Map locks, boolean keepLocks) {
        myLocks = locks;
        myKeepLocks = keepLocks;
    }

    public void clearAuthenticationCache() {
        if (myHttpConnection != null) {
            myHttpConnection.clearAuthenticationCache();
        }
    }

    public String getCapabilityResponse(SVNCapability capability) throws SVNException {
    	if (myCapabilities == null || myCapabilities.get(capability) == null) {
    		exchangeCapabilities();
    	}
        return (String) myCapabilities.get(capability);
    }

    public void setCapability(SVNCapability capability, String capResult){
        myCapabilities.put(capability, capResult);        
    }

    protected IHTTPConnection getConnection() {
        return myHttpConnection;
    }

    protected void exchangeCapabilities() throws SVNException {
        String path = SVNEncodingUtil.uriEncode(getLocation().getPath());
        IHTTPConnection httpConnection = getConnection();
        HTTPStatus status = httpConnection.request("OPTIONS", path, null, (StringBuffer) null, 200, 0, null, null);
        if (status.getCode() == 200) {
        	parseCapabilities(status);
        } else {
        	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_OPTIONS_REQ_FAILED, 
        			"OPTIONS request (for capabilities) got HTTP response code {0}", 
        			new Integer(status.getCode()));
        	SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
    }
    
    protected SVNRepository getRepository() {
        return myRepository;
    }
    
    private void parseCapabilities(HTTPStatus status) {
        if (myCapabilities == null) {
            myCapabilities = new SVNHashMap();
        }
        myCapabilities.put(SVNCapability.DEPTH, DAV_CAPABILITY_NO);
        myCapabilities.put(SVNCapability.MERGE_INFO, DAV_CAPABILITY_NO);
        myCapabilities.put(SVNCapability.LOG_REVPROPS, DAV_CAPABILITY_NO);
    	
    	Collection capValues = status.getHeader().getHeaderValues(HTTPHeader.DAV_HEADER);
    	if (capValues != null) {
    		for (Iterator valuesIter = capValues.iterator(); valuesIter.hasNext();) {
    			String value = (String) valuesIter.next();
    			if (DAVElement.DEPTH_OPTION.equalsIgnoreCase(value)) {
    				myCapabilities.put(SVNCapability.DEPTH, DAV_CAPABILITY_YES);
    			} else if (DAVElement.MERGE_INFO_OPTION.equalsIgnoreCase(value)) {
    				myCapabilities.put(SVNCapability.MERGE_INFO, DAV_CAPABILITY_SERVER_YES);
    			} else if (DAVElement.LOG_REVPROPS_OPTION.equalsIgnoreCase(value)) {
    				myCapabilities.put(SVNCapability.LOG_REVPROPS, DAV_CAPABILITY_YES);
    			} else if (DAVElement.PARTIAL_REPLAY_OPTION.equalsIgnoreCase(value)) {
    				myCapabilities.put(SVNCapability.PARTIAL_REPLAY, DAV_CAPABILITY_YES);
    			}
			}
    	}
    }
    
    private String getActivityCollectionURL(String path, boolean force) throws SVNException {
        if (!force && myActivityCollectionURL != null) {
            return myActivityCollectionURL;
        }
        DAVOptionsHandler handler = new DAVOptionsHandler();
        IHTTPConnection httpConnection = getConnection();
        httpConnection.request("OPTIONS", path, null, DAVOptionsHandler.OPTIONS_REQUEST, -1, 0, null, handler);
        myActivityCollectionURL = handler.getActivityCollectionURL();
        if (myActivityCollectionURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_OPTIONS_REQ_FAILED, 
                    "The OPTIONS request did not include the requested activity-collection-set; this often means that the URL is not WebDAV-enabled");
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
        return myActivityCollectionURL;
    }
    
    private static String generateUUID() {
        try {
            return SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        } catch (SVNException svne) {
            long time = System.currentTimeMillis();
            String uuid = Long.toHexString(time);
            int zeroes = 16 - uuid.length();
            for(int i = 0; i < zeroes; i++) {
                uuid = "0" + uuid;
            }
            return uuid;
        }
    }

}
