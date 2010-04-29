/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    private static final String ANTLR_CLASS_NAME = "org.antlr.runtime.Token";
    private static final String REPCACHE_MANAGER_CLASS_NAME = "org.tmatesoft.svn.core.internal.io.fs.repcache.FSRepresentationCacheManager";

    private static Method ourOpenMethod = null;
    private static Method ourCreateMethod = null;
    
    static {
        Boolean option = Boolean.valueOf(System.getProperty("svnkit.fsfs.repcache", "true"));
        if (option.booleanValue()) {
            try {
                Class antlrClazz = FSRepresentationCacheUtil.class.getClassLoader().loadClass(ANTLR_CLASS_NAME);
                if (antlrClazz == null) {
                    ourIsAvailable = false;
                } else {
                    Class clazz = FSRepresentationCacheUtil.class.getClassLoader().loadClass(SQLJET_DB_CLASS_NAME);
                    ourIsAvailable = clazz != null;
                    clazz = FSRepresentationCacheUtil.class.getClassLoader().loadClass(REPCACHE_MANAGER_CLASS_NAME);
                    if (clazz != null) {
                        ourOpenMethod = clazz.getMethod("openRepresentationCache", new Class[] {FSFS.class});
                        ourCreateMethod = clazz.getMethod("createRepresentationCache", new Class[] {File.class});
                    }
                }
            } catch (Throwable e) {
                ourIsAvailable = false;
            }
        } else {
            ourIsAvailable = false;
        }
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, "SQLJET enabled: " + ourIsAvailable);
    }
    
    public static IFSRepresentationCacheManager open(FSFS fsfs) throws SVNException {
        if (!isAvailable()) {
            return null;
        }
        if (ourOpenMethod != null) {
            try {
                Object result = ourOpenMethod.invoke(null, new Object[] {fsfs});
                if (result instanceof IFSRepresentationCacheManager) {
                    return (IFSRepresentationCacheManager) result;
                }
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SVNException) {
                    throw ((SVNException) e.getCause());
                }
            } catch (Throwable th) {
                
            }
        }
        return null;
    }

    public static void create(File path) throws SVNException {
        if (!isAvailable()) {
            return;
        }
        if (ourCreateMethod != null) {
            try {
                ourCreateMethod.invoke(null, new Object[] {path});
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SVNException) {
                    throw ((SVNException) e.getCause());
                }
            } catch (Throwable th) {
                
            }
        }
    }
    
    private static boolean isAvailable() {
        return ourIsAvailable;
    }

}
