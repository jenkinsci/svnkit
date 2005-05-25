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
 * @see		SVNAuthenticationException
 * @see		SVNError
 * 
 */
public class SVNException extends Exception {

    private static final long serialVersionUID = 1661853897041563030L;
    
    private SVNError[] myErrors;
   
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
    
    /**
     * Constructs an <code>SVNException</code> provided an array of 
     * <code>SVNError</code> instances that are used to store information of each 
     * error occured during a repository server's response to a client's command.
     *  
     * @param errors	an array of errors occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNException(SVNError[] errors) {
        this("", errors);
        
    }
    
    /**
     * Constructs an <code>SVNException</code> provided an error description message 
     * and an array of <code>SVNError</code> instances that are used to store 
     * information of each error occured during a repository server's response to a 
     * client's command.
     * 
     * @param message	a description of why the exception has occured
     * @param errors	an array of errors occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNException(String message, SVNError[] errors) {
        super(message);
        myErrors = errors;
        
    }
    
    /**
     * Constructs an <code>SVNException</code> provided an <code>SVNError</code>
     * instance that is used to store information of an error occured during a 
     * repository server's response to a client's command.
     * 
     * @param error		an error occured during a server's response
     * 					to a client's command
     * @see				SVNError
     */
    public SVNException(SVNError error) {
        this(new SVNError[] {error});
    }
    
    /**
     * The same as {@link #SVNException(String, SVNError[])} except for 
     * <code>SVNError</code> instances are provided as a <code>Collection</code>
     * 
     * @param message	a description of why the exception has occured
     * @param errors	a <code>Collection</code> of errors occured during a server's 
     * 					response to a client's command
     * @see				SVNError
     */
    public SVNException(String message, Collection errors) {
        super(message);
        myErrors = (SVNError[]) errors.toArray(new SVNError[errors.size()]);
    }
    
    /**
     * Returns an array of stored errors occured during a repository server's response
     * to a client's command.
     * 
     * @return	an array of server response errors
     * @see		SVNError 
     */
    public SVNError[] getErrors() {
        return myErrors;
    }
    
    /**
     * Gets an error description message provided for this object.
     * 
     * @return	an exception description message
     */
    public String getMessage() {
        if (myErrors == null || myErrors.length == 0) {
            return super.getMessage();
        }
        StringBuffer sb  = new StringBuffer();
        sb.append(super.getMessage());
        for(int i = 0; i < myErrors.length; i++) {
            sb.append("\n");
            sb.append(myErrors[i].getMessage());            
        }
        return sb.toString();
    }
}
