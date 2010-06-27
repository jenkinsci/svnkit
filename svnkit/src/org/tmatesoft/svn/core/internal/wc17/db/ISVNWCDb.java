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
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * Working copy administrative database layer.
 *
 * The design doc mentions three different kinds of trees, BASE, WORKING and
 * ACTUAL. We have different APIs to handle each tree, enumerated below, along
 * with a blurb to explain what that tree represents.
 *
 * <p>
 * Has those next categories of routines:
 *
 * <ul>
 *
 * <li><b>General administractive functions</b></li>
 *
 * <li>(*Base*") <b> BASE tree management</b>
 * <p>
 * BASE should be what we get from the server. The *absolute* pristine copy.
 * Nothing can change it -- it is always a reflection of the repository. You
 * need to use checkout, update, switch, or commit to alter your view of the
 * repository.
 * </li>
 * <li>(*Pristine*) <b> Pristine ("text base") management </b></li>
 * <li>(*Repository) <b> Repository information management </b></li>
 *
 * <li>(op*) <b> Operations on WORKING tree</b></li>
 *
 * <li>(read*) <b> Read operations on the BASE/WORKING tree</b>
 * <p>
 * These functions query information about nodes in ACTUAL, and returns the
 * requested information from the appropriate ACTUAL, WORKING, or BASE tree.
 * <p>
 * For example, asking for the checksum of the pristine version will return the
 * one recorded in WORKING, or if no WORKING node exists, then the checksum
 * comes from BASE.
 * </li>
 *
 * <li>(global*) <b> Operations that alter multiple trees</b></li>
 *
 * <li>(*Lock) <b> Function to manage the LOCKS table. </b></li>
 *
 * <li>(scan*) <b> Functions to scan up a tree for further data </b></li>
 *
 * <li>(*WorkQueue) <b> Work queue manipulation </b></li>
 *
 * </ul>
 *
 * @author TMate Software Ltd.
 */
public interface ISVNWCDb {

    int WC_FORMAT_17 = 16;
    int WC_HAS_WORK_QUEUE = 13;
    long INVALID_FILESIZE = -1;
    long ENTRY_WORKING_SIZE_UNKNOWN = -1;
    long INVALID_REVNUM = -1;
    String SDB_FILE = "wc.db";

    /** Enumerated constants for how to open a WC datastore. */
    enum WCDbOpenMode {
        /** Open in the default mode (r/w now). */
        Default,
        /** Changes will definitely NOT be made. */
        ReadOnly,
        /** Changes will definitely be made. */
        ReadWrite
    }

    /**
     * Enum indicating what kind of versioned object we're talking about.
     */
    enum WCDbKind {
        /** The node is a directory. */
        Dir,

        /** The node is a file. */
        File,

        /** The node is a symbolic link. */
        Symlink,

        /**
         * The type of the node is not known, due to its absence, exclusion,
         * deletion, or incomplete status.
         */
        Unknown,

        /**
         * This directory node is a placeholder; the actual information is held
         * within the subdirectory.
         * <p>
         * Note: users of this API shouldn't see this kind. It will be handled
         * internally to wc_db.
         * <p>
         * ### only used with per-dir .svn subdirectories.
         */
        Subdir;

        public SVNNodeKind toNodeKind() throws SVNException {
            switch (this) {
                case Dir:
                    return SVNNodeKind.DIR;
                case File:
                case Symlink:
                    return SVNNodeKind.FILE;
                case Unknown:
                    return SVNNodeKind.UNKNOWN;
                default:
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL);
                    SVNErrorManager.error(err, SVNLogType.WC);
                    return null;
            }
        }

    }

    /** Enumerated values describing the state of a node. */
    enum WCDbStatus {

        /** The node is present and has no known modifications applied to it. */
        Normal,

        /**
         * The node has been added (potentially obscuring a delete or move of
         * the BASE node; see BASE_SHADOWED param). The text will be marked as
         * modified, and if properties exist, they will be marked as modified.
         * <p>
         * In many cases {@link WCDbStatus#Added} means any of added, moved-here
         * or copied-here. See individual functions for clarification and
         * {@link ISVNWCDb#scanAddition(File, AdditionInfoField...)} to get more
         * details.
         */
        Added,

        /**
         * This node has been added with history, based on the move source. Text
         * and property modifications are based on whether changes have been
         * made against their pristine versions.
         */
        MovedHere,

        /**
         * This node has been added with history, based on the copy source. Text
         * and property modifications are based on whether changes have been
         * made against their pristine versions.
         */
        Copied,

        /**
         * This node has been deleted. No text or property modifications will be
         * present.
         */
        Deleted,

        /**
         * The information for this directory node is obstructed by something in
         * the local filesystem. Full details are not available.
         * <p>
         * This is only returned by an unshadowed BASE node. If a WORKING node
         * is present, then obstructed_delete or obstructed_add is returned as
         * appropriate.
         * <p>
         * ### only used with per-dir .svn subdirectories.
         */
        Obstructed,

        /**
         * The information for this directory node is obstructed by something in
         * the local filesystem. Full details are not available.
         * <p>
         * The directory has been marked for deletion.
         * <p>
         * ### only used with per-dir .svn subdirectories.
         */
        ObstructedDelete,

        /**
         * The information for this directory node is obstructed by something in
         * the local filesystem. Full details are not available.
         * <p>
         * The directory has been marked for addition.
         * <p>
         * ### only used with per-dir .svn subdirectories.
         */
        ObstructedAdd,

        /** This node was named by the server, but no information was provided. */
        Absent,

        /** This node has been administratively excluded. */
        Excluded,

        /**
         * This node is not present in this revision. This typically happens
         * when a node is deleted and committed without updating its parent. The
         * parent revision indicates it should be present, but this node's
         * revision states otherwise.
         */
        NotPresent,

        /**
         * This node is known, but its information is incomplete. Generally, it
         * should be treated similar to the other missing status values until
         * some (later) process updates the node with its data.
         * <p>
         * When the incomplete status applies to a directory, the list of
         * children and the list of its base properties as recorded in the
         * working copy do not match their working copy versions. The update
         * editor can complete a directory by using a different update
         * algorithm.
         */
        Incomplete,

        /**
         * The BASE node has been marked as deleted. Only used as an internal
         * status.
         */
        BaseDeleted

    }

    /**
     * Enumerated constants for how hard
     * {@link ISVNWCDb#checkPristine(File, SVNChecksum, WCDbCheckMode)} should
     * work on checking for the pristine file.
     *
     * <p>
     * Note: this is bogus. we open the sqlite database "all the time", and
     * don't worry about optimizing that. so: given the db is always open, then
     * the following modes are overengineered, premature optimizations. ... will
     * clean up in a future rev.
     */
    enum WCDbCheckMode {

        /**
         * The caller wants to be sure the pristine file is present and usable.
         * This is the typical mode to use.
         * <p>
         * Implementation note: the SQLite database is opened (if not already)
         * and its state is verified against the file in the filesystem.
         */
        Usable,

        /**
         * The caller is performing just this one check. The implementation will
         * optimize around the assumption no further calls to
         * {@link ISVNWCDb#checkPristine(File, SVNChecksum, WCDbCheckMode)} will
         * occur (but of course has no problem if they do).
         * <p>
         * Note: this test is best used for detecting a *missing* file rather
         * than for detecting a usable file.
         * <p>
         * Implementation note: this will examine the presence of the pristine
         * file in the filesystem. The SQLite database is untouched, though if
         * it is (already) open, then it will be used instead.
         */
        Single,

        /**
         * The caller is going to perform multiple calls, so the implementation
         * should optimize its operation around that.
         * <p>
         * Note: this test is best used for detecting a *missing* file rather
         * than for detecting a usable file.
         * <p>
         * Implementation note: the SQLite database will be opened (if not
         * already), and all checks will simply look in the TEXT_BASE table to
         * see if the given key is present. Note that the file may not be
         * present.
         */
        Multi,

        /**
         * Similar to {@link #Usable}, but the file is checksum'd to ensure that
         * it has not been corrupted in some way.
         */
        Validate

    }

    /**
     * Lock information. We write/read it all as one, so let's use a struct for
     * convenience.
     */
    class WCDbLock {

        /** The lock token */
        public String token;

        /** The owner of the lock, possibly <b>null</b> */
        public String owner;

        /** A comment about the lock, possibly <b>null</b> */
        public String comment;

        /** The date the lock was created */
        public Date date;
    }

    /**
     * Open a working copy administrative database context.
     * <p>
     * This context is (initially) not associated with any particular working
     * copy directory or working copy root (wcroot). As operations are
     * performed, this context will load the appropriate wcroot information.
     * <p>
     * It should be closed manually using {@link #close()}. In particular, this
     * will close any SQLite databases that have been opened and cached.
     *
     * @param mode
     *            indicates whether the caller knows all interactions will be
     *            read-only, whether writing will definitely happen, or whether
     *            a default should be chosen.
     * @param config
     *            should hold the various configuration options that may apply
     *            to the administrative operation.
     * @param autoUpgrade
     *            when is <b>true</b>, then the working copy databases will be
     *            upgraded when possible (when an old database is found/detected
     *            during the operation of a {@link ISVNWCDb} API). If it is
     *            detected that a manual upgrade is required, then
     *            {@link SVNErrorCode#WC_UPGRADE_REQUIRED} will be thrown from
     *            that API. Passing <b>false</b> will allow a bare minimum of
     *            APIs to function (most notably, the
     *            {@link #getFormatTemp(File)} function will always return a
     *            value) since most of these APIs expect a current-format
     *            database to be present.
     * @param enforceEmptyWQ
     *            if is <b>true</b>, then any databases with stale work items in
     *            their work queue will raise an error when they are opened. The
     *            operation will raise {@link SVNErrorCode#WC_CLEANUP_REQUIRED}.
     *            Passing <b>false</b> for this routine means that the work
     *            queue is being processed (via 'svn cleanup') and all
     *            operations should be allowed.
     */
    void open(WCDbOpenMode mode, ISVNOptions config, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException;

    /** Close DB. */
    void close() throws SVNException;

    ISVNOptions getConfig();
    
    /**
     * Initialize the SqlDB for LOCAL_ABSPATH, which should be a working copy
     * path.
     * <p>
     * A REPOSITORY row will be constructed for the repository identified by
     * REPOS_ROOT_URL and REPOS_UUID. Neither of these may be NULL.
     * <p>
     * A node will be created for the directory at REPOS_RELPATH will be added.
     * If INITIAL_REV is greater than zero, then the node will be marked as
     * "incomplete" because we don't know its children. Contrary, if the
     * INITIAL_REV is zero, then this directory should represent the root and we
     * know it has no children, so the node is complete.
     * <p>
     * DEPTH is the initial depth of the working copy, it must be a definite
     * depth, not svn_depth_unknown.
     * @throws SqlJetException 
     */
    void init(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long initialRev, SVNDepth depth) throws SVNException, SqlJetException;

    /**
     * Compute the LOCAL_RELPATH for the given LOCAL_ABSPATH.
     * <p>
     * The LOCAL_RELPATH is a relative path to the working copy's root. That
     * root will be located by this function, and the path will be relative to
     * that location. If LOCAL_ABSPATH is the wcroot directory, then "" will be
     * returned.
     * <p>
     * The LOCAL_RELPATH should ONLY be used for persisting paths to disk. Those
     * patsh should not be an abspath, otherwise the working copy cannot be
     * moved. The working copy library should not make these paths visible in
     * its API (which should all be abspaths), and it should not be using
     * relpaths for other processing.
     * <p>
     * note: with per-dir .svn directories, these relpaths will effectively be
     * the basename. it gets interesting in single-db mode
     */
    File toRelPath(File localAbsPath) throws SVNException;

    /**
     * Compute the local abs path for a localRelPath located within the working
     * copy identified by wcRootAbsPath.
     * <p>
     * This is the reverse of {@link #toRelPath(File)}. It should be used for
     * returning a persisted relpath back into an abspath.
     */
    File fromRelPath(File wcRootAbsPath, File localRelPath) throws SVNException;

    /**
     * Add or replace a directory in the BASE tree.
     * <p>
     * The directory is located at LOCAL_ABSPATH on the local filesystem, and
     * corresponds to <REPOS_RELPATH, REPOS_ROOT_URL, REPOS_UUID> in the
     * repository, at revision REVISION.
     * <p>
     * The directory properties are given by the PROPS.
     * <p>
     * The last-change information is given by <CHANGED_REV, CHANGED_DATE,
     * CHANGED_AUTHOR>.
     * <p>
     * The directory's children are listed in CHILDREN. The child nodes do NOT
     * have to exist when this API is called. For each child node which does not
     * exists, an "incomplete" node will be added. These child nodes will be
     * added regardless of the DEPTH value. The caller must sort out which must
     * be recorded, and which must be omitted.
     * <p>
     * This subsystem does not use DEPTH, but it can be recorded here in the
     * BASE tree for higher-level code to use.
     * <p>
     * If CONFLICT is not NULL, then it describes a conflict for this node. The
     * node will be record as conflicted (in ACTUAL).
     * <p>
     * Any work items that are necessary as part of this node construction may
     * be passed in WORK_ITEMS.
     */
    void addBaseDirectory(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate, String changedAuthor,
            List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /**
     * Add or replace a file in the BASE tree.
     * <p>
     * The file is located at LOCAL_ABSPATH on the local filesystem, and
     * corresponds to <REPOS_RELPATH, REPOS_ROOT_URL, REPOS_UUID> in the
     * repository, at revision REVISION.
     * <p>
     * The file properties are given by the PROPS.
     * <p>
     * The last-change information is given by <CHANGED_REV, CHANGED_DATE,
     * CHANGED_AUTHOR>.
     * <p>
     * The checksum of the file contents is given in CHECKSUM. An entry in the
     * pristine text base is NOT required when this API is called.
     * <p>
     * If the translated size of the file (its contents, translated as defined
     * by its properties) is known, then pass it as TRANSLATED_SIZE. Otherwise,
     * pass {@link #INVALID_FILESIZE}.
     * <p>
     * If CONFLICT is not NULL, then it describes a conflict for this node. The
     * node will be record as conflicted (in ACTUAL).
     * <p>
     * Any work items that are necessary as part of this node construction may
     * be passed in WORK_ITEMS.
     */
    void addBaseFile(File localAbspath, File reposRelpath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate, String changedAuthor,
            SVNChecksum checksum, long translatedSize, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /**
     * Add or replace a symlink in the BASE tree.
     * <p>
     * The symlink is located at LOCAL_ABSPATH on the local filesystem, and
     * corresponds to <REPOS_RELPATH, REPOS_ROOT_URL, REPOS_UUID> in the
     * repository, at revision REVISION.
     * <p>
     * The symlink's properties are given by the PROPS.
     * <p>
     * The last-change information is given by <CHANGED_REV, CHANGED_DATE,
     * CHANGED_AUTHOR>.
     * <p>
     * The target of the symlink is specified by TARGET.
     * <p>
     * If CONFLICT is not NULL, then it describes a conflict for this node. The
     * node will be record as conflicted (in ACTUAL).
     * <p>
     * Any work items that are necessary as part of this node construction may
     * be passed in WORK_ITEMS.
     */
    void addBaseSymlink(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, SVNProperties props, long changedRev, Date changedDate, String changedAuthor,
            File target, SVNSkel conflict, SVNSkel workItem) throws SVNException;

    /**
     * Create a node in the BASE tree that is present in name only.
     * <p>
     * The new node will be located at LOCAL_ABSPATH, and correspond to the
     * repository node described by <REPOS_RELPATH, REPOS_ROOT_URL, REPOS_UUID>
     * at revision REVISION.
     * <p>
     * The node's kind is described by KIND, and the reason for its absence is
     * specified by STATUS. Only three values are allowed for STATUS:
     * <ul>
     * <li> {@link WCDbStatus#Absent}</li>
     * <li> {@link WCDbStatus#Excluded}</li>
     * <li> {@link WCDbStatus#NotPresent}</li>
     * </ul>
     * <p>
     * If CONFLICT is not NULL, then it describes a conflict for this node. The
     * node will be record as conflicted (in ACTUAL).
     * <p>
     * Any work items that are necessary as part of this node construction may
     * be passed in WORK_ITEMS.
     */
    void addBaseAbsentNode(File localAbsPath, File reposRelPath, SVNURL reposRootUrl, String reposUuid, long revision, WCDbKind kind, WCDbStatus status, SVNSkel conflict, SVNSkel workItems)
            throws SVNException;

    /**
     * Remove a node from the BASE tree.
     * <p>
     * The node to remove is indicated by LOCAL_ABSPATH from the local
     * filesystem.
     * <p>
     * Note that no changes are made to the local filesystem; LOCAL_ABSPATH is
     * merely the key to figure out which BASE node to remove.
     * <p>
     * If the node is a directory, then ALL child nodes will be removed from the
     * BASE tree, too.
     */
    void removeBase(File localAbsPath) throws SVNException;

    /**
     * Retrieve information about a node in the BASE tree.
     * <p>
     * For the BASE node implied by LOCAL_ABSPATH from the local filesystem,
     * return information in the provided OUT parameters. Each OUT parameter may
     * be NULL, indicating that specific item is not requested.
     * <p>
     * If there is no information about this node, then
     * {@link SVNErrorCode#WC_PATH_NOT_FOUND} will be thrown.
     * <p>
     * The OUT parameters, and their "not available" values are:
     * <table>
     * <tr>
     * <td>STATUS</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>KIND</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>REVISION</td>
     * <td>-1</td>
     * </tr>
     * <tr>
     * <td>REPOS_RELPATH</td>
     * <td>NULL (caller should scan up)</td>
     * </tr>
     * <tr>
     * <td>REPOS_ROOT_URL</td>
     * <td>NULL (caller should scan up)</td>
     * </tr>
     * <tr>
     * <td>REPOS_UUID</td>
     * <td>NULL (caller should scan up)</td>
     * </tr>
     * <tr>
     * <td>CHANGED_REV</td>
     * <td>-1</td>
     * </tr>
     * <tr>
     * <td>CHANGED_DATE</td>
     * <td>0</td>
     * </tr>
     * <tr>
     * <td>CHANGED_AUTHOR</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>LAST_MOD_TIME</td>
     * <td>0</td>
     * </tr>
     * <tr>
     * <td>DEPTH</td>
     * <td> {@link SVNDepth#UNKNOWN}</td>
     * </tr>
     * <tr>
     * <td>CHECKSUM</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>TRANSLATED_SIZE</td>
     * <td> {@link #INVALID_FILESIZE}</td>
     * </tr>
     * <tr>
     * <td>TARGET</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>LOCK</td>
     * <td>NULL</td>
     * </tr>
     * </table>
     * <p>
     * If the STATUS is normal, and the REPOS_* values are NULL, then the caller
     * should use {@link #scanBaseRepository(File, RepositoryInfoField...)} to
     * scan up the BASE tree for the repository information.
     * <p>
     * If DEPTH is requested, and the node is NOT a directory, then the value
     * will be set to svn_depth_unknown. If LOCAL_ABSPATH is a link, it's up to
     * the caller to resolve depth for the link's target.
     * <p>
     * If CHECKSUM is requested, and the node is NOT a file, then it will be set
     * to NULL.
     * <p>
     * If TRANSLATED_SIZE is requested, and the node is NOT a file, then it will
     * be set to {@link #INVALID_FILESIZE}.
     * <p>
     * If TARGET is requested, and the node is NOT a symlink, then it will be
     * set to NULL.
     */
    WCDbBaseInfo getBaseInfo(File localAbsPath, BaseInfoField... fields) throws SVNException;

    class WCDbBaseInfo {

        public enum BaseInfoField {
            status, kind, revision, reposRelPath, reposRootUrl, reposUuid, changedRev, changedDate, changedAuthor, lastModTime, depth, checksum, translatedSize, target, lock
        }

        public WCDbStatus status;
        public WCDbKind kind;
        public long revision;
        public File reposRelPath;
        public SVNURL reposRootUrl;
        public String reposUuid;
        public long changedRev;
        public Date changedDate;
        public String changedAuthor;
        public Date lastModTime;
        public SVNDepth depth;
        public SVNChecksum checksum;
        public long translatedSize;
        public File target;
        public WCDbLock lock;
    }

    /**
     * Return the value of the property named PROPNAME of the node LOCAL_ABSPATH
     * in the BASE tree.
     * <p>
     * If the node has no property named PROPNAME, return NULL. <br>
     * If the node is not present in the BASE tree, throw an error.
     */
    String getBaseProp(File localAbsPath, String propName) throws SVNException;

    /**
     * Return the properties of the node LOCAL_ABSPATH in the BASE tree.
     * <p>
     * If the node has no properties, return an empty hash. It will never return
     * the NULL. <br>
     * If the node is not present in the BASE tree, throw an error.
     */
    Map<String, String> getBaseProps(File localAbsPath) throws SVNException;

    /**
     * Return a list of the BASE tree node's children's names.
     * <p>
     * For the node indicated by LOCAL_ABSPATH, this function will return the
     * names of all of its children in the list CHILDREN.
     * <p>
     * If the node is not a directory, then
     * {@link SVNErrorCode#WC_NOT_WORKING_COPY} will be thrown.
     */
    List<String> getBaseChildren(File localAbsPath) throws SVNException;

    /** Set the dav cache for LOCAL_ABSPATH to PROPS. */
    void setBaseDavCache(File localAbsPath, SVNProperties props) throws SVNException;

    /**
     * Retrieve the dav cache for LOCAL_ABSPATH. Throw
     * {@link SVNErrorCode#WC_PATH_NOT_FOUND} if no dav cache can be located for
     * LOCAL_ABSPATH in DB.
     */
    SVNProperties getBaseDavCache(File localAbsPath) throws SVNException;

    /**
     * Get the path to the pristine text file identified by SHA1_CHECKSUM. Error
     * if it does not exist.
     * <p>
     * ### This is temporary - callers should not be looking at the file
     * directly.
     */
    File getPristinePath(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException;

    /**
     * Get a readable stream that will yield the pristine text identified by
     * CHECKSUM (### which should/must be its SHA-1 checksum?).
     */
    InputStream readPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException;

    /**
     * Get a directory in which the caller should create a uniquely named file
     * for later installation as a pristine text file.
     * <p>
     * The directory is guaranteed to be one that
     * {@link #installPristine(File, SVNChecksum, SVNChecksum)} can use:
     * specifically, one from which it can atomically move the file.
     */
    File getPristineTempDir(File wcRootAbsPath) throws SVNException;

    /**
     * Install the file TEMPFILE_ABSPATH (which is sitting in a directory given
     * by {@link #getPristineTempDir(File)}) into the pristine data store, to be
     * identified by the SHA-1 checksum of its contents, SHA1_CHECKSUM.
     * <p>
     * ### the md5_checksum parameter is temporary.
     */
    void installPristine(File tempfileAbspath, SVNChecksum sha1Checksum, SVNChecksum md5Checksum) throws SVNException;

    /**
     * Get the MD-5 checksum of a pristine text identified by its SHA-1 checksum
     * SHA1_CHECKSUM. Return an error if the pristine text does not exist or its
     * MD5 checksum is not found.
     */
    SVNChecksum getPristineMD5(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException;

    /**
     * Get the SHA-1 checksum of a pristine text identified by its MD-5 checksum
     * MD5_CHECKSUM. Return an error if the pristine text does not exist or its
     * SHA-1 checksum is not found.
     * <p>
     * Note: The MD-5 checksum is not strictly guaranteed to be unique in the
     * database table, although duplicates are expected to be extremely rare.
     * <p>
     * ### TODO: The behaviour is currently unspecified if the MD-5 checksum is
     * not unique. Need to see whether this function is going to stay in use,
     * and, if so, address this somehow.
     */
    SVNChecksum getPristineSHA1(File wcRootAbsPath, SVNChecksum md5Checksum) throws SVNException;

    /**
     * Remove the pristine text with SHA-1 checksum SHA1_CHECKSUM from the
     * pristine store, if it is not referenced by any of the (other) WC DB
     * tables.
     */
    void removePristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException;

    /**
     * Check for presence, according to the given mode (on how hard we should
     * examine things)
     */
    boolean checkPristine(File wcRootAbsPath, SVNChecksum sha1Checksum, WCDbCheckMode mode) throws SVNException;

    /**
     * If {@link #checkPristine(File, SVNChecksum, WCDbCheckMode)} returns
     * "corrupted pristine file", then this function can be used to repair it.
     * It will attempt to restore integrity between the SQLite database and the
     * filesystem. Failing that, then it will attempt to clean out the record
     * and/or file. Failing that, then it will throw error.
     */
    void repairPristine(File wcRootAbsPath, SVNChecksum sha1Checksum) throws SVNException;

    /**
     * Ensure an entry for the repository at REPOS_ROOT_URL with UUID exists in
     * DB for LOCAL_ABSPATH, either by finding the correct row, or inserting a
     * new row. In either case return the id.
     */
    long ensureRepository(File localAbsPath, SVNURL reposRootUrl, String reposUuid) throws SVNException;

    /** svn cp WCPATH WCPATH ... can copy mixed base/working around */
    void opCopy(File srcAbsPath, File dstAbspath, SVNSkel workItems) throws SVNException;

    /**
     * Record a copy at LOCAL_ABSPATH from a repository directory.
     * <p>
     * This copy is NOT recursive. It simply establishes this one node. CHILDREN
     * must be provided, and incomplete nodes will be constructed for them.
     */
    void opCopyDir(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, List<File> children, SVNDepth depth, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /** Record a copy at LOCAL_ABSPATH from a repository file. */
    void opCopyFile(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, SVNChecksum checksum, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    void opCopySymlink(File localAbsPath, SVNProperties props, long changedRev, Date changedDate, String changedAuthor, File originalReposRelPath, SVNURL originalRootUrl, String originalUuid,
            long originalRevision, File target, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /**
     * Add a new versioned directory. A list of children is NOT passed since
     * they are added in future, distinct calls to opAddDirectory(). Tthis is
     * freshly added, so it has no properties.
     */
    void opAddDirectory(File localAbsPath, SVNSkel workItems) throws SVNException;

    /**
     * As a new file, there are no properties. This file has no "pristine"
     * contents, so a checksum [reference] is not required.
     */
    void opAddFile(File localAbsPath, SVNSkel workItems) throws SVNException;

    /** Newly added symlinks have no properties. */
    void opAddSymlink(File localAbsPath, File target, SVNSkel workItems) throws SVNException;

    /**
     * Set the properties of the node LOCAL_ABSPATH in the ACTUAL tree to PROPS.
     * <p>
     * To specify no properties, PROPS must be an empty hash, not NULL. If the
     * node is not present, throw an error.
     * <p>
     * CONFLICT is used to register a conflict on this node at the same time the
     * properties are changed.
     * <p>
     * WORK_ITEMS are inserted into the work queue, as additional things that
     * need to be completed before the working copy is stable.
     * <p>
     * NOTE: This will overwrite ALL working properties the node currently has.
     * There is no opSetProp() function. Callers must read all the properties,
     * change one, and write all the properties.
     * <p>
     * NOTE: This will create an entry in the ACTUAL table for the node if it
     * does not yet have one.
     */
    void opSetProps(File localAbsPath, SVNProperties props, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /**
     * Set the properties of the node LOCAL_ABSPATH in the BASE tree to PROPS.
     * <p>
     * This function should not exist because properties should be stored onto
     * the BASE node at construction time, in a single atomic operation.
     * <p>
     * To specify no properties, PROPS must be an empty hash, not NULL.
     * <p>
     * If the node is not present, {@link SVNErrorCode#WC_PATH_NOT_FOUND} is
     * thrown.
     */
    void setBasePropsTemp(File localAbsPath, SVNProperties props) throws SVNException;

    /**
     * Set the properties of the node LOCAL_ABSPATH in the WORKING tree to
     * PROPS.
     * <p>
     * This function should not exist because properties should be stored onto
     * the WORKING node at construction time, in a single atomic operation.
     * <p>
     * To specify no properties, PROPS must be an empty hash, not NULL.
     * <p>
     * If the node is not present, {@link SVNErrorCode#WC_PATH_NOT_FOUND} is
     * returned.
     */
    void setWorkingPropsTemp(File localAbsPath, SVNProperties props) throws SVNException;

    void opDelete(File localAbsPath) throws SVNException;

    void opMove(File srcAbsPath, File dstAbsPath) throws SVNException;

    /** mark PATH as (possibly) modified. "svn edit" */
    void opModified(File localAbsPath) throws SVNException;

    /** use NULL to remove from a changelist. */
    void opSetChangelist(File localAbsPath, String changelist) throws SVNException;

    /** caller maintains ACTUAL. we're just recording state. */
    void opMarkConflict(File localAbsPath) throws SVNException;

    /**
     * caller maintains ACTUAL, and how the resolution occurred. we're just
     * recording state.
     * <p>
     * I'm not sure that these three values are the best way to do this, but
     * they're handy for now.
     */
    void opMarkResolved(File localAbspath, boolean resolvedText, boolean resolvedProps, boolean resolvedTree) throws SVNException;

    void opRevert(File localAbspath, SVNDepth depth) throws SVNException;

    /**
     * Return all the children of localAbsPath that are in tree conflicts.
     */
    Map<File, SVNTreeConflictDescription> opReadAllTreeConflicts(File localAbsPath) throws SVNException;

    /**
     * Get any tree conflict associated with localAbspath in DB.
     */
    SVNTreeConflictDescription opReadTreeConflict(File localAbspath) throws SVNException;

    /**
     * Set the tree conflict on LOCAL_ABSPATH in DB to TREE_CONFLICT. Use NULL
     * to remove a tree conflict.
     */
    void opSetTreeConflict(File localAbspath, SVNTreeConflictDescription treeConflict) throws SVNException;

    /**
     * Retrieve information about a node.
     * <p>
     * For the node implied by LOCAL_ABSPATH from the local filesystem, return
     * information in the provided OUT parameters. Each OUT parameter may be
     * NULL, indicating that specific item is not requested.
     * <p>
     * The information returned comes from the BASE tree, as possibly modified
     * by the WORKING and ACTUAL trees.
     * <p>
     * If there is no information about the node, then
     * {@link SVNErrorCode#WC_PATH_NOT_FOUND} will be returned.
     * <p>
     *
     * The OUT parameters, and their "not available" values are:
     *
     * <table>
     * <tr>
     * <td>STATUS</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>KIND</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>REVISION</td>
     * <td>SVN_INVALID_REVNUM</td>
     * </tr>
     * <tr>
     * <td>REPOS_RELPATH</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>REPOS_ROOT_URL</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>REPOS_UUID</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>CHANGED_REV</td>
     * <td>SVN_INVALID_REVNUM</td>
     * </tr>
     * <tr>
     * <td>CHANGED_DATE</td>
     * <td>0</td>
     * </tr>
     * <tr>
     * <td>CHANGED_AUTHOR</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>LAST_MOD_TIME</td>
     * <td>0</td>
     * </tr>
     * <tr>
     * <td>DEPTH</td>
     * <td>svn_depth_unknown</td>
     * </tr>
     * <tr>
     * <td>CHECKSUM</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>TRANSLATED_SIZE</td>
     * <td>SVN_INVALID_FILESIZE</td>
     * </tr>
     * <tr>
     * <td>TARGET</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>CHANGELIST</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>ORIGINAL_REPOS_RELPATH</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>ORIGINAL_ROOT_URL</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>ORIGINAL_UUID</td>
     * <td>NULL</td>
     * </tr>
     * <tr>
     * <td>ORIGINAL_REVISION</td>
     * <td>SVN_INVALID_REVNUM</td>
     * </tr>
     * <tr>
     * <td>TEXT_MOD</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>PROPS_MOD</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>BASE_SHADOWED</td>
     * <td>n/a (always available)</td>
     * </tr>
     * <tr>
     * <td>CONFLICTED</td>
     * <td>FALSE</td>
     * </tr>
     * <tr>
     * <td>LOCK</td>
     * <td>NULL</td>
     * </tr>
     * <table>
     *
     * <p>
     * When STATUS is requested, then it will be one of these values:
     *
     * <ul>
     * <li>
     * {@link WCDbStatus#Normal}
     * <p>
     * A plain BASE node, with no local changes.</li>
     *
     * <li>
     * {@link WCDbStatus#Added} <br>
     * {@link WCDbStatus#ObstructedAdd}
     * <p>
     * A node has been added/copied/moved to here. See BASE_SHADOWED to see if
     * this change overwrites a BASE node. Use scan_addition() to resolve
     * whether this has been added, copied, or moved, and the details of the
     * operation (this function only looks at LOCAL_ABSPATH, but resolving the
     * details requires scanning one or more ancestor nodes).
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#Deleted} <br>
     * {@link WCDbStatus#ObstructedDelete}
     * <p>
     * This node has been deleted or moved away. It may be a delete/move of a
     * BASE node, or a child node of a subtree that was copied/moved to an
     * ancestor location. Call scan_deletion() to determine the full details of
     * the operations upon this node.
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#Obstructed}
     * <p>
     * The versioned subdirectory is missing or obstructed by a file.
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#Absent}
     * <p>
     * The node is versioned/known by the server, but the server has decided not
     * to provide further information about the node. This is a BASE node (since
     * changes are not allowed to this node).
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#Excluded}
     * <p>
     * The node has been excluded from the working copy tree. This may be an
     * exclusion from the BASE tree, or an exclusion for a child node of a
     * copy/move to an ancestor (see BASE_SHADOWED to determine the situation).
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#notPresent}
     * <p>
     * This is a node from the BASE tree, has been marked as "not-present"
     * within this mixed-revision working copy. This node is at a revision that
     * is not in the tree, contrary to its inclusion in the parent node's
     * revision.
     *
     * </li>
     *
     * <li>
     * {@link WCDbStatus#Incomplete}
     * <p>
     * The BASE or WORKING node is incomplete due to an interrupted operation.</li>
     * </ul>
     * <p>
     * If REVISION is requested, it will be set to the revision of the
     * unmodified (BASE) node, or to -1 if any structural changes have been made
     * to that node (that is, if the node has a row in the WORKING table).
     * <p>
     *
     * If DEPTH is requested, and the node is NOT a directory, then the value
     * will be set to {@link SVNDepth#UNKNOWN}.
     * <p>
     *
     * If CHECKSUM is requested, and the node is NOT a file, then it will be set
     * to NULL.
     * <p>
     *
     * If TRANSLATED_SIZE is requested, and the node is NOT a file, then it will
     * be set to {@link #INVALID_FILESIZE}.
     * <p>
     *
     * If TARGET is requested, and the node is NOT a symlink, then it will be
     * set to NULL.
     */
    WCDbInfo readInfo(File localAbsPath, InfoField... fields) throws SVNException;

    class WCDbInfo {

        public enum InfoField {
            status, kind, revision, reposRelPath, reposRootUrl, reposUuid, changedRev, changedDate, changedAuthor, lastModTime, depth, checksum, translatedSize, target, changelist, originalReposRelpath, originalRootUrl, originalUuid, originalRevision, textMod, propsMod, baseShadowed, conflicted, lock;
        }

        /* ### derived */
        public WCDbStatus status;
        public WCDbKind kind;
        public long revision;
        public File reposRelPath;
        public SVNURL reposRootUrl;
        public String reposUuid;
        public long changedRev;
        public Date changedDate;
        public String changedAuthor;
        public long lastModTime;

        /* ### dirs only */
        public SVNDepth depth;
        public SVNChecksum checksum;
        public long translatedSize;
        public File target;
        public String changelist;

        /* ### the following fields if copied/moved (history) */
        public File originalReposRelpath;
        public SVNURL originalRootUrl;
        public String originalUuid;
        public long originalRevision;

        /* ### the followed are derived fields */
        /* ### possibly modified */
        public boolean textMod;
        public boolean propsMod;
        /*
         * ### WORKING shadows a ### deleted BASE?
         */
        public boolean baseShadowed;

        public boolean conflicted;
        public WCDbLock lock;

    }

    /**
     * Return value of the property named PROPNAME of the node LOCAL_ABSPATH in
     * the ACTUAL tree (looking through to the WORKING or BASE tree as
     * required).
     * <p>
     * If the node has no property named PROPNAME, return NULL.
     * <p>
     * If the node is not present, throw an error.
     */
    String readProperty(File localAbsPath, String propname) throws SVNException;

    /**
     * Return the properties of the node LOCAL_ABSPATH in the ACTUAL tree
     * (looking through to the WORKING or BASE tree as required).
     * <p>
     * If the node has no properties, return an empty hash.
     * <p>
     * If the node is not present, throw an error.
     */
    SVNProperties readProperties(File localAbsPath) throws SVNException;

    /**
     * Return the properties of the node LOCAL_ABSPATH in the WORKING tree
     * (looking through to the BASE tree as required).
     * <p>
     * If the node has no properties, return an empty hash.
     * <p>
     * If the node is not present, throw an error.
     */
    SVNProperties readPristineProperties(File localAbspath) throws SVNException;

    /**
     * Return the basenames of the immediate children of LOCAL_ABSPATH in DB.
     */
    List<String> readChildren(File localAbspath) throws SVNException;

    /**
     * Return the basenames of the immediate children of LOCAL_ABSPATH in DB
     * that are conflicted.
     * <p>
     * In case of tree conflicts a victim doesn't have to be in the working
     * copy.
     * <p>
     * This function will probably be removed.
     */
    List<String> readConflictVictims(File localAbspath) throws SVNException;

    /**
     * Return all conflicts that have LOCAL_ABSPATH as victim.
     * <p>
     * Victim must be versioned or be part of a tree conflict.
     * <p>
     * Currently there can be just one property conflict recorded per victim
     * <p>
     * This function will probably be removed.
     */
    List<SVNTreeConflictDescription> readConflicts(File localAbsPath) throws SVNException;

    /**
     * Return the kind of the node in DB at LOCAL_ABSPATH. The WORKING tree will
     * be examined first, then the BASE tree. If the node is not present in
     * either tree and ALLOW_MISSING is TRUE, then {@link WCDbKind#unknown} is
     * returned. If the node is missing and ALLOW_MISSING is FALSE, then it will
     * throw {@link SVNErrorCode#WC_PATH_NOT_FOUND}.
     */
    WCDbKind readKind(File localAbsPath, boolean allowMissing) throws SVNException;

    /**
     * Return TRUE if LOCAL_ABSPATH in DB "is not present, and I haven't
     * scheduled something over the top of it."
     */
    boolean isNodeHidden(File localAbsPath) throws SVNException;

    /**
     * Associate LOCAL_DIR_ABSPATH, and all its children with the repository at
     * at REPOS_ROOT_URL. The relative path to the repos root will not change,
     * just the repository root. The repos uuid will also remain the same. This
     * also updates any locks which may exist for the node, as well as any
     * copyfrom repository information. Finally, the DAV cache (aka "wcprops")
     * will be reset for affected entries.
     *
     * <p>
     *
     * localDirAbspath "should be" the wcroot or a switch root. all URLs under
     * this directory (depth=infinity) will be rewritten.
     *
     * <p>
     *
     * SINGLE_DB is a temp argument, and should be TRUE if using compressed
     * metadata. When all metadata gets compressed, it should disappear.
     */
    void globalRelocate(File localDirAbspath, SVNURL reposRootUrl, boolean singleDb) throws SVNException;

    /**
     * Collapse the WORKING and ACTUAL tree changes down into BASE, called for
     * each committed node.
     * <p>
     * NEW_REVISION must be the revision number of the revision created by the
     * commit. It will become the BASE node's 'revnum' and 'changed_rev' values
     * in the BASE_NODE table.
     * <p>
     * NEW_DATE is the (server-side) date of the new revision. It may be 0 if
     * the revprop is missing on the revision.
     * <p>
     *
     * NEW_AUTHOR is the (server-side) author of the new revision. It may be
     * NULL if the revprop is missing on the revision.
     * <p>
     *
     * One or both of NEW_CHECKSUM and NEW_CHILDREN should be NULL. For new:
     * <ul>
     * <li>files: NEW_CHILDREN should be NULL</li>
     * <li>dirs: NEW_CHECKSUM should be NULL</li>
     * <li>symlinks: both should be NULL</li>
     * </ul>
     * <p>
     *
     * WORK_ITEMS will be place into the work queue.
     */
    void globalCommit(File localAbspath, long newRevision, Date newDate, String newAuthor, SVNChecksum newChecksum, List<File> newChildren, SVNProperties newDavCache, boolean keepChangelist,
            SVNSkel workItems) throws SVNException;

    /**
     * Perform an "update" operation at this node. It will create/modify a BASE
     * node, and possibly update the ACTUAL tree's node (e.g put the node into a
     * conflicted state).
     * <p>
     * There may be cases where we need to tweak an existing WORKING node.
     * <p>
     * This operations on a single node, but may affect children.
     * <p>
     * The repository cannot be changed with this function, but a "switch" (aka
     * changing repos_relpath) is possible.
     * <p>
     * One of NEW_CHILDREN, NEW_CHECKSUM, or NEW_TARGET must be provided. the
     * other two values must be NULL.
     * <p>
     * This does not allow a change of depth.
     * <p>
     * We do not update a file's TRANSLATED_SIZE here. at some future point,
     * when the file is installed, then a TRANSLATED_SIZE will be set.
     */
    void globalUpdate(File localAbsPath, WCDbKind newKind, File newReposRelpath, long newRevision, SVNProperties newProps, long newChangedRev, Date newChangedDate, String newChangedAuthor,
            List<File> newChildren, SVNChecksum newChecksum, File newTarget, SVNProperties newDavCache, SVNSkel conflict, SVNSkel workItems) throws SVNException;

    /**
     * Record the TRANSLATED_SIZE and LAST_MOD_TIME for a versioned node.
     * <p>
     * This function will record the information within the WORKING node, if
     * present, or within the BASE tree. If neither node is present, then
     * {@link SVNErrorCode#WC_PATH_NOT_FOUND} will be thrown.
     * <p>
     * TRANSLATED_SIZE may be {@link #INVALID_FILESIZE}, which will be recorded
     * as such, implying "unknown size".
     * <p>
     * LAST_MOD_TIME may be 0, which will be recorded as such, implying
     * "unknown last mod time".
     */
    void globalRecordFileinfo(File local_abspath, long translated_size, Date last_mod_time) throws SVNException;

    /** Add or replace LOCK for LOCAL_ABSPATH to DB. */
    void addLock(File localAbsPath, WCDbLock lock) throws SVNException;

    /** Remove any lock for LOCAL_ABSPATH in DB. */
    void removeLock(File localAbsPath) throws SVNException;

    /**
     * Scan for a BASE node's repository information.
     * <p>
     * In the typical case, a BASE node has unspecified repository information,
     * meaning that it is implied by its parent's information. When the info is
     * needed, this function can be used to scan up the BASE tree to find the
     * data.
     * <p>
     * For the BASE node implied by LOCAL_ABSPATH, its location in the
     * repository returned in REPOS_ROOT_URL and REPOS_UUID will be returned in
     * REPOS_RELPATH. Any of the OUT parameters may be NULL, indicating no
     * interest in that piece of information.
     */
    WCDbRepositoryInfo scanBaseRepository(File localAbsPath, RepositoryInfoField... fields) throws SVNException;

    class WCDbRepositoryInfo {

        public enum RepositoryInfoField {
            relPath, rootUrl, uuid
        }

        public File relPath;
        public SVNURL rootUrl;
        public String uuid;
    }

    /**
     * Scan upwards for information about a known addition to the WORKING tree.
     * <p>
     * If a node's status as returned by
     * {@link ISVNWCDb#readInfo(File, InfoField...)} is {@link WCDbStatus#Added}
     * (NOT obstructed_add!), then this function returns a refined status in
     * STATUS, which is one of:
     * <p>
     * <ul>
     * <li>
     * {@link WCDbStatus#Added} -- this NODE is a simple add without history.
     * OP_ROOT_ABSPATH will be set to the topmost node in the added subtree
     * (implying its parent will be an unshadowed BASE node). The REPOS_* values
     * will be implied by that ancestor BASE node and this node's position in
     * the added subtree. ORIGINAL_* will be set to their NULL values (and
     * SVN_INVALID_REVNUM for ORIGINAL_REVISION).</li>
     * <li>
     * {@link WCDbStatus#Copied} -- this NODE is the root or child of a copy.
     * The root of the copy will be stored in OP_ROOT_ABSPATH. Note that the
     * parent of the operation root could be another WORKING node (from an add,
     * copy, or move). The REPOS_* values will be implied by the ancestor
     * unshadowed BASE node. ORIGINAL_* will indicate the source of the copy.</li>
     * <li>
     * {@link WCDbStatus#MovedHere} -- this NODE arrived as a result of a move.
     * The root of the moved nodes will be stored in OP_ROOT_ABSPATH. Similar to
     * the copied state, its parent may be a WORKING node or a BASE node. And
     * again, the REPOS_* values are implied by this node's position in the
     * subtree under the ancestor unshadowed BASE node. ORIGINAL_* will indicate
     * the source of the move.</li>
     * </ul>
     * <p>
     * All OUT parameters may be NULL to indicate a lack of interest in that
     * piece of information.
     * <p>
     * STATUS, OP_ROOT_ABSPATH, and REPOS_* will always be assigned a value if
     * that information is requested (and assuming a successful return).
     * <p>
     * ORIGINAL_REPOS_RELPATH will refer to the <b>root</b> of the operation. It
     * does <b>not</b> correspond to the node given by LOCAL_ABSPATH. The caller
     * can use the suffix on LOCAL_ABSPATH (relative to OP_ROOT_ABSPATH) in
     * order to compute the source node which corresponds to LOCAL_ABSPATH.
     * <p>
     * If the node given by LOCAL_ABSPATH does not have changes recorded in the
     * WORKING tree, then {@link SVNErrorCode#WC_PATH_NOT_FOUND} is thrown. If
     * it doesn't have an "added" status, then
     * {@link SVNErrorCode#WC_PATH_UNEXPECTED_STATUS} will be thrown.
     */
    WCDbAdditionInfo scanAddition(File localAbsPath, AdditionInfoField... fields) throws SVNException;

    class WCDbAdditionInfo {

        public enum AdditionInfoField {
            status, opRootAbsPath, reposRelPath, reposRootUrl, reposUuid, originalReposRelPath, originalRootUrl, originalUuid, originalRevision
        }

        public WCDbStatus status;
        public File opRootAbsPath;
        public File reposRelPath;
        public SVNURL reposRootUrl;
        public String reposUuid;
        public File originalReposRelPath;
        public SVNURL originalRootUrl;
        public String originalUuid;
        public long originalRevision;
    }

    /**
     * Scan upwards for additional information about a deleted node.
     * <p>
     * When a deleted node is discovered in the WORKING tree, the situation may
     * be quite complex. This function will provide the information to resolve
     * the circumstances of the deletion.
     * <p>
     *
     * For discussion purposes, we will start with the most complex example and
     * then demonstrate simplified examples. Consider node B/W/D/N has been
     * found as deleted. B is an unmodified directory (thus, only in BASE). W is
     * "replacement" content that exists in WORKING, shadowing a similar B/W
     * directory in BASE. D is a deleted subtree in the WORKING tree, and N is
     * the deleted node.
     * <p>
     *
     * In this example, BASE_DEL_ABSPATH will bet set to B/W. That is the root
     * of the BASE tree (implicitly) deleted by the replacement. BASE_REPLACED
     * will be set to TRUE since B/W replaces the BASE node at B/W.
     * WORK_DEL_ABSPATH will be set to the subtree deleted within the
     * replacement; in this case, B/W/D. No move-away took place, so
     * MOVED_TO_ABSPATH is set to NULL.
     * <p>
     *
     * In another scenario, B/W was moved-away before W was put into the WORKING
     * tree through an add/copy/move-here. MOVED_TO_ABSPATH will indicate where
     * B/W was moved to. Note that further operations may have been performed
     * post-move, but that is not known or reported by this function.
     * <p>
     *
     * If BASE does not have a B/W, then the WORKING B/W is not a replacement,
     * but a simple add/copy/move-here. BASE_DEL_ABSPATH will be set to NULL,
     * and BASE_REPLACED will be set to FALSE.
     * <p>
     *
     * If B/W/D does not exist in the WORKING tree (we're only talking about a
     * deletion of nodes of the BASE tree), then deleting B/W/D would have
     * marked the subtree for deletion. BASE_DEL_ABSPATH will refer to B/W/D,
     * BASE_REPLACED will be FALSE, MOVED_TO_ABSPATH will be NULL, and
     * WORK_DEL_ABSPATH will be NULL.
     * <p>
     *
     * If the BASE node B/W/D was moved instead of deleted, then
     * MOVED_TO_ABSPATH would indicate the target location (and other OUT values
     * as above).
     * <p>
     *
     * When the user deletes B/W/D from the WORKING tree, there are a few
     * additional considerations. If B/W is a simple addition (not a copy or a
     * move-here), then the deletion will simply remove the nodes from WORKING
     * and possibly leave behind "base-delete" markers in the WORKING tree. If
     * the source is a copy/moved-here, then the nodes are replaced with
     * deletion markers.
     * <p>
     *
     * If the user moves-away B/W/D from the WORKING tree, then behavior is
     * again dependent upon the origination of B/W. For a plain add, the nodes
     * simply move to the destination. For a copy, a deletion is made at B/W/D,
     * and a new copy (of a subtree of the original source) is made at the
     * destination. For a move-here, a deletion is made, and a copy is made at
     * the destination (we do not track multiple moves; the source is moved to
     * B/W, then B/W/D is deleted; then a copy is made at the destination;
     * however, note the double-move could have been performed by moving the
     * subtree first, then moving the source to B/W).
     * <p>
     *
     * There are three further considerations when resolving a deleted node:
     * <ul>
     *
     * <li>
     * If the BASE B/W/D was moved-away, then BASE_DEL_ABSPATH will specify
     * B/W/D as the root of the BASE deletion (not necessarily B/W as an
     * implicit delete caused by a replacement; only the closest ancestor is
     * reported). The other parameters will operate as normal, based on what is
     * happening in the WORKING tree. Also note that ancestors of B/W/D may
     * report additional, explicit moved-away status.
     *
     * <li>
     * If the BASE B/W/D was deleted explicitly *and* B/W is a replacement, then
     * the explicit deletion is subsumed by the implicit deletion that occurred
     * with the B/W replacement. Thus, BASE_DEL_ABSPATH will point to B/W as the
     * root of the BASE deletion. IOW, we can detect the explicit move-away, but
     * not an explicit deletion.
     *
     * <li>
     * If B/W/D/N refers to a node present in the BASE tree, and B/W was
     * replaced by a shallow subtree, then it is possible for N to be reported
     * as deleted (from BASE) yet no deletions occurred in the WORKING tree
     * above N. Thus, WORK_DEL_ABSPATH will be set to NULL.
     * </ul>
     *
     * <p>
     * Summary of OUT parameters:
     *
     * <ul>
     *
     * <li>
     * BASE_DEL_ABSPATH will specify the nearest ancestor of the explicit or
     * implicit deletion (if any) that applies to the BASE tree.
     *
     * <li>
     * BASE_REPLACED will specify whether the node at BASE_DEL_ABSPATH has been
     * replaced (shadowed) by nodes in the WORKING tree. If no BASE deletion has
     * occurred (BASE_DEL_ABSPATH is NULL, meaning the deletion is confined to
     * the WORKING TREE), then BASE_REPLACED will be FALSE.
     *
     * <li>
     * MOVED_TO_ABSPATH will specify the nearest ancestor that has moved-away,
     * if any. If no ancestors have been moved-away, then this is set to NULL.
     *
     * <li>
     * WORK_DEL_ABSPATH will specify the root of a deleted subtree within the
     * WORKING tree (note there is no concept of layered delete operations in
     * WORKING, so there is only one deletion root in the ancestry).
     * </ul>
     *
     * <p>
     *
     * All OUT parameters may be set to NULL to indicate a lack of interest in
     * that piece of information.
     *
     * <p>
     * If the node given by LOCAL_ABSPATH does not exist, then
     * {@link SVNErrorCode#WC_PATH_NOT_FOUND} is returned. If it doesn't have a
     * "deleted" status, then {@link SVNErrorCode#WC_PATH_UNEXPECTED_STATUS}
     * will be returned.
     */
    WCDbDeletionInfo scanDeletion(File localAbsPath, DeletionInfoField... fields) throws SVNException;

    class WCDbDeletionInfo {

        public enum DeletionInfoField {
            baseDelAbsPath, baseReplaced, movedToAbsPath, workDelAbsPath
        }

        public File baseDelAbsPath;
        public boolean baseReplaced;
        public File movedToAbsPath;
        public File workDelAbsPath;
    }

    /**
     * In the WCROOT associated with DB and WRI_ABSPATH, add WORK_ITEM to the
     * wcroot's work queue.
     */
    void addWorkQueue(File wcRootAbsPath, SVNSkel workItem) throws SVNException;

    /**
     * In the WCROOT associated with DB and WRI_ABSPATH, fetch a work item that
     * needs to be completed. Its identifier is returned in ID, and the data in
     * WORK_ITEM.
     * <p>
     * Items are returned in the same order they were queued. This allows for
     * (say) queueing work on a parent node to be handled before that of its
     * children.
     * <p>
     * If there are no work items to be completed, then ID will be set to zero,
     * and WORK_ITEM to NULL.
     */
    WCDbWorkQueueInfo fetchWorkQueue(File wcRootAbsPath) throws SVNException;

    class WCDbWorkQueueInfo {

        public long id;
        public SVNSkel work_item;
    }

    /**
     * In the WCROOT associated with DB and WRI_ABSPATH, mark work item ID as
     * completed. If an error occurs, then it is unknown whether the work item
     * has been marked as completed.
     */
    void completedWorkQueue(File wcRootAbsPath, long id) throws SVNException;

    /**
     * Note: LEVELS_TO_LOCK is here strictly for backward compat. The access
     * batons still have the notion of 'levels to lock' and we need to ensure
     * that they still function correctly, even in the new world. 'levels to
     * lock' should not be exposed through the wc-ng APIs at all: users either
     * get to lock the entire tree (rooted at some subdir, of course), or none.
     */
    void setWCLock(File localAbspath, int levelsToLock) throws SVNException;

    boolean isWCLocked(File localAbspath) throws SVNException;

    void removeWCLock(File localAbspath) throws SVNException;

    /** temp function. return the FORMAT for the directory LOCAL_ABSPATH. */
    int getFormatTemp(File localDirAbsPath) throws SVNException;

    /** Return the serialized file external info (from BASE) for LOCAL_ABSPATH.
    Stores NULL into SERIALIZED_FILE_EXTERNAL if this node is NOT a file
    external. If a BASE node does not exist: SVN_ERR_WC_PATH_NOT_FOUND.  */

    String getFileExternalTemp(File path) throws SVNException;

    boolean determineKeepLocalTemp(File localAbsPath) throws SVNException;

    WCDbDirDeletedInfo isDirDeletedTem(File entryAbspath) throws SVNException;

    public static class WCDbDirDeletedInfo {
        public boolean notPresent;
        public long baseRevision;
    }

}
