/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;


import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVResponse;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVException extends SVNException {
    private static final long serialVersionUID = 4845L;

    private String myMessage;
    private int myResponseCode;
    private int myErrorID;
    private DAVException myPreviousException;
    private String myTagName;
    private String myNameSpace;
    private DAVResponse myResponse;
    
    public DAVException(String message, int responseCode, SVNErrorMessage error, SVNLogType logType, Level level, DAVException previousException, 
            String tagName, String nameSpace, int errorID, DAVResponse response) {
        super(error);
        myMessage = message;
        myResponseCode = responseCode;
        myPreviousException = previousException;
        myTagName = tagName;
        myNameSpace = nameSpace; 
        myErrorID = errorID;
        myResponse = response;
        SVNDebugLog.getDefaultLog().log(logType, message, level);
    }

    public DAVException(String message, int responseCode, SVNLogType logType) {
        this(message, responseCode, null, logType, Level.FINE, null, null, null, 0, null);
    }

    public DAVException(String message, int responseCode, SVNLogType logType, String tagName, String nameSpace) {
        this(message, responseCode, null, logType, Level.FINE, null, tagName, nameSpace, 0, null);
    }

    public int getErrorID() {
        return myErrorID;
    }

    public String getTagName() {
        return myTagName;
    }
    
    public int getResponseCode() {
        return myResponseCode;
    }
    
    public String getMessage() {
        return myMessage;
    }

    public DAVException getPreviousException() {
        return myPreviousException;
    }

    public String getNameSpace() {
        return myNameSpace;
    }

    public DAVResponse getResponse() {
        return myResponse;
    }

}
