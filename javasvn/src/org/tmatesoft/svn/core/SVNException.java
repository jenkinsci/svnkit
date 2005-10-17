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


/**
 * A main exception class that is used in the JavaSVN library. All other
 * JavaSVN exception classes extend this one. 
 *  
 * @version	1.0
 * @author 	TMate Software Ltd.
 */
public class SVNException extends Exception {

    /**
     * A default constructor.
     *
     */
    public SVNException() {
    }
    
    /**
     * Constructs an <b>SVNException</b> provided an error 
     * description message.
     * 
     * @param message	an informational message
     */
    public SVNException(String message) {
        super(message);
    }
    
    /**
     * Constructs an <b>SVNException</b> provided an error description 
     * message and an original exception - the cause of this exception.
     * 
     * @param message	an informational message
     * @param cause		an initial cause of this exception 
     */
    public SVNException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs an <code>SVNException</code> provided an original 
     * <code>Throwable</code> - as a real cause of the exception.
     * 
     * @param cause		an initial cause of this exception 
     */
    public SVNException(Throwable cause) {
        super(cause);
    }
    
    /**
     * Returns the informational message describing the cause
     * of this exception.
     * 
     * @return an informational message
     */
    public String getMessage() {
        StringBuffer message = new StringBuffer();
        if (super.getMessage() != null && !"".equals(super.getMessage().trim())) {
            message.append(super.getMessage());
        }
        if (getCause() instanceof SVNException) {
            message.append("\n");
            message.append(((SVNException) getCause()).getMessage());
        }
        return message.toString();
    }
}
