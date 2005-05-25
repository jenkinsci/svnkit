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

import java.util.Collection;

/**
 * An exception class that is used to signal about the fact that errors
 * occured exactly during an authentication try. Provides the same kind of information
 * as its base class does.
 *   
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNException
 */
public class SVNAuthenticationException extends SVNException {

    private static final long serialVersionUID = 1L;
    
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

    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an array of 
     * <code>SVNError</code> instances that are used to store information of each 
     * error occured during a repository server's response to a client's command.
     *  
     * @param errors	an array of errors occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNAuthenticationException(SVNError[] errors) {
        super(errors);
    }

    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an error 
     * description message and an array of <code>SVNError</code> instances that 
     * are used to store information of each error occured during a repository 
     * server's response to a client's command.
     * 
     * @param message	a description of why the exception has occured
     * @param errors	an array of errors occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNAuthenticationException(String message, SVNError[] errors) {
        super(message, errors);
    }

    /**
     * Constructs an <code>SVNAuthenticationException</code> provided an 
     * <code>SVNError</code> instance that is used to store information of an error
     * occured during a repository server's response to a client's command.
     * 
     * @param error		an error occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNAuthenticationException(SVNError error) {
        super(error);
    }

    /**
     * The same as {@link #SVNAuthenticationException(String, SVNError[])} except for 
     * <code>SVNError</code> instances are provided as a <code>Collection</code>
     * 
     * @param message	a description of why the exception has occured
     * @param errors	a <code>Collection</code> of errors occured during a server's 
     * 					response to a client's command
     * @see				SVNError
     */
    public SVNAuthenticationException(String message, Collection errors) {
        super(message, errors);
    }

}
