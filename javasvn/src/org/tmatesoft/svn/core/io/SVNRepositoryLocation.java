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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.PathUtil;

/**
 * The <code>SVNRepositoryLocation</code> class incapsulates a <code>URL</code> used
 * to access the Subversion Repository. There are actually two ways
 * to access the repository:
 * <ul>
 * <li>over a network
 * <li>directly (locally)
 * </ul>
 * Regardless of the way a client do it he always ought to provide a necessary 
 * <code>URL</code>. There are the following <code>URL</code> schemas:
 * <ul>
 * <li><i>file:///</i> - to access the repository locally, e.g. file:///path/to/repos
 * <li><i>http://</i> or <i>https://</i> (with the <i>SSL</i> encryption)-
 * to access the repository via the <i>WebDAV</i> protocol to a Subversion-aware 
 * Apach Server
 * <li><i>svn://</i> - to access the repository via the custom standalone protocol
 * <li><i>svn+ssh://</i> - the same as svn:// but through an <i>SSH</i> tunnel
 * </ul>
 * Specifying a <code>URL</code> you can also point the definite port to go out through,
 * e.g. http://host:8080/path/to/repos. If you don't, then a default one will be used
 * for the current protocol.
 * 
 * <p>
 * <b>NOTE:</b> unfortunately, at present the <i>JavaSVN</i> library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNRepository
 */
public class SVNRepositoryLocation {

    private String myPath;
    private final String myHost;
    private final int myPort;
    private final String myProtocol;
    private String myAsString;
    
    /**
     * Compares two <code>SVNRepositoryLocation</code> objects.
     * 
     * @param location1 	reference to a <code>SVNRepositoryLocation</code> object
     * @param location2 	reference to a <code>SVNRepositoryLocation</code> object
     * @return 				<code>true</code> if both parameters represent the same
     * 						<code>URL</code> (that is the same protocol, host, port, 
     * 						path) or if location1 = location2 = <code>null</code>;
     * 						<code>false</code> - in all other cases
     * @see 				#equals(Object)
     */
    public static boolean equals(SVNRepositoryLocation location1, SVNRepositoryLocation location2) {
        if (location1 == null || location2 == null) {
            return location1 == location2;
        } 
        return location1.toString().equals(location2.toString());
    }
    
    /**
     * This is a static factory method that creates an instance of 
     * <code>SVNRepositoryLocation</code> given a repository location <code>URL</code>
     * string. The method parses the string, extracts from it the protocol, the port
     * number, the host and the path within the host and finally constructs a new 
     * <code>SVNRepositoryLocation</code> object. 
     * 
     * @param  location 		a <code>URL</code> string that defines the location of
     * 							a repository (for example, 
     * 							"http://tmate.org/svn/repos/trunk/").
     * @return 					an instance of <code>SVNRepositoryLocation</code> 
     * 							or <code>null</code> if	the <code>location</code>
     * 							parameter is <code>null</code> 
     * @throws SVNException		if the <code>URL</code> is malformed
     * @see 					#SVNRepositoryLocation(String, String, int, String)
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
     * Constructs a new instance of <code>SVNRepositoryLocation</code>.
     * 
     * @param protocol 		a protocol to connect to a repository server
     * @param host 			a repository server host name (you may use ip 
     * 						adresses as well as dns names)
     * @param port 			a port number
     * @param path 			a path to a repository located at the <code>host</code>;
     * 						may be the root directory which the repository was 
     * 						created in as well as its subdirectory   
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
     * Returns the used protocol as a string.
     * 
     * @return a protocol string 
     */
    public String getProtocol() {
        return myProtocol;
    }
    
    /**
     * Returns the defined host address as a string
     * 
     * @return a host address string 
     */
    public String getHost() {
        return myHost;
    }
    
    /**
     * Returns the defined repository location path 
     * 
     * @return a repository path string 
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Returns the used port number 
     * 
     * @return 	a port number 
     */
    public int getPort() {
        return myPort;
    }

    /**
     * Represents a <code>SVNRepositoryLocation</code> object as a <code>URL</code> 
     * string missing a default port number (if it is used) in the resultant output
     * for all protocols but svn one.
     * 
     * @return 	a <code>URL</code>-string representation of this object
     * @see 	#toCanonicalForm()
     */
    public String toString(){
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
     * Represents a <code>SVNRepositoryLocation</code> object as a canonical 
     * <code>URL</code> string (including a port number). 
     * 
     * @return 		a canonical <code>URL</code>-string representation of this object 
     * @see 		#toString()
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
     * Compares this object with another one
     * 
     * @return 	<code>true</code> if both <code>SVNRepositoryLocation</code> objects 
     * 			represent the same <code>URL</code> (that is the same protocol, host,
     * 			port, path) and <code>false</code> in all other cases
     * 
     * @see 	#equals(SVNRepositoryLocation, SVNRepositoryLocation)
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
     * Returns a hash code of an <code>SVNRepositoryLocation</code> object's 
     * canonical <code>URL</code>-string form.
     * 
     * @return 	a hash code value for this object 
     * @see 	#toCanonicalForm()
     */
    public int hashCode() {
    	return toCanonicalForm().hashCode();
    }
}
