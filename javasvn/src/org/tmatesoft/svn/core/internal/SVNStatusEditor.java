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

package org.tmatesoft.svn.core.internal;

import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNStatusEditor implements ISVNEditor {

    
    private long myTargetRevision;
    private String myCurrentPath;
    private String myRootPath;
    private Map myRemoteStatuses;

    private String myCurrentFilePath;
    private long myCurrentFileRevision;
    private long myCurrentRevision;
    
    public SVNStatusEditor() {
        this("");
    }
    
    public SVNStatusEditor(String rootPath) {
        myRemoteStatuses = new HashMap();
        myRootPath = rootPath;
    }    

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }
    
    public void openRoot(long revision) throws SVNException {
        myCurrentPath = "";
        myCurrentRevision = revision;
    }
    public void deleteEntry(String path, long revision) throws SVNException {
        myRemoteStatuses.put(path, new RemoteSVNStatus(revision, SVNStatus.DELETED, SVNStatus.NOT_MODIFIED));
    }
    
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }
    
    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        RemoteSVNStatus status = (RemoteSVNStatus) myRemoteStatuses.get(path);
        if (status == null) {
            status = new RemoteSVNStatus(myTargetRevision, SVNStatus.ADDED, SVNStatus.NOT_MODIFIED);
        } else if (status.myContentsStatus == SVNStatus.DELETED) {
            status.myContentsStatus = SVNStatus.REPLACED;
        }
        if (status != null) {
            status.isAddedWithHistory = copyPath != null;
            status.isDirectory = true;
            myRemoteStatuses.put(path, status);
        }
    }
    public void openDir(String path, long revision) throws SVNException {
        myCurrentPath = path;
    }
    
    public void changeDirProperty(String name, String value) throws SVNException {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            RemoteSVNStatus status = (RemoteSVNStatus) myRemoteStatuses.get(myCurrentPath);
            if (status == null) {
                status = new RemoteSVNStatus(myCurrentRevision, SVNStatus.NOT_MODIFIED, SVNStatus.MODIFIED);
                myRemoteStatuses.put(myCurrentFilePath, status);
            } else if (status.myContentsStatus != SVNStatus.ADDED && status.myContentsStatus != SVNStatus.REPLACED) {
                status.myPropertiesStatus = SVNStatus.MODIFIED;
            }
            status.isDirectory = true;
        }
    }
    public void closeDir() throws SVNException {
    }
    
    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        myRemoteStatuses.put(path, new RemoteSVNStatus(myTargetRevision, SVNStatus.ADDED, SVNStatus.NOT_MODIFIED));
        RemoteSVNStatus status = (RemoteSVNStatus) myRemoteStatuses.get(path);
        if (status == null) {
            status = new RemoteSVNStatus(myTargetRevision, SVNStatus.ADDED, SVNStatus.NOT_MODIFIED);
        } else if (status.myContentsStatus == SVNStatus.DELETED) {
            status.myContentsStatus = SVNStatus.REPLACED;
        }
        if (status != null) {
            status.isAddedWithHistory = copyPath != null;
            status.isDirectory = false;
            myRemoteStatuses.put(path, status);
        }
        myCurrentFilePath = path;
    }
    public void openFile(String path, long revision) throws SVNException {
        myCurrentFilePath = path;
        myCurrentFileRevision = revision;
    }
    public void applyTextDelta(String baseChecksum)  throws SVNException {
        RemoteSVNStatus status = (RemoteSVNStatus) myRemoteStatuses.get(myCurrentFilePath);
        if (status == null) {
            status = new RemoteSVNStatus(myCurrentFileRevision, SVNStatus.MODIFIED, SVNStatus.NOT_MODIFIED);
            myRemoteStatuses.put(myCurrentFilePath, status);
        } else if (status.myContentsStatus != SVNStatus.ADDED) {
            status.myContentsStatus = SVNStatus.MODIFIED;            
        }
        status.isDirectory = false;
    }
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }    
    public void textDeltaEnd() throws SVNException {
    }
    public void closeFile(String textChecksum) throws SVNException {
        myCurrentFilePath = null;
        myCurrentFileRevision = -1;
    }
    
    
    public void changeFileProperty(String name, String value) throws SVNException {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            RemoteSVNStatus status = (RemoteSVNStatus) myRemoteStatuses.get(myCurrentFilePath);
            if (status == null) {
                myRemoteStatuses.put(myCurrentFilePath, new RemoteSVNStatus(myCurrentFileRevision, SVNStatus.NOT_MODIFIED, SVNStatus.MODIFIED));
            } else if (status.myContentsStatus != SVNStatus.ADDED && status.myContentsStatus != SVNStatus.REPLACED) {
                status.myPropertiesStatus = SVNStatus.MODIFIED;
                status.isDirectory = false;
            }
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return new SVNCommitInfo(myTargetRevision, null, null);
    }
    
    public void abortEdit() throws SVNException {
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public Map completeStatus(Map statuses, String path, boolean descend) {
        // iterate all paths,
        // move matched to a special array
        String relativePath = path.substring(myRootPath.length());
        relativePath = PathUtil.removeLeadingSlash(relativePath);
        
        Collection remoteMatches = new LinkedList();
        for(Iterator remotePaths = myRemoteStatuses.keySet().iterator(); remotePaths.hasNext();) {
            String remotePath = (String) remotePaths.next();
            if (remotePath.startsWith(relativePath)) {
                String name = remotePath.substring(relativePath.length());
                name = PathUtil.removeLeadingSlash(name);
                if (name.indexOf('/') < 0) {
                    // it is a direct child of path, add only if there is a status or nothing.
                    if ((statuses.containsKey(name) && statuses.get(name) != null) || !statuses.containsKey(name)) {
                        remoteMatches.add(remotePath);
                    } 
                } else if (descend) {
                    // check that it is not child of existing direct child.
                    String head = PathUtil.head(name);
                    if (!statuses.containsKey(head)) {
                        remoteMatches.add(remotePath);
                    }
                }
            }
        }
        DebugLog.log("COMPLETING STATUS FOR  " + path + " : " + statuses.keySet());
        DebugLog.log("REMOTE MATCHED ENTRIES " + remoteMatches);
        for(Iterator remotePaths = remoteMatches.iterator(); remotePaths.hasNext();) {
            String remotePath = (String) remotePaths.next();
            String name = remotePath.substring(relativePath.length());
            name = PathUtil.removeLeadingSlash(name);
            
            RemoteSVNStatus remoteStatus = (RemoteSVNStatus) myRemoteStatuses.remove(remotePath);
            if (remoteStatus == null) {
                continue;
            }
            SVNStatus svnStatus = (SVNStatus) statuses.get(name);
            if (svnStatus != null) {
                svnStatus.setRemoteStatus(remoteStatus.myRevision, remoteStatus.myPropertiesStatus, remoteStatus.myContentsStatus);
            } else {
                svnStatus = new SVNStatus(PathUtil.removeLeadingSlash(PathUtil.append(path, name)), 0, 0, -1, -1, 
                        remoteStatus.myRevision, remoteStatus.myContentsStatus, remoteStatus.myPropertiesStatus, 
                        remoteStatus.isAddedWithHistory, false, remoteStatus.isDirectory, null);
                statuses.put(name, svnStatus);
            }
        }
        return statuses;
    }
    
    private static class RemoteSVNStatus {
        
        public RemoteSVNStatus(long revision, int contentsStatus, int propertiesStatus) {
            myRevision = revision;
            myContentsStatus = contentsStatus;
            myPropertiesStatus = propertiesStatus;
        }
        
        public long myRevision;
        public int myContentsStatus;
        public int myPropertiesStatus;
        public boolean isAddedWithHistory;
        public boolean isDirectory;
    }
    
}
