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
 * @author Alexander Kitaev
 */
public class SVNError {
    
    private int myLineNumber;
    private int myCode;
    private String myLocation;
    private String myMessage;
    
    public SVNError(String message) {
        this(message, null, -1, -1);
    }
    
    public SVNError(String message, String location, int code, int line) {
        myMessage = message;
        myLineNumber = line;
        myCode = code;
        myLocation = location;
    }

    public String getMessage() {
        return myMessage;
    }
    
    public String getLocation() {
        return myLocation;    
    }
    
    public int getCode() {
        return myCode;
    }
    
    public int getLineNumber() {
        return myLineNumber;
    }
    
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
