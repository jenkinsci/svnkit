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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.logging.Level;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.db.SVNSqlJetUtil;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea17Factory extends SVNAdminAreaFactory {
    public static final int WC_FORMAT = SVNAdminAreaFactory.WC_FORMAT_17;

    /**
     * @param area
     * @return
     * @throws SVNException
     */
    protected SVNAdminArea doChangeWCFormat(SVNAdminArea area) throws SVNException {
        return null;
    }

    /**
     * @param path
     * @param logLevel
     * @return
     * @throws SVNException
     */
    protected int doCheckWC(File path, Level logLevel) throws SVNException {
        return getVersion(path);
    }

    /**
     * @param path
     * @param url
     * @param rootURL
     * @param uuid
     * @param revNumber
     * @param depth
     * @throws SVNException
     */
    protected void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException {
    }

    /**
     * @param path
     * @param version
     * @return
     * @throws SVNException
     */
    protected SVNAdminArea doOpen(File path, int version) throws SVNException {
        if (version != getSupportedVersion()) {
            return null;
        }
        return new SVNAdminArea17(path);
    }

    /**
     * @return
     */
    public int getSupportedVersion() {
        return WC_FORMAT;
    }

    /**
     * @param path
     * @return
     * @throws SVNException
     */
    protected int getVersion(File path) throws SVNException {
        File sdbFile = SVNAdminUtil.getSDBFile(path); 
        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(sdbFile, false);
            return db.getOptions().getUserVersion();
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (SqlJetException e) {
                    SVNSqlJetUtil.convertException(e);
                }
            }
        }
        return 0;
    }

}
