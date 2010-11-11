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
    DELETE_ACTUAL_EMPTY,
    DELETE_ACTUAL_NODE(SVNWCDbDeleteActualNode.class),
    DELETE_BASE_NODE(SVNWCDbDeleteBaseNode.class),
    DELETE_LOCK,
    DELETE_NODES(SVNWCDbDeleteNodes.class),
    DELETE_PRISTINE,
    DELETE_WC_LOCK(SVNWCDbDeleteWCLock.class),
    DELETE_WORK_ITEM(SVNWCDbDeleteWorkItem.class),
    DELETE_WORKING_NODES,
    DETERMINE_TREE_FOR_RECORDING(SVNWCDbDetermineTreeForRecording.class),
    FIND_WC_LOCK(SVNWCDbFindWCLock.class),
    FSFS_GET_REVPROP(SVNFSFSRevPropGet.class),
    FSFS_SET_REVPROP(SVNFSFSRevPropSet.class),
    INSERT_ACTUAL_CONFLICT_DATA,
    INSERT_ACTUAL_PROPERTY_CONFLICTS,
    INSERT_ACTUAL_PROPS,
    INSERT_ACTUAL_TEXT_CONFLICTS(SVNWCDbInsertActualTextConflicts.class),
    INSERT_ACTUAL_TREE_CONFLICTS(SVNWCDbInsertActualTreeConflicts.class),
    INSERT_BASE_NODE,
    INSERT_BASE_NODE_INCOMPLETE,
    INSERT_NODE(SVNWCDbInsertNode.class),
    INSERT_PRISTINE(SVNWCDbInsertPristine.class),
    INSERT_REPOSITORY,
    INSERT_WC_LOCK(SVNWCDbInsertWCLock.class),
    INSERT_WCROOT,
    INSERT_WORK_ITEM(SVNWCDbInsertWorkItem.class),
    INSERT_WORKING_NODE_FROM_BASE(SVNWCDbInsertWorkingNodeFromBase.class),
    INSERT_WORKING_NODE_NORMAL_FROM_BASE(SVNWCDbInsertWorkingNodeNormalFromBase.class),
    INSERT_WORKING_NODE_NOT_PRESENT_FROM_BASE,
    LOOK_FOR_WORK,
    REVPROP_CREATE_SCHEMA(SVNFSFSRevPropCreateSchema.class),
    SELECT_ACTUAL_CONFLICT_VICTIMS(SVNWCDbSelectActualConflictVictims.class),
    SELECT_ACTUAL_NODE(SVNWCDbSelectActualNode.class),
    SELECT_ACTUAL_PROPS(SVNWCDbSelectActualProperties.class),
    SELECT_ACTUAL_TREE_CONFLICT(SVNWCDbSelectActualTreeConflict.class),
    SELECT_ANY_PRISTINE_REFERENCE,
    SELECT_BASE_DAV_CACHE,
    SELECT_BASE_NODE(SVNWCDbSelectBaseNode.class),
    SELECT_BASE_NODE_CHILDREN(SVNWCDbSelectBaseNodeChildren.class),
    SELECT_BASE_NODE_WITH_LOCK(SVNWCDbSelectBaseNodeWithLock.class),
    SELECT_BASE_PROPS(SVNWCDbSelectBaseProperties.class),
    SELECT_CONFLICT_DETAILS(SVNWCDbSelectConflictDetails.class),
    SELECT_DELETION_INFO(SVNWCDbSelectDeletionInfo.class),
    SELECT_FILE_EXTERNAL(SVNWCDBSelectFileExternal.class),
    SELECT_NODE_PROPS(SVNWCDbSelectNodeProps.class),
    SELECT_NOT_PRESENT(SVNWCDbSelectNotPresent.class),
    SELECT_PRISTINE_MD5_CHECKSUM(SVNWCDbSelectPristineMD5Checksum.class),
    SELECT_PRISTINE_SHA1_CHECKSUM(SVNWCDbSelectSHA1Checksum.class),
    SELECT_REPOSITORY(SVNWCDbSelectRepository.class),
    SELECT_REPOSITORY_BY_ID(SVNWCDbSelectRepositoryById.class),
    SELECT_WC_LOCK(SVNWCDbSelectWCLock.class),
    SELECT_WCROOT_NULL(SVNWCDbSelectWCRootNull.class),
    SELECT_WORK_ITEM(SVNWCDbSelectWorkItem.class),
    SELECT_WORKING_NODE(SVNWCDbSelectWorkingNode.class),
    SELECT_WORKING_NODE_CHILDREN(SVNWCDbSelectWorkingNodeChildren.class),
    SELECT_WORKING_OP_DEPTH_RECURSIVE,
    UPDATE_ACTUAL_CONFLICT_DATA,
    UPDATE_ACTUAL_PROPERTY_CONFLICTS(SVNWCDbUpdateActualPropertyConflicts.class),
    UPDATE_ACTUAL_PROPS(SVNWCDbUpdateActualProps.class),
    UPDATE_ACTUAL_TEXT_CONFLICTS(SVNWCDbUpdateActualTextConflicts.class),
    UPDATE_ACTUAL_TREE_CONFLICTS,
    UPDATE_BASE_NODE_FILEINFO,
    UPDATE_BASE_NODE_PRESENCE_REVNUM_AND_REPOS_PATH(SVNUpdateBaseNodePresenceRevnumAndReposPath.class),
    UPDATE_BASE_REPOS,
    UPDATE_BASE_REVISION(SVNWCDbUpdateBaseRevision.class),
    UPDATE_COPYFROM(SVNWCDbUpdateCopyfrom.class),
    UPDATE_COPYFROM_TO_INHERIT,
    UPDATE_FILE_EXTERNAL,
    UPDATE_NODE_BASE_DEPTH,
    UPDATE_NODE_BASE_EXCLUDED,
    UPDATE_NODE_BASE_PRESENCE(SVNWCDbUpdateNodeBasePresence.class),
    UPDATE_NODE_WORKING_DEPTH,
    UPDATE_NODE_WORKING_EXCLUDED,
    UPDATE_NODE_WORKING_PRESENCE,
    UPDATE_OP_DEPTH,
    UPDATE_WORKING_NODE_FILEINFO(SVNWCDbUpdateWorkingNodeFileinfo.class),
    SELECT_LOWEST_WORKING_NODE(SVNWCDbSelectLowestWorkingNode.class),
    CLEAR_TEXT_CONFLICT(SVNWCDbClearTextConflict.class),
    CLEAR_PROPS_CONFLICT(SVNWCDbClearPropsConflict.class);

    private Class<? extends SVNSqlJetStatement> statementClass;

    private SVNWCDbStatements() {
    }

    private SVNWCDbStatements(Class<? extends SVNSqlJetStatement> statementClass) {
        this.statementClass = statementClass;
    }

    public Class<? extends SVNSqlJetStatement> getStatementClass() {
        return statementClass;
    }

}
