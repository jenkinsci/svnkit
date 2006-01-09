/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNCommitter implements ISVNCommitPathHandler {

    private Map myCommitItems;
    private Map myModifiedFiles;
    private Collection myTmpFiles;
    private String myRepositoryRoot;
    private SVNDeltaGenerator myDeltaGenerator;

    public SVNCommitter(Map commitItems, String reposRoot, Collection tmpFiles) {
        myCommitItems = commitItems;
        myModifiedFiles = new TreeMap();
        myTmpFiles = tmpFiles;
        myRepositoryRoot = reposRoot;
    }

    public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(commitPath);
        SVNWCAccess wcAccess = item.getWCAccess();
        wcAccess.checkCancelled();
        if (item.isCopied()) {
            if (item.getCopyFromURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Commit item ''{0}'' has copy flag but no copyfrom URL", item.getFile());                    
                SVNErrorManager.error(err);
            } else if (item.getRevision().getNumber() < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Commit item ''{0}'' has copy flag but an invalid revision", item.getFile());                    
                SVNErrorManager.error(err);
            }
        }
        SVNEvent event = null;
        boolean closeDir = false;

        if (item.isAdded() && item.isDeleted()) {
            event = SVNEventFactory.createCommitEvent(wcAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_REPLACED, item.getKind(), null);
        } else if (item.isAdded()) {
            String mimeType = null;
            if (item.getKind() == SVNNodeKind.FILE) {
                SVNWCAccess tmpWCAccess = SVNWCAccess.create(item.getFile());
                mimeType = tmpWCAccess.getAnchor().getProperties(tmpWCAccess.getTargetName(), false).getPropertyValue(SVNProperty.MIME_TYPE);
            }
            event = SVNEventFactory.createCommitEvent(wcAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_ADDED, item.getKind(), mimeType);
        } else if (item.isDeleted()) {
            event = SVNEventFactory.createCommitEvent(wcAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_DELETED, item.getKind(), null);
        } else if (item.isContentsModified() || item.isPropertiesModified()) {
            event = SVNEventFactory.createCommitEvent(wcAccess.getAnchor().getRoot(), item.getFile(), SVNEventAction.COMMIT_MODIFIED, item.getKind());
        }
        if (event != null) {
            event.setPath(item.getPath());
            wcAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        long rev = item.getRevision().getNumber();
        long cfRev = item.getCopyFromURL() != null ? rev : -1;
        if (item.isDeleted()) {
            commitEditor.deleteEntry(commitPath, rev);
        }
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
        }
        if (item.isPropertiesModified()) {
            if (item.getKind() == SVNNodeKind.FILE) {
                if (!fileOpen) {
                    commitEditor.openFile(commitPath, rev);
                }
                fileOpen = true;
            } else if (!item.isAdded()) {
                // do not open dir twice.
                if ("".equals(commitPath)) {
                    commitEditor.openRoot(rev);
                } else {
                    commitEditor.openDir(commitPath, rev);
                }
                closeDir = true;
            }
            sendPropertiedDelta(commitPath, item, commitEditor);
        }
        if (item.isContentsModified() && item.getKind() == SVNNodeKind.FILE) {
            if (!fileOpen) {
                commitEditor.openFile(commitPath, rev);
            }
            myModifiedFiles.put(commitPath, item);
        } else if (fileOpen) {
            commitEditor.closeFile(commitPath, null);
        }
        return closeDir;
    }

    public void sendTextDeltas(ISVNEditor editor) throws SVNException {
        for (Iterator paths = myModifiedFiles.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNCommitItem item = (SVNCommitItem) myModifiedFiles.get(path);
            SVNWCAccess wcAccess = item.getWCAccess();
            wcAccess.checkCancelled();

            SVNEvent event = SVNEventFactory.createCommitEvent(wcAccess.getAnchor().getRoot(), item.getFile(),
                    SVNEventAction.COMMIT_DELTA_SENT, SVNNodeKind.FILE, null);
            wcAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);

            SVNDirectory dir = wcAccess.getDirectory(SVNPathUtil.removeTail(item.getPath()));
            String name = SVNPathUtil.tail(item.getPath());
            SVNEntry entry = dir.getEntries().getEntry(name, false);

            File tmpFile = dir.getBaseFile(name, true);
            myTmpFiles.add(tmpFile);
            SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);

            String checksum = null;
            if (!item.isAdded()) {
                checksum = SVNFileUtil.computeChecksum(dir.getBaseFile(name, false));
                String realChecksum = entry.getChecksum();
                if (realChecksum != null && !realChecksum.equals(checksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                            new Object[] {dir.getFile(name), realChecksum, checksum}); 
                    SVNErrorManager.error(err);
                }
            }
            editor.applyTextDelta(path, checksum);
            if (myDeltaGenerator == null) {
                myDeltaGenerator = new SVNDeltaGenerator();
            }
            InputStream sourceIS = null;
            InputStream targetIS = null;
            File baseFile = dir.getBaseFile(name, false);
            String newChecksum = null;
            try {
                sourceIS = baseFile.exists() ? SVNFileUtil.openFileForReading(dir.getBaseFile(name, false)) : SVNFileUtil.DUMMY_IN;
                targetIS = tmpFile.exists() ? SVNFileUtil.openFileForReading(tmpFile) : SVNFileUtil.DUMMY_IN;
                newChecksum = myDeltaGenerator.sendDelta(path, sourceIS, targetIS, editor, true);
            } finally {
                SVNFileUtil.closeFile(sourceIS);
                SVNFileUtil.closeFile(targetIS);
            }
            editor.closeFile(path, newChecksum);
        }
    }

    private void sendPropertiedDelta(String commitPath, SVNCommitItem item, ISVNEditor editor) throws SVNException {
        SVNDirectory dir;
        String name;
        SVNWCAccess wcAccess = item.getWCAccess();
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = wcAccess.getDirectory(item.getPath());
            name = "";
        } else {
            dir = wcAccess.getDirectory(SVNPathUtil.removeTail(item.getPath()));
            name = SVNPathUtil.tail(item.getPath());
        }
        SVNEntry entry = dir.getEntries().getEntry(name, false);
        boolean replaced = false;
        if (entry != null) {
            replaced = entry.isScheduledForReplacement();
        }
        SVNProperties props = dir.getProperties(name, false);
        SVNProperties baseProps = replaced ? null : dir.getBaseProperties(name,
                false);
        Map diff = replaced ? props.asMap() : baseProps.compareTo(props);
        if (diff != null && !diff.isEmpty()) {
            SVNProperties tmpProps = dir.getBaseProperties(name, true);
            props.copyTo(tmpProps);
            if (!tmpProps.getFile().exists()) {
                // create empty tmp (!) file just to make sure it will be used on post-commit.
                SVNFileUtil.createEmptyFile(tmpProps.getFile());
            }
            myTmpFiles.add(tmpProps.getFile());

            for (Iterator names = diff.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String value = (String) diff.get(propName);
                if (item.getKind() == SVNNodeKind.FILE) {
                    editor.changeFileProperty(commitPath, propName, value);
                } else {
                    editor.changeDirProperty(propName, value);
                }
            }
        }
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

    public static SVNCommitInfo commit(Collection tmpFiles, Map commitItems, String repositoryRoot, ISVNEditor commitEditor) throws SVNException {
        SVNCommitter committer = new SVNCommitter(commitItems, repositoryRoot, tmpFiles);
        SVNCommitUtil.driveCommitEditor(committer, commitItems.keySet(), commitEditor, -1);
        committer.sendTextDeltas(commitEditor);

        return commitEditor.closeEdit();
    }
}
