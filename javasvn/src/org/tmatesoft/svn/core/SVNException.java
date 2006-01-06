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
    
    private SVNErrorMessage myErrorMessage;


    public SVNException(SVNErrorMessage errorMessage) {
        this(errorMessage, null);
    }

    public SVNException(SVNErrorMessage errorMessage, Throwable cause) {
        super(cause);
        if (cause instanceof SVNException) {
            SVNErrorMessage childMessages = ((SVNException) cause).getErrorMessage();
            SVNErrorMessage parent = errorMessage;
            while(parent.hasChildErrorMessage()) {
                parent = parent.getChildErrorMessage();
            }
            parent.setChildErrorMessage(childMessages);
        }
        myErrorMessage = errorMessage;
    }
    
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
