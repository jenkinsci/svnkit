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

import org.tmatesoft.svn.util.PathUtil;

/**
 * The <code>SVNRepositoryLocation</code> class incapsulates a URL used
 * to access the Subversion Repository. There are actually two ways
 * to access the repository:
 * <ol>
 * <li>over a network
 * <li>directly (locally)
 * </ol>
 * Regardless of the way you do it you always ought to provide a necessary URL. 
 * There are the following URL schemas:
 * <ol>
 * <li>file:/// - to access the repository locally, e.g. file:///path/to/repos
 * <li>http:// or https:// (with SSL encryption)-
 * to access the repository via the WebDAV protocol to Subversion-aware Apach
 * Server
 * <li>svn:// - to access the repository via the custom standalone protocol
 * <li>svn+ssh:// - the same as svn:// but through a SSH tunnel
 * </ol>
 * Specifying a URL you can also point a definite port to go out through, e.g.
 * http://host:8080/path/to/repos. If you don't then a default one will be used
 * for the current protocol.
 *     
 * @version 1.0
 * @author TMate Software Ltd.
 * @see SVNRepository
 */
public class SVNRepositoryLocation {

    private String myPath;
    private final String myHost;
    private final int myPort;
    private final String myProtocol;
    private String myAsString;

    /**
     * <p>
     * This static method compares two <code>SVNRepositoryLocation</code> objects.
     * </p>
     * @param location1 reference to a <code>SVNRepositoryLocation</code> object
     * @param location2 reference to a <code>SVNRepositoryLocation</code> object
     * @return true if both parameters have the same (equal) repository access attributes
     * (i.e. protocol, host, port, path) or if
     * location1 = location2 = <code>null</code>.
     * false - in all other cases
     * @see #equals(Object)
     */
    public static boolean equals(SVNRepositoryLocation location1, SVNRepositoryLocation location2) {
        if (location1 == null || location2 == null) {
            return location1 == location2;
        } 
        return location1.toString().equals(location2.toString());
    }

    /**
     * <p>
     * This is a static factory method that creates an instance of <code>SVNRepositoryLocation</code>
     * given the repository access URL string. The method parses the string, extracts
     * from it the protocol, the port number, the host and the path within the host and finally
     * constructs a new <code>SVNRepositoryLocation</code> object. 
     * </p>
     * @param location <code>String</code> parameter to define the needed URL (for example,
     * "http://tmate.org/svn/repos/trunk/").
     * @return a reference to SVNRepositoryLocation object or <code>null</code> if
     * location parameter is <code>null</code> 
     * @throws {@link SVNException}
     * @see #SVNRepositoryLocation(String, String, int, String)
     */
    public static SVNRepositoryLocation parseURL(String location) throws SVNException {
        if (location == null) {
            return null;
        }
        int index = location.indexOf(':');
        if (index < 0) {
            throw new SVNException("malformed url: " + location);
        }
        String protocol = location.substring(0, index);
        location = "http" + location.substring(protocol.length());
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            throw new SVNException("malformed url " + location);
        }
        if (url != null) {
            String host = url.getHost();
            int port = url.getPort();
            if (port < 0) {
                if ("svn".equals(protocol)) {
                    port = 3690;
                } else if ("http".equals(protocol)) {
                    port = 80;
                } else if ("https".equals(protocol)) {
                    port = 443;
                } else if ("svn+ssh".equals(protocol)) {
                    port = 22;
                }
            }
            String path = url.getPath();
            if (!path.endsWith("/")) {
                path += "/";
            }
            return new SVNRepositoryLocation(protocol, host, port, path);
        }
        throw new SVNException("malformed url " + location);
    }

    /**
     * <p>
     * Creates a new instance of <code>SVNRepositoryLocation</code>.
     * </p>
     * @param protocol <code>String</code> parameter to define the protocol to be used
     * @param host <code>String</code> parameter to define the host (you may use ip 
     * adresses as well as dns names)
     * @param port the port number
     * @param path <code>String</code> parameter to define the path (relative to the host)
     * deep down the reposytory tree root  
     */
    public SVNRepositoryLocation(String protocol, String host, int port, String path) {
        myHost = host;
        myProtocol = protocol;
        myPort = port;
        myPath = path;
        myPath = PathUtil.removeTrailingSlash(myPath);
        myPath = PathUtil.encode(myPath);
    }

    /**
     * <p>
     * Returns the used protocol as a string.
     * </p>
     * @return protocol string 
     */
    public String getProtocol() {
        return myProtocol;
    }

    /**
     * <p>
     * Returns the defined host address as a string
     * </p>
     * @return host string 
     */
    public String getHost() {
        return myHost;
    }
    /**
     * <p>
     * Returns the defined repository location path 
     * </p>
     * @return path string 
     */

    public String getPath() {
        return myPath;
    }
    /**
     * <p>
     * Returns the used port number 
     * </p>
     * @return <code>int</code> value of the port 
     */
    public int getPort() {
        return myPort;
    }
    /**
     * <p>
     * Represents a <code>SVNRepositoryLocation</code> object as a URL string 
     * missing a default port number (if it is used) in the resultant output
     * for all protocols but svn one.
     * </p>
     * @see #toCanonicalForm()
     * @return URL string representation of this object 
     */
    public String toString() {
        if (myAsString != null) {
            return myAsString;
        }
        StringBuffer sb = new StringBuffer();
        sb.append(myProtocol);
        sb.append("://");
        sb.append(PathUtil.encode(myHost));
        if (myPort != getDefaultPort(myProtocol)) {
            sb.append(':');
            sb.append(myPort);
        }
        sb.append(myPath);
        myAsString = sb.toString();
        return sb.toString();
    }
    /**
     * <p>
     * Represents a <code>SVNRepositoryLocation</code> object as a complete URL
     * string (including a port number). 
     * </p>
     * @see #toString()
     * @return canonical URL string representation of this object 
     */
    public String toCanonicalForm() {
        StringBuffer sb = new StringBuffer();
        sb.append(myProtocol);
        sb.append("://");
        sb.append(PathUtil.encode(myHost));
        sb.append(':');
        sb.append(myPort);
        sb.append(myPath);
        return sb.toString();
    }

    private static int getDefaultPort(String protocol) {
        if ("http".equals(protocol)) {
            return 80;
        } else if ("svn".equals(protocol)) {
            return -1; // force port saving.
        } else if ("https".equals(protocol)) {
            return 443;
        } else if ("svn+ssh".equals(protocol)) {
            return 22;
        }
        return -1;
    }
    
    /**
     * <p>
     * Compares the current object with another one
     * </p>
     * @return true if both <code>SVNRepositoryLocation</code> objects have the
     * same (equal) repository access attributes (i.e. protocol, host, port, path)
     * and false in all other cases.
     * @see #equals(SVNRepositoryLocation, SVNRepositoryLocation)
     */
    public boolean equals(Object o) {
    	if (o == this) {
    		return true;
    	}
    	if (o == null || o.getClass() != SVNRepositoryLocation.class) {
    		return false;
    	}
    	return toCanonicalForm().equals(((SVNRepositoryLocation) o).toCanonicalForm());
    }
    
    /**
     * <p>
     * Returns the hash code of a <code>SVNRepositoryLocation</code> object's 
     * canonical URL string form.
     * </p>
     * @return the hash code value of the canonical URL string
     * @see #toCanonicalForm()
     */
    public int hashCode() {
    	return toCanonicalForm().hashCode();
    }
}
