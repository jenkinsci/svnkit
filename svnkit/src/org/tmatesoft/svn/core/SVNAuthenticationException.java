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

/**
 * An exception class that is used to signal about the fact that errors
 * occured exactly during an authentication try. Provides the same kind 
 * of information as its base class does.
 *   
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see		SVNException
 */
public class SVNAuthenticationException extends SVNException {

    /**
     * Creates a new authentication exception given detailed error 
     * information and the original cause.
     * 
     * @param errorMessage an error message
     * @param cause        an original cause
     */
    public SVNAuthenticationException(SVNErrorMessage errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }

    /**
     * Creates a new authentication exception given detailed error 
     * information.
     * 
     * @param errorMessage an error message
     */
    public SVNAuthenticationException(SVNErrorMessage errorMessage) {
        super(errorMessage);
    }
}
