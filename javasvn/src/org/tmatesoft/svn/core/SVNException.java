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
    
    private SVNErrorMessage[] myErrorMessages;

    public SVNException(SVNErrorMessage errorMessage, Throwable cause) {
        this(new SVNErrorMessage[] {errorMessage == null ? SVNErrorMessage.UNKNOWN_ERROR_MESSAGE : errorMessage}, cause);
    }

    public SVNException(SVNErrorMessage[] errorMessages, Throwable cause) {
        super(cause);
        if (cause instanceof SVNException) {
            SVNErrorMessage[] nestedMessages = ((SVNException) cause).getErrorMessages();
            errorMessages = append(errorMessages, nestedMessages);
        }
        myErrorMessages = errorMessages;
    }

    public SVNException(SVNErrorMessage errorMessage) {
        this(errorMessage, null);
    }

    public SVNException(SVNErrorMessage[] errorMessages) {
        this(errorMessages, null);
    }
    
    public SVNErrorMessage getErrorMessage() {
        if (myErrorMessages != null && myErrorMessages.length > 0) {
            return myErrorMessages[0];
        }
        return null;
    }

    public SVNErrorMessage[] getErrorMessages() {
        return myErrorMessages;
    }
    
    /**
     * Returns the informational message describing the cause
     * of this exception.
     * 
     * @return an informational message
     */
    public String getMessage() {
        StringBuffer message = new StringBuffer();
        if (myErrorMessages != null && myErrorMessages.length > 0) {
            for (int i = 0; i < myErrorMessages.length; i++) {
                SVNErrorMessage err = myErrorMessages[i];
                if (err != null) {
                    message.append(err.toString());
                    message.append("\n");
                }
            }
        }
        return message.toString();
    }
    
    private static SVNErrorMessage[] append(SVNErrorMessage[] e1, SVNErrorMessage[] e2) {
        if (e1 == null) {
            return e2;
        } else if (e2 == null) {
            return e1;
        } 
        SVNErrorMessage[] result = new SVNErrorMessage[e1.length + e2.length];
        System.arraycopy(e1, 0, result, 0, e1.length);
        System.arraycopy(e2, 0, result, e1.length, e2.length);
        return result;
    }
}
