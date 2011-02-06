/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNCommitter17 implements ISVNCommitPathHandler {

    private SVNWCContext myContext;
    private Map<String, SVNCommitItem> myCommittables;
    private String myRepositoryRoot;
    private Collection myTmpFiles;
    private Map<File, SVNChecksum> myMd5Checksums;
    private Map<File, SVNChecksum> mySha1Checksums;
    private Map<String, SVNCommitItem> myModifiedFiles;

    public SVNCommitter17(SVNWCContext context, Map<String, SVNCommitItem> committables, String repositoryRoot, Collection tmpFiles, Map<File, SVNChecksum> md5Checksums,
            Map<File, SVNChecksum> sha1Checksums) {
        myContext = context;
        myCommittables = committables;
        myRepositoryRoot = repositoryRoot;
        myTmpFiles = tmpFiles;
        myMd5Checksums = md5Checksums;
        mySha1Checksums = sha1Checksums;
        myModifiedFiles = new TreeMap<String, SVNCommitItem>();
    }

    public static SVNCommitInfo commit(SVNWCContext context, Collection tmpFiles, Map<String, SVNCommitItem> committables, String repositoryRoot, ISVNEditor commitEditor,
            Map<File, SVNChecksum> md5Checksums, Map<File, SVNChecksum> sha1Checksums) throws SVNException {
        SVNCommitter17 committer = new SVNCommitter17(context, committables, repositoryRoot, tmpFiles, md5Checksums, sha1Checksums);
        SVNCommitUtil.driveCommitEditor(committer, committables.keySet(), commitEditor, -1);
        committer.sendTextDeltas(commitEditor);
        return commitEditor.closeEdit();
    }

    public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
        SVNCommitItem item = myCommittables.get(commitPath);
        myContext.checkCancelled();
        if (item.isCopied()) {
            if (item.getCopyFromURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Commit item ''{0}'' has copy flag but no copyfrom URL", item.getFile());
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (item.getRevision().getNumber() < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Commit item ''{0}'' has copy flag but an invalid revision", item.getFile());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        boolean closeDir = false;
        File localAbspath = null;
        if (item.getKind() != SVNNodeKind.NONE && item.getFile() != null) {
            localAbspath = item.getFile();
        }
        long rev = item.getRevision().getNumber();
        SVNEvent event = null;
        if (item.isAdded() && item.isDeleted()) {
            event = SVNEventFactory.createSVNEvent(localAbspath, item.getKind(), null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_REPLACED, null, null, null);
            event.setPreviousRevision(rev);
        } else if (item.isDeleted()) {
            event = SVNEventFactory.createSVNEvent(localAbspath, item.getKind(), null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_DELETED, null, null, null);
            event.setPreviousRevision(rev);
        } else if (item.isAdded()) {
            String mimeType = null;
            if (item.getKind() == SVNNodeKind.FILE && localAbspath != null) {
                mimeType = myContext.getProperty(localAbspath, SVNProperty.MIME_TYPE);
            }
            event = SVNEventFactory.createSVNEvent(localAbspath, item.getKind(), mimeType, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_ADDED, null, null, null);
            event.setPreviousRevision(item.getCopyFromRevision() != null ? item.getCopyFromRevision().getNumber() : -1);
            event.setPreviousURL(item.getCopyFromURL());
        } else if (item.isContentsModified() || item.isPropertiesModified()) {
            SVNStatusType contentState = SVNStatusType.UNCHANGED;
            if (item.isContentsModified()) {
                contentState = SVNStatusType.CHANGED;
            }
            SVNStatusType propState = SVNStatusType.UNCHANGED;
            if (item.isPropertiesModified()) {
                propState = SVNStatusType.CHANGED;
            }
            event = SVNEventFactory.createSVNEvent(localAbspath, item.getKind(), null, SVNRepository.INVALID_REVISION, contentState, propState, null, SVNEventAction.COMMIT_MODIFIED, null, null, null);
            event.setPreviousRevision(rev);
        }
        if (event != null) {
            event.setURL(item.getURL());
            myContext.getEventHandler().handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        if (item.isDeleted()) {
            try {
                commitEditor.deleteEntry(commitPath, rev);
            } catch (SVNException e) {
                fixError(commitPath, e, SVNNodeKind.FILE);
            }
        }
        long cfRev = item.getCopyFromRevision().getNumber();
        Map outgoingProperties = item.getOutgoingProperties();
        boolean fileOpen = false;
        if (item.isAdded()) {
            String copyFromPath = getCopyFromPath(item.getCopyFromURL());
            if (item.getKind() == SVNNodeKind.FILE) {
                commitEditor.addFile(commitPath, copyFromPath, cfRev);
                fileOpen = true;
            } else {
                commitEditor.addDir(commitPath, copyFromPath, cfRev);
                closeDir = true;
            }
            if (outgoingProperties != null) {
                for (Iterator propsIter = outgoingProperties.keySet().iterator(); propsIter.hasNext();) {
                    String propName = (String) propsIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) outgoingProperties.get(propName);
                    if (item.getKind() == SVNNodeKind.FILE) {
                        commitEditor.changeFileProperty(commitPath, propName, propValue);
                    } else {
                        commitEditor.changeDirProperty(propName, propValue);
                    }
                }
                outgoingProperties = null;
            }
        }
        if (item.isPropertiesModified() || (outgoingProperties != null && !outgoingProperties.isEmpty())) {
            if (item.getKind() == SVNNodeKind.FILE) {
                if (!fileOpen) {
                    try {
                        commitEditor.openFile(commitPath, rev);
                    } catch (SVNException e) {
                        fixError(commitPath, e, SVNNodeKind.FILE);
                    }
                }
                fileOpen = true;
            } else if (!item.isAdded()) {
                // do not open dir twice.
                try {
                    if ("".equals(commitPath)) {
                        commitEditor.openRoot(rev);
                    } else {
                        commitEditor.openDir(commitPath, rev);
                    }
                } catch (SVNException svne) {
                    fixError(commitPath, svne, SVNNodeKind.DIR);
                }
                closeDir = true;
            }
            if (item.isPropertiesModified()) {
                try {
                    sendPropertiesDelta(localAbspath, commitPath, item, commitEditor);
                } catch (SVNException e) {
                    fixError(commitPath, e, item.getKind());
                }
            }
            if (outgoingProperties != null) {
                for (Iterator propsIter = outgoingProperties.keySet().iterator(); propsIter.hasNext();) {
                    String propName = (String) propsIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) outgoingProperties.get(propName);
                    if (item.getKind() == SVNNodeKind.FILE) {
                        commitEditor.changeFileProperty(commitPath, propName, propValue);
                    } else {
                        commitEditor.changeDirProperty(propName, propValue);
                    }
                }
            }
        }
        if (item.isContentsModified() && item.getKind() == SVNNodeKind.FILE) {
            if (!fileOpen) {
                try {
                    commitEditor.openFile(commitPath, rev);
                } catch (SVNException e) {
                    fixError(commitPath, e, SVNNodeKind.FILE);
                }
            }
            myModifiedFiles.put(commitPath, item);
        } else if (fileOpen) {
            commitEditor.closeFile(commitPath, null);
        }
        return closeDir;
    }

    private void fixError(String path, SVNException e, SVNNodeKind kind) throws SVNException {
        SVNErrorMessage err = e.getErrorMessage();
        if (err.getErrorCode() == SVNErrorCode.FS_NOT_FOUND || err.getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
            err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, kind == SVNNodeKind.DIR ? "Directory ''{0}'' is out of date" : "File ''{0}'' is out of date", path);
            throw new SVNException(err);
        }
        throw e;
    }

    private String getCopyFromPath(SVNURL url) {
        if (url == null) {
            return null;
        }
        String path = url.getPath();
        if (myRepositoryRoot.equals(path)) {
            return "/";
        }
        return path.substring(myRepositoryRoot.length());
    }

    private void sendPropertiesDelta(File localAbspath, String commitPath, SVNCommitItem item, ISVNEditor commitEditor) throws SVNException {
        SVNNodeKind kind = myContext.readKind(localAbspath, false);
        SVNProperties propMods = myContext.getPropDiffs(localAbspath).propChanges;
        for (Object i : propMods.nameSet()) {
            String propName = (String) i;
            SVNPropertyValue propValue = propMods.getSVNPropertyValue(propName);
            if (kind == SVNNodeKind.FILE) {
                commitEditor.changeFileProperty(commitPath, propName, propValue);
            } else {
                commitEditor.changeDirProperty(propName, propValue);
            }
        }
    }

    private void sendTextDeltas(ISVNEditor commitEditor) {
        // TODO
        throw new UnsupportedOperationException();
    }

}
