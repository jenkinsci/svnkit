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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVOptionsHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVPropertiesHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Alexander Kitaev
 */
class DAVConnection {
    
    private SVNRepositoryLocation myLocation;
    private HttpConnection myHttpConnection;
    private String myActivityCollectionURL;
    
    public DAVConnection(SVNRepositoryLocation location) {
        myLocation = location;
    }
    
    public SVNRepositoryLocation getLocation() {
        return myLocation;
    }
    
    public void open(DAVRepository repository) throws SVNException {
        if (myHttpConnection == null) {
        	myHttpConnection = new HttpConnection(myLocation, repository.getCredentialsProvider());
            if (repository != null && repository.getRepositoryUUID() == null) {
                String path = myLocation.getPath();
                final DAVResponse[] result = new DAVResponse[1];
                StringBuffer body = DAVPropertiesHandler.generatePropertiesRequest(null, DAVElement.STARTING_PROPERTIES);
                IDAVResponseHandler handler = new IDAVResponseHandler() {
                    public void handleDAVResponse(DAVResponse response) {
                        result[0] = response;
                    }
                };
                byte[] bodyBuffer = null;
                try {
                    bodyBuffer = body.toString().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new SVNException(e);
                }
                while(true) {
                    DAVStatus status = myHttpConnection.request("PROPFIND", path, 0, null, bodyBuffer, new DAVPropertiesHandler(handler), new int[] {200, 207, 404});
                    if (status.getResponseCode() == 404) {
                        if ("".equals(path) || "/".equals(path)) {
                            throw new SVNException(status.getErrorText());
                        }
                        path = PathUtil.removeTail(path);
                    } else if (status.getResponseCode() == 200 || status.getResponseCode() == 207) {
                        break;
                    } else {
                        throw new SVNException(status.getErrorText());
                    }
                }
                String uuid = (String) result[0].getPropertyValue(DAVElement.REPOSITORY_UUID);
                String relativePath = (String) result[0].getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH);
                
                String root = myLocation.getPath();
                if (relativePath != null) {
                    relativePath = PathUtil.removeTrailingSlash(relativePath);
                    root = PathUtil.removeTrailingSlash(root);
                    if (root.endsWith(relativePath)) {
                        root = root.substring(0, root.length() - relativePath.length() - 1);                        
                    }
                }
                repository.updateCredentials(uuid, root);
            }
        }
    }    
    
    public void doPropfind(String path, int depth, String label, DAVElement[] properties, IDAVResponseHandler handler) throws SVNException {
        StringBuffer body = DAVPropertiesHandler.generatePropertiesRequest(null, properties);
        try {
            byte[] bodyBuffer = body.toString().getBytes("UTF-8");
            myHttpConnection.request("PROPFIND", path, depth, label, bodyBuffer, new DAVPropertiesHandler(handler), new int[] {200, 207});
        } catch (IOException e) {
            throw new SVNException(e);
        } 
    }

	public void doGet(String path, OutputStream os) throws SVNException {
		myHttpConnection.request("GET", path, 0, null, new byte[0], os, null);
    }
	
	public void doReport(String path, byte[] requestBody, DefaultHandler handler) throws SVNException {
		myHttpConnection.request("REPORT", path, 0, null, requestBody, handler, new int[] {200, 207});
	}

    public void doProppatch(String path, byte[] requestBody, DefaultHandler handler) throws SVNException {
        myHttpConnection.request("PROPPATCH", path, 0, null, requestBody, handler, new int[] {200, 207});

    }
    
    public String doMakeActivity() throws SVNException {
        String url = getActivityCollectionURL(myLocation.getPath(), false) + generateUUID();
        DAVStatus status = myHttpConnection.request("MKACTIVITY", url, 0, null, null, (OutputStream) null, new int[] {201, 404});
        if (status.getResponseCode() == 404) {
            // refetch
            url = getActivityCollectionURL(myLocation.getPath(), true) + generateUUID();
            status = myHttpConnection.request("MKACTIVITY", url, 0, null, null, (OutputStream) null, new int[] {201});
        }
        return url;
    }
    
    public DAVStatus doDelete(String path) throws SVNException {
        return myHttpConnection.request("DELETE", path, null, (byte[]) null, null, null);
    }

    public DAVStatus doDelete(String path, long revision) throws SVNException {
        Map header = new HashMap();
        if (revision >= 0) {
            header.put("X-SVN-Version-Name", Long.toString(revision));
        }
        header.put("Depth", "infinity");
        return myHttpConnection.request("DELETE", path, header, (byte[]) null, null, null);
    }
    
    public DAVStatus doMakeCollection(String path) throws SVNException {
        return myHttpConnection.request("MKCOL", path, null, (byte[]) null, null, null);
    }
    
    public DAVStatus doPutDiff(String path, InputStream data) throws SVNException {
        
        Map headers = new HashMap();
        headers.put("Content-Type", "application/vnd.svn-svndiff");

        return myHttpConnection.request("PUT", path, headers, data, null, null);
    }
    
    public DAVStatus doMerge(String activityURL, boolean response, DefaultHandler handler) throws SVNException {
        StringBuffer request = DAVMergeHandler.generateMergeRequest(null, activityURL);
        
        byte[] reqBody = request.toString().getBytes();
        Map header = null;
        if (!response) {
            header = new HashMap();
            header.put("X-SVN-Options", "no-merge-response");
        }
        return myHttpConnection.request("MERGE", myLocation.getPath(), header, reqBody, handler, null);
    }
    
    public DAVStatus doCheckout(String activityPath, String path) throws SVNException {
        StringBuffer request = new StringBuffer();
        request.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        request.append("<D:checkout xmlns:D=\"DAV:\">");
        request.append("<D:activity-set>");
        request.append("<D:href>");
        request.append(activityPath);
        request.append("</D:href>");
        request.append("</D:activity-set></D:checkout>");
        byte[] reqBody = request.toString().getBytes();
        DAVStatus status = myHttpConnection.request("CHECKOUT", path, 0, null, reqBody, (DefaultHandler) null, null);
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
        return myHttpConnection.request("COPY", src, header, (byte[]) null, null, null);
    }

    public void close()  {
        if (myHttpConnection != null) {
            myHttpConnection.close();
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
}