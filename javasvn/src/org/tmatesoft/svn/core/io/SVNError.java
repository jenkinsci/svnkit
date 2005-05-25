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

/**
 * This class represents an error description that a custom Subversion repository server - 
 * <i>svnserve</i> - sends to a client if it can not perform the client's request.
 * Such errors are stored in an <code>SVNException</code> that almost all of
 * the <i>JavaSVN</i> library methods may throw. 
 * 
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNException
 */
public class SVNError {
    
    private int myLineNumber;
    private int myCode;
    private String myLocation;
    private String myMessage;
    
    /**
     * Constructs an <code>SVNError</code> given an error description
     * 
     * @param message	an error description message
     */
    public SVNError(String message) {
        this(message, null, -1, -1);
    }
    
    /**
     * Constructs an <code>SVNError</code> given an error description, the path
     * the failed command was invoked for, an error code and a file line number (if any).
     * 
     * <p>
     * All this error information is reported by an <i>svnserve</i>.
     * 
     * @param message	an error description message
     * @param location	a path in a repository
     * @param code		an error code
     * @param line		a file line number or -1 if not actual
     */
    public SVNError(String message, String location, int code, int line) {
        myMessage = message;
        myLineNumber = line;
        myCode = code;
        myLocation = location;
    }
    
    /**
     * Gets the error description.
     *  
     * @return	the error description message
     */
    public String getMessage() {
        return myMessage;
    }
    
    /**
     * Gets the path at which the client's request failed  
     * 
     * @return	a path in the repository
     */
    public String getLocation() {
        return myLocation;    
    }
    
    /**
     * Gets the error code
     * 
     * @return the error code 
     */
    public int getCode() {
        return myCode;
    }
    
    /**
     * Gets the line number of the file.
     * If an error occured for a file then {@link #getLocation()} returns its
     * path and this method - the exact line number in the file (if actual).
     * 
     * @return	the file line number
     */
    public int getLineNumber() {
        return myLineNumber;
    }
    
    /**
     * Gets a string representation of this object.
     * 
     * @return	a string representation
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getCode());
        sb.append(": ");
        sb.append(getMessage());
        sb.append(" [");
        sb.append(getLocation());
        sb.append(":");
        sb.append(getLineNumber());
        sb.append("]");
        return sb.toString();
    }
    
    

}
