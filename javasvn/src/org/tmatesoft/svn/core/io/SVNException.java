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
 * A basic exeption class that provides information on errors/specific situations
 * occured during the work of the <i>JavaSVN</i> library.
 * 
 * <p>
 * Each SQLException provides several kinds of information: 
 * <ul>
 * <li>a string describing the error. This is used as the Java Exception message, 
 * 	   available via the method <code>getMesage()</code>.
 * <li>an array of <code>SVNError</code> instances representing a detailed information
 * 	   about occured errors.
 * 
 * <p>
 * <code>SVNException</code> is also used as a base class for extending - to provide
 * specific cases dependent information of errors (like 
 * <code>SVNAuthenticationException</code>).
 *  
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see     SVNAuthenticationException
 * @see     SVNCancelException
 * 
 */
public class SVNException extends Exception {

    /**
     * A default constructor.
     *
     */
    public SVNException() {
    }
    
    /**
     * Constructs an <code>SVNException</code> provided an error description message.
     * 
     * @param message	a description of why the exception has occured
     */
    public SVNException(String message) {
        super(message);
    }
    
    /**
     * Constructs an <code>SVNException</code> provided an error description message
     * and an original <code>Throwable</code> - as a real cause of the exception.
     * 
     * @param message	a description of why the exception has occured
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
