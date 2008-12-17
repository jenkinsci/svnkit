/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core;


/**
 * A main exception class that is used in the SVNKit library. All other
 * SVNKit exception classes extend this one. Detailed information 
 * on the error (description, error code) is encapsulated inside an error 
 * message that is held by an <b>SVNException</b>. 
 *  
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNException extends Exception {
    
    private static final long serialVersionUID = 4845L;
    
    private SVNErrorMessage myErrorMessage;

    /**
     * Creates an exception given an error message. 
     * 
     * @param errorMessage an error message
     */
    public SVNException(SVNErrorMessage errorMessage) {
        this(errorMessage, errorMessage.getCause());
    }
    
    /**
     * Creates an exception given an error message and the cause exception.
     * 
     * @param errorMessage an error message
     * @param cause        the real cause of the error
     */
    public SVNException(SVNErrorMessage errorMessage, Throwable cause) {
        super(findCause(errorMessage,cause));
//      this can create cyclic reference among messages, if cause already contains errorMessage as a child
//        if (cause instanceof SVNException) {
//            SVNErrorMessage childMessages = ((SVNException) cause).getErrorMessage();
//            SVNErrorMessage parent = errorMessage;
//            while(parent.hasChildErrorMessage()) {
//                parent = parent.getChildErrorMessage();
//            }
//            if (parent != childMessages) {
//                parent.setChildErrorMessage(childMessages);
//            }
//        }

        myErrorMessage = errorMessage;
    }

    private static Throwable findCause(SVNErrorMessage errorMessage, Throwable cause) {
        if(cause!=null) return cause;

        // SVNErrorMessage has its own chaining mechanism, so if no 'cause' is given, try to pick up
        // the nearset exception from there, so that printStackTrace() would print the root cause.
        for( SVNErrorMessage e = errorMessage; e!=null; e=e.getChildErrorMessage() )
            if(e.getCause()!=null)
                return e.getCause();
        return null;
    }

    /**
     * Returns an error message provided to this exception object.
     * 
     * @return an error message that contains details on the error
     */
    public SVNErrorMessage getErrorMessage() {
        return myErrorMessage;
    }
    
    /**
     * Returns the informational message describing the cause
     * of this exception.
     * 
     * @return an informational message
     */
    public String getMessage() {
        SVNErrorMessage error = getErrorMessage();
        if (error != null) {
            return error.getFullMessage();
        }
        return super.getMessage();
    }
}
