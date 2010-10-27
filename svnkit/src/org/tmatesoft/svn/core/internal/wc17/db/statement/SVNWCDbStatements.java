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

import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropCreateSchema;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropGet;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropSet;

/**
 * @author TMate Software Ltd.
 */
public enum SVNWCDbStatements {

    CREATE_SCHEMA,
    SELECT_WORKING_NODE(SVNWCDbSelectWorkingNode.class),
    SELECT_WCROOT_NULL(SVNWCDbSelectWCRootNull.class),
    SELECT_BASE_NODE(SVNWCDbSelectBaseNode.class),
    SELECT_BASE_NODE_WITH_LOCK(SVNWCDbSelectBaseNodeWithLock.class),
    SELECT_REPOSITORY_BY_ID(SVNWCDbSelectRepositoryById.class),
    SELECT_ACTUAL_NODE(SVNWCDbSelectActualNode.class),
    SELECT_BASE_NODE_CHILDREN(SVNWCDbSelectBaseNodeChildren.class),
    SELECT_WORKING_NODE_CHILDREN(SVNWCDbSelectWorkingNodeChildren.class),
    SELECT_ACTUAL_CONFLICT_VICTIMS(SVNWCDbSelectActualConflictVictims.class),
    SELECT_ACTUAL_TREE_CONFLICT(SVNWCDbSelectActualTreeConflict.class),
    SELECT_ACTUAL_PROPS(SVNWCDbSelectActualProperties.class),
    SELECT_BASE_PROPS(SVNWCDbSelectBaseProperties.class),
    SELECT_FILE_EXTERNAL(SVNWCDBSelectFileExternal.class),
    SELECT_WC_LOCK(SVNWCDbSelectWCLock.class),
    SELECT_PRISTINE_SHA1_CHECKSUM(SVNWCDbSelectSHA1Checksum.class),
    SELECT_PRISTINE_MD5_CHECKSUM(SVNWCDbSelectPristineMD5Checksum.class),
    SELECT_DELETION_INFO(SVNWCDbSelectDeletionInfo.class),
    SELECT_CONFLICT_DETAILS(SVNWCDbSelectConflictDetails.class),
    SELECT_NOT_PRESENT(SVNWCDbSelectNotPresent.class),
    REVPROP_CREATE_SCHEMA(SVNFSFSRevPropCreateSchema.class),
    FSFS_GET_REVPROP(SVNFSFSRevPropGet.class),
    FSFS_SET_REVPROP(SVNFSFSRevPropSet.class),
    UPDATE_BASE_NODE_PRESENCE_REVNUM_AND_REPOS_PATH(SVNUpdateBaseNodePresenceRevnumAndReposPath.class),
    SELECT_NODE_PROPS(SVNWCDbSelectNodeProps.class),
    INSERT_WCROOT,
    SELECT_REPOSITORY,
    INSERT_REPOSITORY,
    INSERT_BASE_NODE,
    INSERT_BASE_NODE_INCOMPLETE,
    INSERT_WORK_ITEM,
    DELETE_WORKING_NODES,
    INSERT_WORKING_NODE_NORMAL_FROM_BASE,
    INSERT_WORKING_NODE_NOT_PRESENT_FROM_BASE,
    UPDATE_COPYFROM,
    DELETE_BASE_NODE,
    DELETE_NODES,
    INSERT_NODE,
    DELETE_WORK_ITEM,
    SELECT_WORK_ITEM,
    SELECT_BASE_DAV_CACHE,
    UPDATE_WORKING_NODE_FILEINFO,
    UPDATE_BASE_NODE_FILEINFO,
    DETERMINE_TREE_FOR_RECORDING,
    UPDATE_ACTUAL_PROPS,
    INSERT_ACTUAL_PROPS,
    UPDATE_ACTUAL_TREE_CONFLICTS,
    INSERT_ACTUAL_TREE_CONFLICTS,
    UPDATE_ACTUAL_CONFLICT_DATA,
    INSERT_ACTUAL_CONFLICT_DATA,
    DELETE_ACTUAL_EMPTY,
    DELETE_LOCK,
    LOOK_FOR_WORK,
    SELECT_ANY_PRISTINE_REFERENCE,
    DELETE_PRISTINE,
    UPDATE_NODE_WORKING_PRESENCE,
    INSERT_WORKING_NODE_FROM_BASE,
    SELECT_WORKING_OP_DEPTH_RECURSIVE,
    UPDATE_OP_DEPTH,
    DELETE_ACTUAL_NODE;

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
