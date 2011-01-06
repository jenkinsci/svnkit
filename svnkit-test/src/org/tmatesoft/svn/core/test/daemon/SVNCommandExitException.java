/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test.daemon;

class SVNCommandExitException extends SecurityException {
    
    private static final long serialVersionUID = 1L;
    
    private int myCode;

    public SVNCommandExitException(int code) {
        myCode = code;
    }
    public int getCode() {
        return myCode;
    }
}