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
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
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

    public SVNRemoteStatusEditor17(File anchorAbsPath, String targetBaseName, SVNWCContext wcContext, ISVNOptions options, boolean includeIgnored, boolean reportAll, SVNDepth depth,
            SVNExternalsStore externalsStore, ISVNStatusHandler realHandler) throws SVNException {
        super(SVNFileUtil.createFilePath(anchorAbsPath, targetBaseName), wcContext, options, includeIgnored, reportAll, depth, externalsStore, realHandler);
        myAnchorStatus = internalStatus(SVNFileUtil.createFilePath(anchorAbsPath, targetBaseName));
        this.myAnchorAbsPath = anchorAbsPath;
        this.myTargetBaseName = targetBaseName;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myDirectoryInfo = new DirectoryInfo(null, null);
    }

    public void abortEdit() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void absentDir(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void absentFile(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void closeDir() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void openDir(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void openFile(String path, long revision) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void textDeltaEnd(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void handleStatus(SVNStatus status) throws SVNException {
        throw new UnsupportedOperationException();
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

    }

}
