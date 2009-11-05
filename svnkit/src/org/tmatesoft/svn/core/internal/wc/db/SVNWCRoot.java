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
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.File;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCRoot {
    private int myFormat;
    private SqlJetDb myStorage;
    private File myPath;
    private long myWCId;

    public SVNWCRoot(int format, SqlJetDb storage, File path, long wcId) {
        myFormat = format;
        myStorage = storage;
        myPath = path;
        myWCId = wcId;
    }

    public void dispose() throws SVNException {
        try {
            if (myStorage != null && myStorage.isOpen()) {
                myStorage.close();
            }
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
    }
    
    public int getFormat() {
        return myFormat;
    }
    
    public SqlJetDb getStorage() {
        return myStorage;
    }
    
    public File getPath() {
        return myPath;
    }
    
    public long getWCId() {
        return myWCId;
    }
    
}
