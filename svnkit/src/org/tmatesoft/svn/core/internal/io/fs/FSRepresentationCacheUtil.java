/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class FSRepresentationCacheUtil {
    
    private static volatile boolean ourIsAvailable;
    private static final String SQLJET_DB_CLASS_NAME = "org.tmatesoft.sqljet.core.table.SqlJetDb";
    
    static {
        try {
            Class clazz = FSRepresentationCacheUtil.class.getClassLoader().loadClass(SQLJET_DB_CLASS_NAME);
            ourIsAvailable = clazz != null; 
        } catch (Throwable e) {
            ourIsAvailable = false;
        }
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, "SQLJET enabled: " + ourIsAvailable);
    }
    
    public static FSRepresentationCacheManager open(FSFS fsfs) throws SVNException {
        if (!isAvailable()) {
            return null;
        }
        return FSRepresentationCacheManager.openRepresentationCache(fsfs);
    }

    public static void create(File path) throws SVNException {
        if (!isAvailable()) {
            return;
        }
        FSRepresentationCacheManager.createRepresentationCache(path);
    }
    
    private static boolean isAvailable() {
        return ourIsAvailable;
    }

}
