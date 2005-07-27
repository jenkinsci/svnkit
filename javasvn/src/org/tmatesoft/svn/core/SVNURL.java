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
package org.tmatesoft.svn.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNURL {
    
    public static SVNURL create(String protocol, String userInfo, String host, int port, String path, boolean uriEncoded) throws SVNException {
        path = path == null ? "/" : path.trim();
        if (!uriEncoded) {
            path = SVNEncodingUtil.uriEncode(path);
        }
        if (path.length() > 0 && path.charAt(0) != '/') {
            path = "/" + path;
        }
        if (path.length() > 0 && path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        String url = composeURL(protocol, userInfo, host, port, path);
        return new SVNURL(url, true);
    }
    
    public static SVNURL parseURIDecoded(String url) throws SVNException {
        return new SVNURL(url, false);
    }

    public static SVNURL parseURIEncoded(String url) throws SVNException {
        return new SVNURL(url, true);
    }
    
    private static final Map DEFAULT_PORTS = new HashMap();
    
    static {
        DEFAULT_PORTS.put("svn", new Integer(3690));
        DEFAULT_PORTS.put("svn+ssh", new Integer(22));
        DEFAULT_PORTS.put("http", new Integer(80));
        DEFAULT_PORTS.put("https", new Integer(443));
    }
    
    private String myURL;
    private String myProtocol;
    private String myHost;
    private String myPath;
    private String myUserName;
    private int myPort;
    private String myEncodedPath;
    private boolean myIsDefaultPort;
    
    private SVNURL(String url, boolean uriEncoded) throws SVNException {
        if (url == null) {
            SVNDebugLog.log(new Exception());
            SVNErrorManager.error("svn: invalid URL '" + url + "'");
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int index = url.indexOf("://");
        if (index <= 0) {
            SVNErrorManager.error("svn: invalid URL '" + url + "'");
        }
        myProtocol = url.substring(0, index);
        if (!DEFAULT_PORTS.containsKey(myProtocol)) {
            SVNErrorManager.error("svn: invalid URL '" + url + "': protocol '" + myProtocol + "' is not supported");
        }
        String testURL = "http" + url.substring(index);
        URL httpURL;
        try {
            httpURL = new URL(testURL);
        } catch (MalformedURLException e) {
            SVNErrorManager.error("svn: invalid URL '" + url + "': " + e.getMessage());
            return;
        }
        myHost = httpURL.getHost();
        if (uriEncoded) {
            myEncodedPath = httpURL.getPath();
            myPath = SVNEncodingUtil.uriDecode(myEncodedPath);
        } else {
            myPath = httpURL.getPath();
            myEncodedPath = SVNEncodingUtil.uriEncode(myPath);
        }
        myUserName = httpURL.getUserInfo();
        myPort = httpURL.getPort();
        myIsDefaultPort = myPort < 0;
        if (myPort < 0) {
            Integer defaultPort = (Integer) DEFAULT_PORTS.get(myProtocol);
            myPort = defaultPort.intValue();
        } 
    }
    
    public String getProtocol() {
        return myProtocol;
    }
    
    public String getHost() {
        return myHost;
    }
    
    public int getPort() {
        return myPort;
    }
    
    // uri-decoded
    public String getPath() {
        return myPath;
    }
    
    public String getURIEncodedPath() {
        return myEncodedPath;
    }
    
    public String getUserInfo() {
        return myUserName;
    }
    
    public String toString() {
        if (myURL == null) {
            myURL = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), getURIEncodedPath());
        }
        return myURL;
    }
    
    public SVNURL appendPath(String segment, boolean uriEncoded) {
        if (segment == null) {
            return this;
        }
        if (!uriEncoded) {
            segment = SVNEncodingUtil.uriEncode(segment);
        }
        String newPath = SVNPathUtil.append(getURIEncodedPath(), segment);
        String url = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), newPath);
        try {
            return parseURIEncoded(url);
        } catch (SVNException e) {
            //
        }
        return null;
    }
    
    private static String composeURL(String protocol, String userInfo, String host, int port, String path) {
        StringBuffer url = new StringBuffer();
        url.append(protocol);
        url.append("://");
        if (userInfo != null) {
            url.append(userInfo);
            url.append("@");
        }
        url.append(host);
        if (port >= 0) {
            url.append(":");
            url.append(port);
        }
        url.append(path);
        return url.toString();
    }
} 