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
 * @author TMate Software Ltd.
 */
public class SVNAuthenticationException extends SVNException {

    private static final long serialVersionUID = 1L;

    public SVNAuthenticationException() {
    }

    public SVNAuthenticationException(String message) {
        super(message);
    }

    public SVNAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SVNAuthenticationException(Throwable cause) {
        super(cause);
    }

    public SVNAuthenticationException(SVNError[] errors) {
        super(errors);
    }

    public SVNAuthenticationException(String message, SVNError[] errors) {
        super(message, errors);
    }

    public SVNAuthenticationException(SVNError error) {
        super(error);
    }

    public SVNAuthenticationException(String message, Collection errors) {
        super(message, errors);
    }

}
