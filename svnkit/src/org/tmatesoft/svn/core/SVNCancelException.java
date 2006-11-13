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
 * The <b>SVNCancelException</b> is used to signal about an operation 
 * cancel event.
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see		SVNException
 *
 */
public class SVNCancelException extends SVNException {
    /**
     * Creates a cancel exception.
     *
     */
    public SVNCancelException() {
        super(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled"));
    }

    /**
     * Constructs an <b>SVNCancelException</b> given the
     * error message.
     * 
     * @param errorMessage  an error message describing why the operation 
     *                      was cancelled
     */
    public SVNCancelException(SVNErrorMessage errorMessage) {
        super(errorMessage);
    }
}
