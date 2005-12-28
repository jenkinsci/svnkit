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

import java.text.MessageFormat;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNErrorMessage {
    
    private Object[] myObjects;
    private String myMessage;
    private SVNErrorCode myErrorCode;
    
    private static final Object[] EMPTY_ARRAY = new Object[0];
    
    public static SVNErrorMessage UNKNOWN_ERROR_MESSAGE = create(SVNErrorCode.UNKNOWN);
    
    public static SVNErrorMessage create(SVNErrorCode code) {
        return create(code, "");
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message) {
        return new SVNErrorMessage(code, message, EMPTY_ARRAY);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object object) {
        return new SVNErrorMessage(code == null ? SVNErrorCode.BASE : code, message == null ? "" : message, 
                object == null ? new Object[] {"NULL"} : new Object[] {object});
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object[] objects) {
        return new SVNErrorMessage(code == null ? SVNErrorCode.BASE : code, message == null ? "" : message, 
                objects == null ? EMPTY_ARRAY : objects);
    }

    protected SVNErrorMessage(SVNErrorCode code, String message, Object[] relatedObjects) {
        myErrorCode = code;
        if (message != null && message.startsWith("svn: ")) {
            message = message.substring("svn: ".length());
        }
        myMessage = message;
        myObjects = relatedObjects;
    }
    
    public SVNErrorCode getErrorCode() {
        return myErrorCode;
    }
    
    public String getMessage() {
        return toString();
    }
    
    public String getMessageTemplate() {
        return myMessage;
    }
    
    public Object[] getRelatedObjects() {
        return myObjects;
    }    
    
    public String toString() {
        if ("".equals(myMessage)) {
            return "svn: " + myErrorCode.toString();
        }
        return "svn: " + (myObjects.length > 0 ? MessageFormat.format(myMessage, myObjects) : myMessage);
    }
}