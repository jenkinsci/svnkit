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
    
    public static final int TYPE_ERROR = 0;
    public static final int TYPE_WARNING = 1;
    
    private Object[] myObjects;
    private String myMessage;
    private SVNErrorCode myErrorCode;
    private int myType;
    private SVNErrorMessage myChildErrorMessage;
    
    private static final Object[] EMPTY_ARRAY = new Object[0];
    
    public static SVNErrorMessage UNKNOWN_ERROR_MESSAGE = create(SVNErrorCode.UNKNOWN);
    
    public static SVNErrorMessage create(SVNErrorCode code) {
        return create(code, "", TYPE_ERROR);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message) {
        return create(code, message, TYPE_ERROR);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object object) {
        return create(code, message, object, TYPE_ERROR);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object[] objects) {
        return create(code, message, objects, TYPE_ERROR);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, int type) {
        return new SVNErrorMessage(code, message, EMPTY_ARRAY, type);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object object, int type) {
        return new SVNErrorMessage(code == null ? SVNErrorCode.BASE : code, message == null ? "" : message, 
                object == null ? new Object[] {"NULL"} : new Object[] {object}, type);
    }

    public static SVNErrorMessage create(SVNErrorCode code, String message, Object[] objects, int type) {
        return new SVNErrorMessage(code == null ? SVNErrorCode.BASE : code, message == null ? "" : message, 
                objects == null ? EMPTY_ARRAY : objects, type);
    }

    protected SVNErrorMessage(SVNErrorCode code, String message, Object[] relatedObjects, int type) {
        myErrorCode = code;
        if (message != null && message.startsWith("svn: ")) {
            message = message.substring("svn: ".length());
        }
        myMessage = message;
        myObjects = relatedObjects;
        myType = type;
    }
    
    public int getType() {
        return myType;
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
    
    public SVNErrorMessage getChildErrorMessage() {
        return myChildErrorMessage;
    }
    
    public boolean hasChildErrorMessage() {
        return myChildErrorMessage != null;
    }
    
    public String toString() {
        StringBuffer line = new StringBuffer();
        line.append("svn: ");
        if (getType() == TYPE_WARNING) {
            line.append("warning: ");
        }
        if ("".equals(myMessage)) {
            line.append(myErrorCode.toString());
        } else {
            line.append(myObjects.length > 0 ? MessageFormat.format(myMessage, myObjects) : myMessage);
        }
        return line.toString();
    }

    public void setChildErrorMessage(SVNErrorMessage childMessage) {
        myChildErrorMessage = childMessage;
    }
}