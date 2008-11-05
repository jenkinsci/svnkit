/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVServletUtil {
    
    public static FSTransactionInfo openTxn(FSFS fsfs, String txnName) throws DAVException {
        FSTransactionInfo txnInfo = null;
        try {
            txnInfo = fsfs.openTxn(txnName);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_TRANSACTION) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "The transaction specified by the activity does not exist", null);
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "There was a problem opening the transaction specified by this activity.", null);
        }
        return txnInfo;
    }
    
    public static String getTxn(File activitiesDB, String activityID) {
        File activityFile = DAVPathUtil.getActivityPath(activitiesDB, activityID);
        try {
            return DAVServletUtil.readTxn(activityFile);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e.getMessage());
        }
        return null;
    }
    
    public static String readTxn(File activityFile) throws IOException {
        String txnName = null;
        for (int i = 0; i < 10; i++) {
            txnName = SVNFileUtil.readSingleLine(activityFile);
        }
        return txnName; 
    }
    
    public static SVNNodeKind checkPath(FSRoot root, String path) throws DAVException {
        try {
            return root.checkNodeKind(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_DIRECTORY) {
                return SVNNodeKind.NONE;
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Error checking kind of path ''{0}'' in repository", new Object[] { path });
        }
    }
    
}
