/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNErrorManager {

    public static void cancel(String message) throws SVNCancelException {
        throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, message));
    }

    public static void authenticationFailed(String message, Object messageObject) throws SVNAuthenticationException {
        throw new SVNAuthenticationException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, message, messageObject));
    }
    
    public static void error(SVNErrorMessage err) throws SVNException {
        if (err == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
        }
        if (err.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err);
        } else if (err.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err);
        } else {
            throw new SVNException(err);
        }
    }

    public static void error(SVNErrorMessage err, Throwable cause) throws SVNException {
        if (err == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
        }
        if (err.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err);
        } else if (err.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err);
        } else {
            throw new SVNException(err, cause);
        }
    }

    public static void error(SVNErrorMessage err1, SVNErrorMessage err2) throws SVNException {
        if (err1 == null) {
            error(err2);
        } else if (err2 == null) {
            error(err1);
        }
        err1.setChildErrorMessage(err2);
        if (err1.getErrorCode() == SVNErrorCode.CANCELLED || err2.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException(err1);
        } else if (err1.getErrorCode().isAuthentication() || err2.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(err1);
        } 
        throw new SVNException(err1);
    }
}
