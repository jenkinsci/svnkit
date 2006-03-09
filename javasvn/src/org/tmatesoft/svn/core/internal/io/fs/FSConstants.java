/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSConstants {
//    public static String SVN_REPOS_README = "README.txt";
    public static final String SVN_REPOS_DB_DIR = "db";
    public static final String SVN_REPOS_DAV_DIR = "dav";
    public static final String SVN_REPOS_LOCKS_DIR = "locks";
    public static final String SVN_REPOS_CONF_DIR = "conf";
    public static final String SVN_REPOS_TXNS_DIR = "transactions";
    public static final String TXN_PATH_EXT = ".txn";
    public static final String TXN_PATH_EXT_CHILDREN = ".children";
    public static final String PATH_PREFIX_NODE = "node.";
    public static final String TXN_PATH_EXT_PROPS = ".props";
    //txn specific paths
    public static final String TXN_PATH_REV = "rev";
    public static final String TXN_PATH_CHANGES = "changes";
    public static final String TXN_PATH_NEXT_IDS = "next-ids";
    public static final String TXN_PATH_TXN_PROPS = "props";
    public static final String SVN_REPOS_DB_LOCKFILE = "db.lock";
    public static final String SVN_REPOS_DB_LOGS_LOCKFILE = "db-logs.lock";
    public static final String SVN_REPOS_CONF_SVNSERVE_CONF_FILE = "svnserve.conf";
    public static final String SVN_REPOS_CONF_PASSWD_FILE = "passwd";
    public static final String SVN_REPOS_FSFS_FORMAT = "fsfs";
    public static final String SVN_REPOS_DB_CURRENT_FILE = "current";
    public static final String SVN_REPOS_FORMAT_FILE = "format";
    public static final int SVN_REPOS_FORMAT_NUMBER = 3;
    public static final String SVN_REPOS_FS_FORMAT_FILE = "format";
    public static final int SVN_FS_FORMAT_NUMBER = 1;
    public static final String SVN_REPOS_FS_TYPE_FILE = "fs-type";
    public static final String SVN_REPOS_UUID_FILE = "uuid";
    public static final String SVN_REPOS_REVPROPS_DIR = "revprops";
    public static final String SVN_REPOS_REVS_DIR = "revs";
    public static final String SVN_REPOS_WRITE_LOCK_FILE = "write-lock";

    //the following are keys that appear in digest lock file
    public static final String PATH_LOCK_KEY = "path";
    public static final String CHILDREN_LOCK_KEY = "children";
    public static final String TOKEN_LOCK_KEY = "token";
    public static final String OWNER_LOCK_KEY = "owner";
    public static final String IS_DAV_COMMENT_LOCK_KEY = "is_dav_comment";
    public static final String CREATION_DATE_LOCK_KEY = "creation_date";
    public static final String EXPIRATION_DATE_LOCK_KEY = "expiration_date";
    public static final String COMMENT_LOCK_KEY = "comment";
    //rev-node files keywords
    public static final String HEADER_ID = "id";
    public static final String HEADER_TYPE = "type";
    public static final String HEADER_COUNT = "count";
    public static final String HEADER_PROPS = "props";
    public static final String HEADER_TEXT = "text";
    public static final String HEADER_CPATH = "cpath";
    public static final String HEADER_PRED = "pred";
    public static final String HEADER_COPYFROM = "copyfrom";
    public static final String HEADER_COPYROOT = "copyroot";
    public static final String REP_DELTA = "DELTA";
    public static final String REP_PLAIN = "PLAIN";
    public static final String REP_TRAILER = "ENDREP";
    public static final int MD5_DIGESTSIZE = 16;
    /* The alphanumeric keys passed in and out of nextKey()
     * are guaranteed never to be longer than this many bytes.
     * It is therefore safe to declare a key as "byte[MAX_KEY_SIZE] key".
     * Note that this limit will be a problem if the number of
     * keys in a table ever exceeds

        18217977168218728251394687124089371267338971528174
        76066745969754933395997209053270030282678007662838
        67331479599455916367452421574456059646801054954062
        15017704234999886990788594743994796171248406730973
        80736524850563115569208508785942830080999927310762
        50733948404739350551934565743979678824151197232629
        947748581376,

       but that's a risk we'll live with for now. 
    */
    public static final int MAX_KEY_SIZE = 200;
    
    //transaction beginning bitmask flags
    /* Do on-the-fly out-of-dateness checks.  That is, an fs routine may
     * throw error if a caller tries to edit an out-of-date item in the
     * transaction.  
     * 
     * Not yet implemented. 
     */
    public static final int SVN_FS_TXN_CHECK_OUT_OF_DATENESS = 0x00001;
    /* Do on-the-fly lock checks.  That is, an fs routine may throw error
     * if a caller tries to edit a locked item without having rights to the lock.
     */
    public static final int SVN_FS_TXN_CHECK_LOCKS = 0x00002;

    // uuid format - 36 symbols
    public static final int SVN_UUID_FILE_LENGTH = 36;
    
    /* Number of characters from the head of a digest file name used to
     * calculate a subdirectory in which to drop that file. 
     */
    public static final int DIGEST_SUBDIR_LEN = 3;
    
    //invalid revision number, suppose it to be -1
    public static final int SVN_INVALID_REVNUM = -1;
    
    public static final String FLAG_TRUE = "true";
    public static final String FLAG_FALSE = "false";
    
    public static final int SVN_STREAM_CHUNK_SIZE = 102400;
    
    /* used for constructing lock token */
    public static final String SVN_OPAQUE_LOCK_TOKEN = "opaquelocktoken:";
    
    /* determines dir for locks */
    public static final String LOCK_ROOT_DIR = "locks";
}
