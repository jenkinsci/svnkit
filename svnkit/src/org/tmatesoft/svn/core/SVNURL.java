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
package org.tmatesoft.svn.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.io.File;

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * The <b>SVNURL</b> class is used for representing urls. Those SVNKit
 * API methods, that need repository locations to carry out an operation, 
 * receive a repository location url represented by <b>SVNURL</b>. This
 * class does all the basic work for a caller: parses an original url 
 * string (splitting it to components), encodes/decodes a path component
 * to/from the UTF-8 charset, checks for validity (such as protocol support
 *  - if SVNKit does not support a particular protocol, <b>SVNURL</b> 
 * throws a corresponding exception). 
 * 
 * <p>
 * To create a new <b>SVNURL</b> representation, pass an original url
 * string (like <span class="javastring">"http://userInfo@host:port/path"</span>)
 * to a corresponding <i>parse</i> method of this class. 
 *  
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNURL {
    /**
     * Creates a new <b>SVNURL</b> representation from the given url 
     * components.
     * 
     * @param protocol       a protocol component
     * @param userInfo       a user info component
     * @param host           a host component
     * @param port           a port number
     * @param path           a path component
     * @param uriEncoded     <span class="javakeyword">true</span> if 
     *                       <code>path</code> is UTF-8 encoded,
     *                       <span class="javakeyword">false</span> 
     *                       otherwise 
     * @return               a new <b>SVNURL</b> representation
     * @throws SVNException  if the resultant url (composed of the given 
     *                       components) is malformed
     */
    public static SVNURL create(String protocol, String userInfo, String host, int port, String path, boolean uriEncoded) throws SVNException {
        if (host == null || host.indexOf('@') >= 0) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Invalid host name ''{0}''", host));
        }
        path = path == null ? "/" : path.trim();
        if (!uriEncoded) {
            path = SVNEncodingUtil.uriEncode(path);
        } else {
            path = SVNEncodingUtil.autoURIEncode(path);
        }
        if (path.length() > 0 && path.charAt(0) != '/') {
            path = "/" + path;
        }
        if (path.length() > 0 && path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        protocol = protocol == null ? "http" : protocol.toLowerCase();
        String errorMessage = null;
        if (userInfo != null && userInfo.indexOf('/') >= 0) {
            errorMessage = "Malformed URL: user info part could not include '/' symbol";
        } else if (host == null && !"file".equals(protocol)) {
            errorMessage = "Malformed URL: host could not be NULL";
        } else if (!"file".equals(protocol) && host.indexOf('/') >= 0) {
            errorMessage = "Malformed URL: host could not include '/' symbol";
        }
        if (errorMessage != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, errorMessage);
            SVNErrorManager.error(err);
        }
        String url = composeURL(protocol, userInfo, host, port, path);
        return new SVNURL(url, true);
    }

    /**
     * Parses the given decoded (not UTF-8 encoded) url string and creates 
     * a new <b>SVNURL</b> representation for this url.
     * 
     * @param  url           an input url string (like <span class="javastring">'http://myhost/mypath'</span>)
     * @return               a new <b>SVNURL</b> representation of <code>url</code>
     * @throws SVNException  if <code>url</code> is malformed
     */
    public static SVNURL parseURIDecoded(String url) throws SVNException {
        return new SVNURL(url, false);
    }
    
    /**
     * Parses the given UTF-8 encoded url string and creates a new 
     * <b>SVNURL</b> representation for this url. 
     * 
     * @param  url           an input url string (like <span class="javastring">'http://myhost/my%20path'</span>)
     * @return               a new <b>SVNURL</b> representation of <code>url</code>
     * @throws SVNException  if <code>url</code> is malformed
     */
    public static SVNURL parseURIEncoded(String url) throws SVNException {
        return new SVNURL(url, true);
    }
    
    /**
     * Creates a <span class="javastring">"file:///"</span> <b>SVNURL</b> 
     * representation given a filesystem style repository path.  
     * 
     * @param  repositoryPath  a repository path as a filesystem path  
     * @return                 an <b>SVNURL</b> representation  
     * @throws SVNException
     */
    public static SVNURL fromFile(File repositoryPath) throws SVNException {
        if (repositoryPath == null) {
            return null;
        }
        String path = repositoryPath.getAbsoluteFile().getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return SVNURL.parseURIDecoded("file://" + path);
    }
    
    /**
     * Returns the default port number for the specified protocol.
     * 
     * @param protocol a particular access protocol
     * @return         default port number
     */
    public static int getDefaultPortNumber(String protocol) {
        if (protocol == null) {
            return -1;
        }
        protocol = protocol.toLowerCase();
        if ("file".equals(protocol)) {
            return -1;
        }
        Integer dPort = (Integer) DEFAULT_PORTS.get(protocol);
        if (dPort != null) {
            return dPort.intValue();
        }
        return -1;
    }
    
    private static final Map DEFAULT_PORTS = new HashMap();
    
    static {
        DEFAULT_PORTS.put("svn", new Integer(3690));
        DEFAULT_PORTS.put("svn+ssh", new Integer(22));
        DEFAULT_PORTS.put("http", new Integer(80));
        DEFAULT_PORTS.put("https", new Integer(443));
        DEFAULT_PORTS.put("file", new Integer(0));
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL cannot be NULL");
            SVNErrorManager.error(err);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int index = url.indexOf("://");
        if (index <= 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Malformed URL ''{0}''", url);
            SVNErrorManager.error(err);
        }
        myProtocol = url.substring(0, index);
        myProtocol = myProtocol.toLowerCase();
        if (!DEFAULT_PORTS.containsKey(myProtocol) && !myProtocol.startsWith("svn+")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL protocol is not supported ''{0}''", url);
            SVNErrorManager.error(err);
        }
        if ("file".equals(myProtocol)) {
            String normalizedPath = norlmalizeURLPath(url, url.substring("file://".length()));
            
            URL testURL = null;
            try {
                testURL = new URL(myProtocol + "://" + normalizedPath);
            } catch (MalformedURLException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Malformed URL: ''{0}'': {1}", new Object[] {url, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
                return;
            }
            String testPath = getPath(testURL);
            if(testPath == null || "".equals(testPath)){
                //no path, only host - follow subversion behaviour
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Local URL ''{0}'' contains only a hostname, no path", url);
                SVNErrorManager.error(err);
                return;
            }
            myHost = testURL.getHost() == null ? "" : testURL.getHost();
            if (uriEncoded) {
                // autoencode it.
                myEncodedPath = SVNEncodingUtil.autoURIEncode(testPath);
                SVNEncodingUtil.assertURISafe(myEncodedPath);
                myPath = SVNEncodingUtil.uriDecode(myEncodedPath);
                myPath = myPath.replace(File.separatorChar, '/');
                if(!myPath.startsWith("/")){
                    myPath = "/" + myPath;
                }
            } else {
                myPath = testPath;
                if(!myPath.startsWith("/")){
                    myPath = "/" + myPath;
                }
                myEncodedPath = SVNEncodingUtil.uriEncode(myPath);
            }
            myUserName = testURL.getUserInfo();
            myPort = testURL.getPort();
        } else {
            String testURL = "http" + url.substring(index);
            URL httpURL;
            try {
                httpURL = new URL(testURL);
            } catch (MalformedURLException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Malformed URL: ''{0}'': {1}", new Object[] {url, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
                return;
            }
            myHost = httpURL.getHost();
            String httpPath = norlmalizeURLPath(url, getPath(httpURL));
            if (uriEncoded) {
                // autoencode it.
                myEncodedPath = SVNEncodingUtil.autoURIEncode(httpPath);
                SVNEncodingUtil.assertURISafe(myEncodedPath);
                myPath = SVNEncodingUtil.uriDecode(myEncodedPath);
            } else {
                myPath = httpPath;
                myEncodedPath = SVNEncodingUtil.uriEncode(myPath);
            }
            myUserName = httpURL.getUserInfo();
            myPort = httpURL.getPort();
            myIsDefaultPort = myPort < 0;
            if (myPort < 0) {
                Integer defaultPort = (Integer) DEFAULT_PORTS.get(myProtocol);
                myPort = defaultPort != null ? defaultPort.intValue() : 0;
            } 
        }
    }
    
    /**
     * Returns the protocol component of the url represented by this
     * object.
     *  
     * @return  a protocol name (like <code>http</code>)
     */
    public String getProtocol() {
        return myProtocol;
    }
    
    /**
     * Returns the host component of the url represented by this object. 
     * 
     * @return  a host name
     */
    public String getHost() {
        return myHost;
    }
    
    /**
     * Returns the port number specified (or default) for the host.
     *  
     * @return  a port number
     */
    public int getPort() {
        return myPort;
    }
    
    /**
     * Says if the url is provided with a non-default port number or not.
     * 
     * @return  <span class="javakeyword">true</span> if the url
     *          comes with a non-default port number, 
     *          <span class="javakeyword">false</span> otherwise  
     * @see     #getPort()
     */
    public boolean hasPort() {
        return !myIsDefaultPort;
    }
    
    /**
     * Returns the path component of the url represented by this object 
     * as a uri-decoded string 
     * 
     * @return  a uri-decoded path
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Returns the path component of the url represented by this object 
     * as a uri-encoded string
     * 
     * @return  a uri-encoded path
     */
    public String getURIEncodedPath() {
        return myEncodedPath;
    }
    
    /**
     * Returns the user info component of the url represented by this 
     * object.
     *  
     * @return a user info part of the url (if it was provided)
     */
    public String getUserInfo() {
        return myUserName;
    }
    
    /**
     * Returns a string representing a UTF-8 encoded url. 
     * 
     * @return an encoded url string
     */
    public String toString() {
        if (myURL == null) {
            myURL = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), getURIEncodedPath());
        }
        return myURL;
    }
    
    /**
     * Returns a string representing a decoded url. 
     * 
     * @return a decoded url string
     */
    public String toDecodedString() {
        return composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), getPath());
    }
    
    /**
     * Constructs a new <b>SVNURL</b> representation appending a new path
     * segment to the path component of this representation.  
     * 
     * @param  segment      a new path segment
     * @param  uriEncoded   <span class="javakeyword">true</span> if 
     *                      <code>segment</code> is UTF-8 encoded,
     *                      <span class="javakeyword">false</span> 
     *                      otherwise 
     * @return              a new <b>SVNURL</b> representation
     * @throws SVNException if a parse error occurred
     */
    public SVNURL appendPath(String segment, boolean uriEncoded) throws SVNException {
        if (segment == null || "".equals(segment)) {
            return this;
        }
        if (!uriEncoded) {
            segment = SVNEncodingUtil.uriEncode(segment);
        } else {
            segment = SVNEncodingUtil.autoURIEncode(segment);
        }
        String path = getURIEncodedPath();
        if ("".equals(path)) {
            path = "/" + segment;
        } else {
            path = SVNPathUtil.append(path, segment);
        }
        String url = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), path);
        return parseURIEncoded(url);
    }

    /**
     * Creates a new <b>SVNURL</b> object replacing a path component of 
     * this object with a new provided one. 
     * 
     * @param  path            a path component
     * @param  uriEncoded      <span class="javakeyword">true</span> if <code>path</code> 
     *                         is UTF-8 encoded
     * @return                 a new <b>SVNURL</b> representation                 
     * @throws SVNException    if a parse error occurred
     */
    public SVNURL setPath(String path, boolean uriEncoded) throws SVNException {
        if (path == null || "".equals(path)) {
            path = "/";
        }
        if (!uriEncoded) {
            path = SVNEncodingUtil.uriEncode(path);
        } else {
            path = SVNEncodingUtil.autoURIEncode(path);
        }
        String url = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), path);
        return parseURIEncoded(url);
    }
    
    /**
     * Constructs a new <b>SVNURL</b> representation removing a tail path
     * segment from the path component of this representation.  
     * 
     * @return  a new <b>SVNURL</b> representation
     */
    public SVNURL removePathTail() throws SVNException {
        String newPath = SVNPathUtil.removeTail(myPath);
        String url = composeURL(getProtocol(), getUserInfo(), getHost(), myIsDefaultPort ? -1 : getPort(), newPath);
        return parseURIDecoded(url);
    }
    /**
     * Compares this object with another one.
     * 
     * @param   obj  an object to compare with
     * @return  <span class="javakeyword">true</span> if <code>obj</code>
     *          is an instance of <b>SVNURL</b> and has got the same
     *          url components as this object has
     */
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != SVNURL.class) {
            return false;
        }
        SVNURL url = (SVNURL) obj;
        boolean eq = myProtocol.equals(url.myProtocol) && 
            myPort == url.myPort &&
            myHost.equals(url.myHost) &&
            myPath.equals(url.myPath) &&
            hasPort() == url.hasPort();
        if (myUserName == null) {
            eq &= url.myUserName == null;
        } else {
            eq &= myUserName.equals(url.myUserName);
        }
        return eq;
    }
    
    /**
     * Calculates and returns a hash code for this object.
     * 
     * @return a hash code value
     */
    public int hashCode() {
        int code = myProtocol.hashCode() + myHost.hashCode()*27 + myPath.hashCode()*31 + myPort*17;
        if (myUserName != null) {
            code += 37*myUserName.hashCode();
        }
        return code;
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
        if (path != null && !path.startsWith("/")) {
            path = '/' + path;
        }
        url.append(path);
        return url.toString();
    }
    
    private static String norlmalizeURLPath(String url, String path) throws SVNException {
        StringBuffer result = new StringBuffer(path.length());
        for(StringTokenizer tokens = new StringTokenizer(path, "/"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("".equals(token) || ".".equals(token)) {
                continue;
            } else if ("..".equals(token)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL ''{0}'' contains '..' element", url);
                SVNErrorManager.error(err);
            } else {
                result.append("/");
                result.append(token);
            }
        }
        if (!path.startsWith("/") && result.length() > 0) {
            result = result.delete(0, 1);
        }
        return result.toString();
    }
    
    private static String getPath(URL url) {
        String path = url.getPath();
        String ref = url.getRef();
        if (ref != null) {
            if (path == null) {
                path = "";
            }
            path += '#' + ref;
        }
        return path;
    }

} 