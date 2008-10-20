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

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVServletUtil {
    
    public static String readTxn(File activityFile) throws IOException {
        String txnName = null;
        for (int i = 0; i < 10; i++) {
            txnName = SVNFileUtil.readSingleLine(activityFile);
        }
        return txnName; 
    }
    
}
