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
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNRemoteStatusEditor17 extends SVNStatusEditor17 implements ISVNEditor, ISVNStatusHandler {

    private boolean myIsRootOpen;
    private SVNStatus17 myAnchorStatus;

    private DirectoryInfo myDirectoryInfo;
    private FileInfo myFileInfo;

    private File myAnchorAbsPath;
    private String myTargetBaseName;
    private File myTargetAbsPath;

    public SVNRemoteStatusEditor17(File anchorAbsPath, String targetBaseName, SVNWCContext wcContext, ISVNOptions options, boolean includeIgnored, boolean reportAll, SVNDepth depth,
            SVNExternalsStore externalsStore, ISVNStatusHandler realHandler) throws SVNException {
        super(SVNFileUtil.createFilePath(anchorAbsPath, targetBaseName), wcContext, options, includeIgnored, reportAll, depth, externalsStore, realHandler);
        myAnchorStatus = internalStatus(SVNFileUtil.createFilePath(anchorAbsPath, targetBaseName));
        myAnchorAbsPath = anchorAbsPath;
        myTargetBaseName = targetBaseName;
        myTargetAbsPath = SVNFileUtil.createFilePath(anchorAbsPath, targetBaseName);
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myDirectoryInfo = new DirectoryInfo(null, null);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        final File local_abspath = SVNFileUtil.createFilePath(myAnchorAbsPath, path);
        final SVNWCDbKind kind = myWCContext.getDb().readKind(local_abspath, false);
        tweakStatusHash(myDirectoryInfo, myDirectoryInfo, local_abspath, kind == SVNWCDbKind.Dir, SVNStatusType.STATUS_DELETED, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                SVNRevision.create(revision), null);
        if (myDirectoryInfo.parent != null && myTargetBaseName == null)
            tweakStatusHash(myDirectoryInfo.parent, myDirectoryInfo, myDirectoryInfo.localAbsPath, kind == SVNWCDbKind.Dir, SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_MODIFIED,
                    SVNStatusType.STATUS_NONE, null, null);
    }

    private void tweakStatusHash(DirectoryInfo dirInfo, DirectoryInfo childDir, File localAbsPath, boolean isDir, SVNStatusType reposNodeStatus, SVNStatusType reposTextStatus,
            SVNStatusType reposPropStatus, SVNRevision deletedRev, SVNLock reposLock) throws SVNException {

        Map<File, SVNStatus17> statushash = dirInfo.statii;

        /* Is PATH already a hash-key? */
        SVNStatus17 statstruct = statushash.get(localAbsPath);

        /* If not, make it so. */
        if (statstruct == null) {
            /*
             * If this item isn't being added, then we're most likely dealing
             * with a non-recursive (or at least partially non-recursive)
             * working copy. Due to bugs in how the client reports the state of
             * non-recursive working copies, the repository can send back
             * responses about paths that don't even exist locally. Our best
             * course here is just to ignore those responses. After all, if the
             * client had reported correctly in the first, that path would
             * either be mentioned as an 'add' or not mentioned at all,
             * depending on how we eventually fix the bugs in non-recursivity.
             * See issue #2122 for details.
             */
            if (reposNodeStatus != SVNStatusType.STATUS_ADDED)
                return;

            /* Use the public API to get a statstruct, and put it into the hash. */
            statstruct = internalStatus(localAbsPath);
            statstruct.setReposLock(reposLock);
            statushash.put(localAbsPath, statstruct);
        }

        /* Merge a repos "delete" + "add" into a single "replace". */
        if ((reposNodeStatus == SVNStatusType.STATUS_ADDED) && (statstruct.getReposNodeStatus() == SVNStatusType.STATUS_DELETED))
            reposNodeStatus = SVNStatusType.STATUS_REPLACED;

        /* Tweak the structure's repos fields. */
        if (reposNodeStatus != null)
            statstruct.setReposNodeStatus(reposNodeStatus);
        if (reposTextStatus != null)
            statstruct.setReposTextStatus(reposTextStatus);
        if (reposPropStatus != null)
            statstruct.setReposPropStatus(reposPropStatus);

        /* Copy out-of-date info. */

        /*
         * The last committed date, and author for deleted items isn't
         * available.
         */
        if (statstruct.getReposNodeStatus() == SVNStatusType.STATUS_DELETED) {
            statstruct.setOodKind(isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE);

            /*
             * Pre 1.5 servers don't provide the revision a path was deleted. So
             * we punt and use the last committed revision of the path's parent,
             * which has some chance of being correct. At worse it is a higher
             * revision than the path was deleted, but this is better than
             * nothing...
             */
            if (deletedRev == null)
                statstruct.setOodChangedRev(dirInfo.ood_changed_rev);
            else
                statstruct.setOodChangedRev(deletedRev.getNumber());
        } else {
            statstruct.setOodKind(childDir.ood_kind);
            statstruct.setOodChangedRev(childDir.ood_changed_rev);
            statstruct.setOodChangedDate(childDir.ood_changed_date);
            if (childDir.ood_changed_author != null)
                statstruct.setOodChangedAuthor(childDir.ood_changed_author);
        }

        return;
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myDirectoryInfo = new DirectoryInfo(path, myDirectoryInfo);
        myDirectoryInfo.added = true;
        myDirectoryInfo.parent.text_changed = true;
    }

    public void openDir(String path, long revision) throws SVNException {
        myDirectoryInfo = new DirectoryInfo(path, myDirectoryInfo);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX) && !name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myDirectoryInfo.prop_changed = true;
        }

        /* Note any changes to the repository. */
        if (value != null) {
            if (SVNProperty.COMMITTED_REVISION.equals(name)) {
                try {
                    myDirectoryInfo.ood_changed_rev = Long.parseLong(value.getString());
                } catch (NumberFormatException nfe) {
                    myDirectoryInfo.ood_changed_rev = SVNWCDb.INVALID_REVNUM;
                }
            } else if (SVNProperty.LAST_AUTHOR.equals(name)) {
                myDirectoryInfo.ood_changed_author = value.getString();
            } else if (SVNProperty.COMMITTED_DATE.equals(name)) {
                myDirectoryInfo.ood_changed_date = SVNDate.parseDate(value.getString());
            }
        }
    }

    public void closeDir() throws SVNException {
        DirectoryInfo db = myDirectoryInfo;
        DirectoryInfo pb = db.parent;

        /*
         * If nothing has changed and directory has no out of date descendants,
         * return.
         */
        if (db.added || db.prop_changed || db.text_changed || db.ood_changed_rev != SVNWCDb.INVALID_REVNUM) {
            SVNStatusType repos_node_status;
            SVNStatusType repos_text_status;
            SVNStatusType repos_prop_status;

            /* If this is a new directory, add it to the statushash. */
            if (db.added) {
                repos_node_status = SVNStatusType.STATUS_ADDED;
                repos_text_status = SVNStatusType.STATUS_ADDED;
                repos_prop_status = db.prop_changed ? SVNStatusType.STATUS_ADDED : SVNStatusType.STATUS_NONE;
            } else {
                repos_node_status = (db.text_changed || db.prop_changed) ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
                repos_text_status = db.text_changed ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
                repos_prop_status = db.prop_changed ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            }

            /*
             * Maybe add this directory to its parent's status hash. Note that
             * tweak_statushash won't do anything if repos_text_status is not
             * svn_wc_status_added.
             */
            if (pb != null) {
                /*
                 * ### When we add directory locking, we need to find a ###
                 * directory lock here.
                 */
                tweakStatusHash(pb, db, db.localAbsPath, true, repos_node_status, repos_text_status, repos_prop_status, null, null);
            } else {
                /*
                 * We're editing the root dir of the WC. As its repos status
                 * info isn't otherwise set, set it directly to trigger
                 * invocation of the status callback below.
                 */
                myAnchorStatus.setReposNodeStatus(repos_node_status);
                myAnchorStatus.setReposPropStatus(repos_prop_status);
                myAnchorStatus.setReposTextStatus(repos_text_status);

                /* If the root dir is out of date set the ood info directly too. */
                if (db.ood_changed_rev != myAnchorStatus.getRevision()) {
                    myAnchorStatus.setOodChangedRev(db.ood_changed_rev);
                    myAnchorStatus.setOodChangedDate(db.ood_changed_date);
                    myAnchorStatus.setOodKind(db.ood_kind);
                    myAnchorStatus.setOodChangedAuthor(db.ood_changed_author);
                }
            }
        }

        /*
         * Handle this directory's statuses, and then note in the parent that
         * this has been done.
         */
        if (pb != null && !db.excluded) {
            boolean was_deleted = false;
            SVNStatus17 dir_status = dir_status = pb.statii.get(db.localAbsPath);

            /* See if the directory was deleted or replaced. */
            if (dir_status != null && ((dir_status.getReposNodeStatus() == SVNStatusType.STATUS_DELETED) || (dir_status.getReposNodeStatus() == SVNStatusType.STATUS_REPLACED)))
                was_deleted = true;

            /* Now do the status reporting. */
            handleStatii(dir_status != null ? dir_status.getReposRootUrl() : null, dir_status != null ? dir_status.getReposRelpath() : null, db.statii, was_deleted, db.depth);
            if (dir_status != null && isSendableStatus(dir_status))
                getDefaultHandler().handleStatus(dir_status.getStatus16());
            pb.statii.remove(db.localAbsPath);
        } else if (pb == null) {
            /*
             * If this is the top-most directory, and the operation had a
             * target, we should only report the target.
             */
            if (myTargetBaseName != null) {
                SVNStatus17 tgt_status = db.statii.get(myTargetAbsPath);
                if (tgt_status != null) {
                    if (tgt_status.isVersioned() && tgt_status.getKind() == SVNNodeKind.DIR) {
                        getDirStatus(myTargetAbsPath, null, null, null, null, getDepth(), isReportAll(), isNoIgnore(), true, getDefaultHandler());
                    }
                    if (isSendableStatus(tgt_status)) {
                        getDefaultHandler().handleStatus(tgt_status.getStatus16());
                    }
                }
            } else {
                /*
                 * Otherwise, we report on all our children and ourself. Note
                 * that our directory couldn't have been deleted, because it is
                 * the root of the edit drive.
                 */
                handleStatii(myAnchorStatus.getReposRootUrl(), myAnchorStatus.getReposRelpath(), db.statii, false, getDepth());
                if (isSendableStatus(myAnchorStatus))
                    getDefaultHandler().handleStatus(myAnchorStatus.getStatus16());
                myAnchorStatus = null;
            }
        }
    }

    private boolean isSendableStatus(SVNStatus17 status) {

        /* If the repository status was touched at all, it's interesting. */
        if (status.getReposNodeStatus() != SVNStatusType.STATUS_NONE)
            return true;

        /* If there is a lock in the repository, send it. */
        if (status.getReposLock() != null)
            return true;

        /* If the item is ignored, and we don't want ignores, skip it. */
        if ((status.getNodeStatus() == SVNStatusType.STATUS_IGNORED) && (!isNoIgnore()))
            return false;

        /*
         * If we want everything, we obviously want this single-item subset of
         * everything.
         */
        if (isReportAll())
            return true;

        /* If the item is unversioned, display it. */
        if (status.getNodeStatus() == SVNStatusType.STATUS_UNVERSIONED)
            return true;

        /* If the text, property or tree state is interesting, send it. */
        if ((status.getNodeStatus() != SVNStatusType.STATUS_NONE && (status.getNodeStatus() != SVNStatusType.STATUS_NORMAL)))
            return true;
        if (status.isConflicted())
            return true;

        /* If it's switched, send it. */
        if (status.isSwitched())
            return true;

        /* If there is a lock token, send it. */
        if (status.isVersioned() && status.getLock() != null)
            return true;

        /* If the entry is associated with a changelist, send it. */
        if (status.getChangelist() != null)
            return true;

        /* Otherwise, don't send it. */
        return false;
    }

    private void handleStatii(SVNURL dirReposRootUrl, File dirReposRelpath, Map<File, SVNStatus17> statii, boolean dirWasDeleted, SVNDepth depth) throws SVNException {
        ISVNStatusHandler handler = dirWasDeleted ? this : getDefaultHandler();
        for (Iterator paths = statii.keySet().iterator(); paths.hasNext();) {
            File localAbsPath = (File) paths.next();
            SVNStatus17 status = statii.get(localAbsPath);
            if (status.getNodeStatus() != SVNStatusType.STATUS_OBSTRUCTED && status.getNodeStatus() != SVNStatusType.STATUS_MISSING && status.isVersioned() && status.getKind() == SVNNodeKind.DIR
                    && (depth == SVNDepth.UNKNOWN || depth == SVNDepth.INFINITY)) {
                getDirStatus(localAbsPath, dirReposRootUrl, dirReposRelpath, null, myGlobalIgnores, depth, isReportAll(), isNoIgnore(), true, handler);
            }
            if (dirWasDeleted)
                status.setReposNodeStatus(SVNStatusType.STATUS_DELETED);
            if (isSendableStatus(status))
                getDefaultHandler().handleStatus(status.getStatus16());
        }
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myFileInfo = new FileInfo(myDirectoryInfo, path, true);
        myDirectoryInfo.text_changed = true;
    }

    public void openFile(String path, long revision) throws SVNException {
        myFileInfo = new FileInfo(myDirectoryInfo, path, false);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myFileInfo.text_changed = true;
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (!propertyName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) && !propertyName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myFileInfo.prop_changed = true;
        }
        if (propertyValue != null) {
            if (SVNProperty.COMMITTED_REVISION.equals(propertyName)) {
                try {
                    myFileInfo.ood_changed_rev = Long.parseLong(propertyValue.getString());
                } catch (NumberFormatException nfe) {
                    myFileInfo.ood_changed_rev = SVNWCDb.INVALID_REVNUM;
                }
            } else if (SVNProperty.COMMITTED_DATE.equals(propertyName)) {
                myFileInfo.ood_changed_date = SVNDate.parseDate(propertyValue.getString());
            } else if (SVNProperty.LAST_AUTHOR.equals(propertyName)) {
                myFileInfo.ood_changed_author = propertyValue.getString();
            }
        }
    }

    public void closeFile(String path, String textChecksum) throws SVNException {

        SVNStatusType repos_node_status;
        SVNStatusType repos_text_status;
        SVNStatusType repos_prop_status;
        SVNLock repos_lock = null;

        /* If nothing has changed, return. */
        if (!(myFileInfo.added || myFileInfo.prop_changed || myFileInfo.text_changed))
            return;

        /* If this is a new file, add it to the statushash. */
        if (myFileInfo.added) {
            repos_node_status = SVNStatusType.STATUS_ADDED;
            repos_text_status = SVNStatusType.STATUS_ADDED;
            repos_prop_status = myFileInfo.prop_changed ? SVNStatusType.STATUS_ADDED : SVNStatusType.STATUS_NONE;
            if (myRepositoryLocks != null) {
                File dir_repos_relpath = findDirReposRelpath(myFileInfo.parent);
                if (dir_repos_relpath != null) {
                    /*
                     * repos_lock still uses the deprecated filesystem absolute
                     * path format
                     */
                    File repos_relpath = SVNFileUtil.createFilePath(dir_repos_relpath, myFileInfo.name);
                    repos_lock = (SVNLock) myRepositoryLocks.get(SVNURL.parseURIEncoded("/" + repos_relpath.toString()).getPath());
                }
            }
        } else {
            repos_node_status = (myFileInfo.text_changed || myFileInfo.prop_changed) ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            repos_text_status = myFileInfo.text_changed ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            repos_prop_status = myFileInfo.prop_changed ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
        }

        tweakStatusHash(myFileInfo, myFileInfo.localAbsPath, repos_node_status, repos_text_status, repos_prop_status, SVNWCDb.INVALID_REVNUM, repos_lock);
    }

    private void tweakStatusHash(FileInfo fileInfo, File localAbsPath, SVNStatusType reposNodeStatus, SVNStatusType reposTextStatus, SVNStatusType reposPropStatus, long revnum, SVNLock reposLock)
            throws SVNException {

        Map<File, SVNStatus17> statushash = fileInfo.parent.statii;
        SVNStatus17 statstruct = statushash.get(localAbsPath);
        if (statstruct == null) {
            if (reposNodeStatus != SVNStatusType.STATUS_ADDED)
                return;
            statstruct = internalStatus(localAbsPath);
            statstruct.setReposLock(reposLock);
            statushash.put(localAbsPath, statstruct);
        }

        if ((reposNodeStatus == SVNStatusType.STATUS_ADDED) && (statstruct.getReposNodeStatus() == SVNStatusType.STATUS_DELETED))
            reposNodeStatus = SVNStatusType.STATUS_REPLACED;

        /* Tweak the structure's repos fields. */
        if (reposNodeStatus != null)
            statstruct.setReposNodeStatus(reposNodeStatus);
        if (reposTextStatus != null)
            statstruct.setReposTextStatus(reposTextStatus);
        if (reposPropStatus != null)
            statstruct.setReposPropStatus(reposPropStatus);

        statstruct.setOodChangedRev(fileInfo.ood_changed_rev);
        statstruct.setOodChangedDate(fileInfo.ood_changed_date);
        statstruct.setOodKind(fileInfo.ood_kind);
        if (fileInfo.ood_changed_author != null)
            statstruct.setOodChangedAuthor(fileInfo.ood_changed_author);

    }

    private File findDirReposRelpath(DirectoryInfo dirinfo) {
        /* If we have no name, we're the root, return the anchor URL. */
        if (dirinfo.name == null)
            return myAnchorStatus.getReposRelpath();
        File repos_relpath;
        DirectoryInfo parent = dirinfo.parent;
        SVNStatus17 status = parent.statii.get(dirinfo.localAbsPath);
        if (status != null)
            return status.getReposRelpath();
        repos_relpath = findDirReposRelpath(parent);
        if (repos_relpath != null)
            return SVNFileUtil.createFilePath(repos_relpath, dirinfo.name);
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void handleStatus(SVNStatus status) throws SVNException {
        status.setContentsStatus(SVNStatusType.STATUS_DELETED);
        getDefaultHandler().handleStatus(status);
    }

    private class DirectoryInfo implements ISVNStatusHandler {

        private File localAbsPath;
        private String name;
        private DirectoryInfo parent;
        private TreeMap<File, SVNStatus17> statii;
        private long ood_changed_rev;
        private Date ood_changed_date;
        private SVNNodeKind ood_kind;
        private String ood_changed_author;
        private boolean excluded;
        private SVNDepth depth;
        private boolean added;
        private boolean prop_changed;
        private boolean text_changed;

        public DirectoryInfo(String path, DirectoryInfo parent) throws SVNException {
            File local_abspath;
            SVNStatus17 status_in_parent;

            assert (path != null || parent == null);

            /* Construct the absolute path of this directory. */
            if (parent != null)
                local_abspath = SVNFileUtil.createFilePath(myAnchorAbsPath, path);
            else
                local_abspath = myAnchorAbsPath;

            /* Finish populating the baton members. */
            this.localAbsPath = local_abspath;
            this.name = path != null ? SVNPathUtil.tail(path) : null;
            this.parent = parent;
            this.statii = new TreeMap<File, SVNStatus17>();
            this.ood_changed_rev = SVNWCContext.INVALID_REVNUM;
            this.ood_changed_date = null;
            this.ood_kind = SVNNodeKind.DIR;
            this.ood_changed_author = null;

            if (parent != null) {
                if (parent.excluded)
                    this.excluded = true;
                else if (parent.depth == SVNDepth.IMMEDIATES)
                    this.depth = SVNDepth.EMPTY;
                else if (parent.depth == SVNDepth.FILES || parent.depth == SVNDepth.EMPTY)
                    this.excluded = true;
                else if (parent.depth == SVNDepth.UNKNOWN)
                    /*
                     * This is only tentative, it can be overridden from d's
                     * entry later.
                     */
                    this.depth = SVNDepth.UNKNOWN;
                else
                    this.depth = SVNDepth.INFINITY;
            } else {
                this.depth = getDepth();
            }

            /*
             * Get the status for this path's children. Of course, we only want
             * to do this if the path is versioned as a directory.
             */
            if (parent != null)
                status_in_parent = parent.statii.get(this.localAbsPath);
            else
                status_in_parent = myAnchorStatus;

            /*
             * Order is important here. We can't depend on
             * status_in_parent->entry being non-NULL until after we've checked
             * all the conditions that might indicate that the parent is
             * unversioned ("unversioned" for our purposes includes being an
             * external or ignored item).
             */
            if (status_in_parent != null && (status_in_parent.getNodeStatus() != SVNStatusType.STATUS_UNVERSIONED) && (status_in_parent.getNodeStatus() != SVNStatusType.STATUS_MISSING)
                    && (status_in_parent.getNodeStatus() != SVNStatusType.STATUS_OBSTRUCTED) && (status_in_parent.getNodeStatus() != SVNStatusType.STATUS_EXTERNAL)
                    && (status_in_parent.getNodeStatus() != SVNStatusType.STATUS_IGNORED) && (status_in_parent.getKind() == SVNNodeKind.DIR) && (!this.excluded)
                    && (this.depth == SVNDepth.UNKNOWN || this.depth == SVNDepth.INFINITY || this.depth == SVNDepth.FILES || this.depth == SVNDepth.IMMEDIATES)) {
                SVNStatus17 this_dir_status;
                Collection ignores = myGlobalIgnores;

                getDirStatus(local_abspath, status_in_parent.getReposRootUrl(), status_in_parent.getReposRelpath(), null, ignores, this.depth == SVNDepth.FILES ? SVNDepth.FILES : SVNDepth.IMMEDIATES,
                        true, true, true, this);

                /* If we found a depth here, it should govern. */
                this_dir_status = this.statii.get(this.localAbsPath);
                if (this_dir_status != null && this_dir_status.isVersioned() && (this.depth == SVNDepth.UNKNOWN || this.depth.compareTo(status_in_parent.getDepth()) > 0)) {
                    this.depth = this_dir_status.getDepth();
                }
            }

        }

        public void handleStatus(SVNStatus status) throws SVNException {
            if (status.getStatus17() != null)
                statii.put(status.getFile(), status.getStatus17());
        }
    }

    public class FileInfo {

        private File localAbsPath;
        private String name;
        private DirectoryInfo parent;
        private long ood_changed_rev;
        private Date ood_changed_date;
        private SVNNodeKind ood_kind;
        private String ood_changed_author;
        private boolean added;
        private boolean text_changed;
        private boolean prop_changed;

        public FileInfo(DirectoryInfo parent, String path, boolean added) {
            this.localAbsPath = SVNFileUtil.createFilePath(myAnchorAbsPath, path);
            this.name = SVNFileUtil.getFileName(this.localAbsPath);
            this.parent = parent;
            this.ood_changed_rev = SVNWCDb.INVALID_REVNUM;
            this.ood_changed_date = null;
            this.ood_kind = SVNNodeKind.FILE;
            this.ood_changed_author = null;
        }

    }

}
