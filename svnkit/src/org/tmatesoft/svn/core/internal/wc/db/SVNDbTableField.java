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
package org.tmatesoft.svn.core.internal.wc.db;


/**
 * Names of this enum elements must precisely match corresponding table names.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public enum SVNDbTableField {
    id, 
    root, 
    uuid, 
    local_abspath, 
    wc_id, 
    local_relpath, 
    repos_id, 
    repos_relpath, 
    parent_relpath, 
    presence, 
    kind, 
    revnum, 
    checksum, 
    translated_size, 
    changed_rev,
    changed_author, 
    changed_date, 
    depth, 
    symlink_target, 
    last_mod_time, 
    properties, 
    dav_cache, 
    incomplete_children, 
    file_external, 
    compression, 
    size, 
    refcount, 
    copyfrom_repos_id, 
    copyfrom_repos_path, 
    copyfrom_revnum, 
    moved_here, 
    moved_to, 
    keep_local, 
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
    right_checksum,
    lock_token, 
    lock_owner, 
    lock_comment, 
    lock_date, 
    work,
    local_dir_relpath
}
