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
 * @author Alexander Kitaev
 */
public class SVNException extends Exception {

    private static final long serialVersionUID = 1661853897041563030L;
    
    private SVNError[] myErrors;

    public SVNException() {
    }
    public SVNException(String message) {
        super(message);
    }
    public SVNException(String message, Throwable cause) {
        super(message, cause);
    }
    public SVNException(Throwable cause) {
        super(cause);
    }
    
    public SVNException(SVNError[] errors) {
        this("", errors);
        
    }
    public SVNException(String message, SVNError[] errors) {
        super(message);
        myErrors = errors;
        
    }

    public SVNException(SVNError error) {
        this(new SVNError[] {error});
    }

    public SVNException(String message, Collection errors) {
        super(message);
        myErrors = (SVNError[]) errors.toArray(new SVNError[errors.size()]);
    }
    
    public SVNError[] getErrors() {
        return myErrors;
    }
    
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
