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

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public enum SVNWCDbSchema {

    WCROOT(WCROOT__Fields.class, WCROOT__Indices.class), BASE_NODE(BASE_NODE__Fields.class, BASE_NODE__Fields.class), WORKING_NODE(WORKING_NODE__Fields.class, WORKING_NODE__Indices.class), LOCK(
            LOCK__Fields.class), REPOSITORY(REPOSITORY_Fields.class,REPOSITORY_Indices.class), 
            ACTUAL_NODE(ACTUAL_NODE_Fields.class, ACTUAL_NODE_Indices.class);

    final public Class<? extends Enum> fields;
    final public Class<? extends Enum> indices;

    private SVNWCDbSchema(Class<? extends Enum> fields) {
        this.fields = fields;
        this.indices = Empty.class;
    }

    private SVNWCDbSchema(Class<? extends Enum> fields, Class<? extends Enum> indices) {
        this.fields = fields;
        this.indices = indices;
    }

    public enum Empty {
    }

    public enum WCROOT__Fields {
        id, local_abspath;
    }

    public enum WCROOT__Indices {
        I_LOCAL_ABSPATH;
    }

    public enum BASE_NODE__Fields {
        wc_id, local_relpath, repos_id, repos_relpath, parent_relpath, presence, kind, revnum, checksum, translated_size, changed_rev, changed_date, changed_author, depth, symlink_target, last_mod_time, properties, dav_cache, incomplete_children, file_external;
    }

    public enum BASE_NODE__Indices {
        I_PARENT;
    }

    public enum WORKING_NODE__Fields {
        wc_id, local_relpath, parent_relpath, presence, kind, checksum, translated_size, changed_rev, changed_date, changed_author, depth, symlink_target, copyfrom_repos_id, copyfrom_repos_path, copyfrom_revnum, moved_here, moved_to, last_mod_time, properties, keep_local;
    }

    public enum WORKING_NODE__Indices {
        I_WORKING_PARENT;
    }

    public enum LOCK__Fields {
        repos_id, repos_relpath, lock_token, lock_owner, lock_comment, lock_date
    }
    
    public enum REPOSITORY_Fields {
        id, root, uuid
    }
    
    public enum REPOSITORY_Indices {
        I_UUID, I_ROOT
    }
    
    public enum ACTUAL_NODE_Fields {
        wc_id,
        local_relpath,
        parent_relpath,
        properties,
        conflict_old,
        conflict_new,
        conflict_working,
        prop_reject,
        changelist,
        text_mod,
        tree_conflict_data,
        conflict_data,
        older_checksum,
        left_checksum,
        right_checksum;
    }
    
    public enum ACTUAL_NODE_Indices{
        I_ACTUAL_PARENT, I_ACTUAL_CHANGELIST
    }

}
