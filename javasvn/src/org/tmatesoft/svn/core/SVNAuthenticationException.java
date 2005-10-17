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
 * An exception class that is used to signal about the fact that errors
 * occured exactly during an authentication try. Provides the same kind 
 * of information as its base class does.
 *   
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNException
 */
public class SVNAuthenticationException extends SVNException {

    /**
     * A default constructor.
     *
     */
    public SVNAuthenticationException() {
    }
    
    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an error 
     * description message.
     * 
     * @param message	a description of why the exception has occured
     */
    public SVNAuthenticationException(String message) {
        super(message);
    }
    
    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an error 
     * description message and an original <code>Throwable</code> - as a real cause
     * of the exception.
     * 
     * @param message	a description of why the exception has occured
     * @param cause		an initial cause of this exception 
     */
    public SVNAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an original 
     * <code>Throwable</code> - as a real cause of the exception.
     * 
     * @param cause		an initial cause of this exception 
     */
    public SVNAuthenticationException(Throwable cause) {
        super(cause);
    }
}
