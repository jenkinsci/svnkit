/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public abstract class AbstractSVNTestValidator {

    public abstract void validate() throws SVNException;

    public void fail(String message, SVNTestErrorCode errorCode) throws SVNException {
//        SVNErrorManager.error(SVNErrorMessage.create(errorCode, "FAILED:\n" + message), SVNLogType.DEFAULT);
       SVNTestDebugLog.log("FAILED:\n" + message);
    }

    public void success() {
        SVNTestDebugLog.log("PASSED");
    }
}
