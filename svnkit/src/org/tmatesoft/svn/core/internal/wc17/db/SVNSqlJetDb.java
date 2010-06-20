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
package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;

import org.tmatesoft.sqljet.core.table.SqlJetDb;


/**
 * @author  TMate Software Ltd.
 */
public class SVNSqlJetDb {
    
    
    public static enum Mode {
        /** open the database read-only */
        ReadOnly,
        /** open the database read-write */
        ReadWrite,
        /** open/create the database read-write */
        RWCreate
    };

    
    private SqlJetDb db;
    
    
    public SqlJetDb getDb() {
        return db;
    }


    public void close(){
    }


    public static SVNSqlJetDb open(File dirAbsPath, String sdbFileName, Mode rwcreate) {
        return null;
    }

    public SVNSqlJetStatement getStatement(SVNWCDbStatements statementIndex) {
        return null;
    }

    public void execStatement(SVNWCDbStatements statementIndex) {        
    }

}
