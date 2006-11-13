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

import java.io.Serializable;
import java.text.MessageFormat;


/**
 * The <b>SVNErrorMessage</b> class represents error and warning messages describing 
 * reasons of exceptions occurred during runtime. An error message may be of two levels: 
 * <ul>
 * <li>Error type</li>
 * <li>Warning type</li>
 * </ul>
 * An error message may contain an error messages stack trace, what is useful for 
 * error reason investigations. Also such a message contains an error code ({@link SVNErrorCode})
 * what gives an ability to find out what kind of an error it is.    
 * 
 * <p>
 * Error messages may be formatted. <b>SVNErrorMessage</b> performs formatting with the 
 * help of the JDK's {@link MessageFormat} class. To make a formatted message, use 
 * {@link MessageFormat} parsable format patterns and provide an array of related objects 
 * when creating an <b>SVNErrorMessage</b>.  
 * 
 * <p>
 * Error messages may be supplied within exceptions of the main exception type - 
 * {@link SVNException}.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNErrorMessage implements Serializable {
    /**
     * Error messages of this type are considered to be errors (most critical) rather 
     * than warnings. 
     */
    public static final int TYPE_ERROR = 0;
    /**
     * Error messages of this type are considered to be warnings, what in certain 
     * situations may be OK. 
     */
    public static final int TYPE_WARNING = 1;
    
    private Object[] myObjects;
    private String myMessage;
    private SVNErrorCode myErrorCode;
    private int myType;
    private SVNErrorMessage myChildErrorMessage;
    
    private static final Object[] EMPTY_ARRAY = new Object[0];
    
    /**
     * This is a type of an error message denoting an error of an unknown nature.
     * This corresponds to an {@link SVNErrorCode#UNKNOWN} error.
     */
    public static SVNErrorMessage UNKNOWN_ERROR_MESSAGE = create(SVNErrorCode.UNKNOWN);
    
    /**
     * Creates an error message given an error code.
     * 
     * @param   code  an error code
     * @return        a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code) {
        return create(code, "", TYPE_ERROR);
    }

    /**
     * Creates an error message given an error code and description.
     * 
     * @param  code      an error code
     * @param  message   an error description   
     * @return           a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code, String message) {
        return create(code, message, TYPE_ERROR);
    }

    /**
     * Creates an error message given an error code, description and may be a related 
     * object to be formatted with the error description. 
     * To format the provided <code>object</code> with the <code>message</code>, you 
     * should use valid format patterns parsable for {@link MessageFormat}.
     * 
     * @param  code       an error code
     * @param  message    an error description
     * @param  object     an object related to the error <code>message</code>
     * @return            a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code, String message, Object object) {
        return create(code, message, object, TYPE_ERROR);
    }
    
    /**
     * Creates an error message given an error code, description and may be related 
     * objects to be formatted with the error description. 
     * To format the provided <code>objects</code> with the <code>message</code>, you 
     * should use valid format patterns parsable for {@link MessageFormat}.
     * 
     * @param  code       an error code
     * @param  message    an error description
     * @param  objects    an array of objects related to the error <code>message</code>
     * @return            a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code, String message, Object[] objects) {
        return create(code, message, objects, TYPE_ERROR);
    }
    
    /**
     * Creates an error message given an error code, description and a type (
     * whether it's a warning or an error). 
     *  
     * @param  code       an error code
     * @param  message    an error description
     * @param  type       an error type 
     * @return            a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code, String message, int type) {
        return new SVNErrorMessage(code, message, EMPTY_ARRAY, type);
    }
    
    /**
     * Creates an error message given an error code, description, an error type 
     * (whether it's a warning or an error) and may be a related object to be 
     * formatted with the error description. To format the provided <code>object</code> 
     * with the <code>message</code>, you should use valid format patterns parsable for 
     * {@link MessageFormat}.
     * 
     * @param  code       an error code
     * @param  message    an error description
     * @param  object     an object related to the error <code>message</code>
     * @param  type       an error type 
     * @return            a new error message
     */
    public static SVNErrorMessage create(SVNErrorCode code, String message, Object object, int type) {
        return new SVNErrorMessage(code == null ? SVNErrorCode.BASE : code, message == null ? "" : message, 
                object == null ? new Object[] {"NULL"} : new Object[] {object}, type);
    }
    
    /**
     * Creates an error message given an error code, description, an error type 
     * (whether it's a warning or an error) and may be related objects to be 
     * formatted with the error description. To format the provided <code>objects</code> 
     * with the <code>message</code>, you should use valid format patterns parsable for 
     * {@link MessageFormat}.
     * 
     * @param  code       an error code
     * @param  message    an error description
     * @param  objects    an array of objects related to the error <code>message</code>
     * @param  type       an error type 
     * @return            a new error message
     */
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
    
    /**
     * Returns the type of the error (whether it's a warning or an error). 
     * 
     * @return the type of this error message
     */
    public int getType() {
        return myType;
    }
    
    /**
     * Returns the error code of the error. 
     * 
     * @return th error code of the error
     */
    public SVNErrorCode getErrorCode() {
        return myErrorCode;
    }
    
    /**
     * Returns an error description formatted with the 
     * related objects if needed. This call is equivalent to 
     * a call to {@link #toString()}
     * 
     * @return an error message
     */
    public String getMessage() {
        return toString();
    }
    
    /**
     * Gets a string representation of the entire stack trace of 
     * error messages (if they were provided) starting with the initial 
     * cause of the error. 
     * 
     * @return a string representing a full list of error messages
     */
    public String getFullMessage() {
        SVNErrorMessage err = this;            
        StringBuffer buffer = new StringBuffer();
        while (err != null) {
            buffer.append(err.getMessage());
            if (err.hasChildErrorMessage()) {
                buffer.append('\n');
            }
            err = err.getChildErrorMessage();
        }
        return buffer.toString();
    }
    
    /**
     * Returns an error description which may contain message format 
     * patterns.
     * 
     * @return an error description
     */
    public String getMessageTemplate() {
        return myMessage;
    }
    
    /**
     * Returns objects (if any) that were provided to be formatted 
     * with the error description. Objects are formatted by the standard 
     * {@link MessageFormat} engine.
     *  
     * @return an array of objects 
     */
    public Object[] getRelatedObjects() {
        return myObjects;
    }    
    
    /**
     * Returns an error message (if any) that was returned from a 
     * deeper method call. So the topmost error messages have the 
     * entire chain of error messages down to the real error cause.   
     * 
     * @return a child error message object (if this object is not the 
     *         first one)
     */
    public SVNErrorMessage getChildErrorMessage() {
        return myChildErrorMessage;
    }
    
    /**
     * Says if this error message object has got a child error message. 
     * 
     * @return <span class="javakeyword">true</span> if has, 
     *         <span class="javakeyword">false</span> otherwise (for 
     *         example, an initial error message would not have a child
     *         error message) 
     */
    public boolean hasChildErrorMessage() {
        return myChildErrorMessage != null;
    }
    
    /**
     * Returns a string representation of this error message object
     * formatting (if needed) the error description with the provided related objects. 
     * If no error description pattern has been provided, the return 
     * value includes a string representation of the error code (see {@link SVNErrorCode}). 
     * 
     * @return  a string representing this object.
     */
    public String toString() {
        StringBuffer line = new StringBuffer();
        if (getType() == TYPE_WARNING && getErrorCode() == SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED) {
            line.append("Warning: ");
        } else if (getType() == TYPE_WARNING) {
            line.append("svn: warning: ");
        } else {
            line.append("svn: ");
        }
        if ("".equals(myMessage)) {
            line.append(myErrorCode.toString());
        } else {
            line.append(myObjects.length > 0 ? MessageFormat.format(myMessage, myObjects) : myMessage);
        }
        return line.toString();
    }
    
    /**
     * Sets a child error message for this one.
     * 
     * @param childMessage a child error message
     */
    public void setChildErrorMessage(SVNErrorMessage childMessage) {
        myChildErrorMessage = childMessage;
    }
    
    /**
     * Wraps this error message into a new one that is returned as 
     * a parent error message. A parent message is set the error code 
     * of this error message, a new error description and this error 
     * message as its child. 
     *  
     * @param  parentMessage     a parent error description
     * @return                   a parent error message
     */
    public SVNErrorMessage wrap(String parentMessage){
        SVNErrorMessage parentError = SVNErrorMessage.create(this.getErrorCode(), parentMessage);
        parentError.setChildErrorMessage(this);
        return parentError;
    }

    /**
     * Wraps this error message into a new one that is returned as 
     * a parent error message. A parent message is set the error code 
     * of this error message, a new error description and this error 
     * message as its child. 
     *  
     * @param  parentMessage     a parent error description
     * @param  relatedObject     an object to be formatted with <code>parentMessage</code>
     * @return                   a parent error message
     */
    public SVNErrorMessage wrap(String parentMessage, Object relatedObject){
        SVNErrorMessage parentError = SVNErrorMessage.create(this.getErrorCode(), parentMessage, relatedObject);
        parentError.setChildErrorMessage(this);
        return parentError;
    }
    
    /**
     * Returns true if this message is a warning message, not error one.
     * 
     * @return true or false
     */
    public boolean isWarning() {
        return myType == TYPE_WARNING;
    }
    
}