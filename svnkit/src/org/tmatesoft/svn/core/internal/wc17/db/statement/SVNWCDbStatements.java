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
package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetStatement;

/**
 * @author TMate Software Ltd.
 */
public enum SVNWCDbStatements {

    CREATE_SCHEMA, 
    INSERT_WCROOT, 
    SELECT_REPOSITORY, 
    INSERT_REPOSITORY, 
    INSERT_BASE_NODE, 
    INSERT_BASE_NODE_INCOMPLETE, 
    INSERT_WORK_ITEM, 
    SELECT_WORKING_NODE(SVNWCDbSelectWorkingNodeStatement.class), 
    SELECT_WCROOT_NULL(SVNWCDbSelectWCRootNullStatement.class), 
    SELECT_BASE_NODE_WITH_LOCK, 
    SELECT_BASE_NODE, SELECT_REPOSITORY_BY_ID;

    private Class<? extends SVNSqlJetStatement> statementClass;

    private SVNWCDbStatements(Class<? extends SVNSqlJetStatement> statementClass) {
        this.statementClass = statementClass;
    }

    private SVNWCDbStatements() {
    }
    
    public Class<? extends SVNSqlJetStatement> getStatementClass() {
        return statementClass;
    }

}
