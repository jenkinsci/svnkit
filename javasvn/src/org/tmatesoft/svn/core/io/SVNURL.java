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
package org.tmatesoft.svn.core.io;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNURL {
    
    // uri-encoded.
    public static SVNURL parse(String url) throws SVNException {
        return new SVNURL(url);
    }
    
    private static final Collection VALID_PROTOCOLS = new HashSet();
    
    static {
        VALID_PROTOCOLS.add("svn");
        VALID_PROTOCOLS.add("svn+ssh");
        VALID_PROTOCOLS.add("http");
        VALID_PROTOCOLS.add("https");
    }
    
    private String myURL;
    private String myProtocol;
    private String myHost;
    private String myPath;
    private String myUserName;
    private int myPort;
    
    private SVNURL(String url) throws SVNException {
        if (url == null) {
            SVNErrorManager.error("svn: invalid URL '" + url + "'");
        }
        // remove trailing '/'
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        myURL = url;
        int index = url.indexOf("://");
        if (index <= 0) {
            SVNErrorManager.error("svn: invalid URL '" + url + "'");
        }
        myProtocol = url.substring(0, index);
        if (!VALID_PROTOCOLS.contains(myProtocol)) {
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
        myPath = SVNEncodingUtil.uriDecode(httpURL.getPath());
        myUserName = httpURL.getUserInfo();
        myPort = httpURL.getPort();
        if (myPort < 0) {
            if ("svn".equals(myProtocol)) {
                myPort = 3690;
            } else if ("svn+ssh".equals(myProtocol)) {
                myPort = 22;
            } else if ("http".equals(myProtocol)) {
                myPort = 80;
            } else if ("https".equals(myProtocol)) {
                myPort = 443;
            }
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
    
    public String getUserInfo() {
        return myUserName;
    }
    
    public String toString() {
        return myURL;
    }
}