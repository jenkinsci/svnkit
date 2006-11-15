/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRemoteDiffEditor implements ISVNEditor {

    private SVNRepository myRepos;
    private long myRevision1;
    private long myRevision2;
    private File myTarget;
    private SVNAdminAreaInfo myAdminInfo;
    private boolean myIsDryRun;
    
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myEventHandler;
    private ISVNEventHandler myCancelHandler;
    private AbstractDiffCallback myDiffCallback;

    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private File myTempDirectory;
    private Collection myTempFiles;

    public SVNRemoteDiffEditor(SVNAdminAreaInfo info, File target, AbstractDiffCallback callback,
            SVNRepository repos, long revision1, long revision2, boolean dryRun, ISVNEventHandler handler,
            ISVNEventHandler cancelHandler) {
        myAdminInfo = info;
        myTarget = target;
        myDiffCallback = callback;
        myRepos = repos;
        myRevision1 = revision1;
        myRevision2 = revision2;
        myEventHandler = handler;
        myCancelHandler = cancelHandler;
        myDeltaProcessor = new SVNDeltaProcessor();
        myIsDryRun = dryRun;
    }

    public void targetRevision(long revision) throws SVNException {
        myRevision2 = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
        myCurrentDirectory.loadFromRepository();
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNStatusType type = SVNStatusType.INAPPLICABLE;
        SVNEventAction action = SVNEventAction.SKIP;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_DELETE;
        
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNAdminArea dir = retrieve(myCurrentDirectory.myWCFile, true);
        
        if (myAdminInfo == null || dir != null) {
            if (nodeKind == SVNNodeKind.FILE) {
                SVNFileInfo file = new SVNFileInfo(path, false);
                file.loadFromRepository();
                String baseType = (String) file.myBaseProperties.get(SVNProperty.MIME_TYPE);
                type = getDiffCallback().fileDeleted(path, file.myBaseFile, null, baseType, null, file.myBaseProperties);
            } else if (nodeKind == SVNNodeKind.DIR) {
                type = getDiffCallback().directoryDeleted(path);
            }
            if (type != SVNStatusType.MISSING && type != SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.UPDATE_DELETE;
                if (myIsDryRun) {
                    getDiffCallback().addDeletedPath(path);
                }
            }
        }
        if (myEventHandler != null) {
            SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, path, action, expectedAction, type, type, nodeKind);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)  throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myBaseProperties = Collections.EMPTY_MAP;
        
        SVNEventAction expectedAction = SVNEventAction.UPDATE_ADD;
        SVNEventAction action = expectedAction;
        SVNStatusType type = getDiffCallback().directoryAdded(path, myRevision2);
        if (myEventHandler != null) {
            if (type == SVNStatusType.MISSING || type == SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.SKIP; 
            }
            // TODO prop type?
            SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, path, action, expectedAction, type, type, SVNNodeKind.DIR);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, false);
        myCurrentDirectory.loadFromRepository();
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        SVNStatusType type = SVNStatusType.UNKNOWN;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_UPDATE;
        SVNEventAction action = expectedAction;
        
        if (myIsDryRun) {
            getDiffCallback().clearDeletedPaths();
        }
        
        SVNAdminArea dir = null;
        if (!myCurrentDirectory.myPropertyDiff.isEmpty()) {
            try {
                dir = retrieve(myCurrentDirectory.myWCFile, myIsDryRun);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    if (myEventHandler != null) {
                        action = SVNEventAction.SKIP;
                        SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, myCurrentDirectory.myRepositoryPath, action, 
                                expectedAction, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNNodeKind.DIR);
                        myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    return;
                } 
                throw e;
            }
            if (!myIsDryRun || dir != null) {
                type = getDiffCallback().propertiesChanged(myCurrentDirectory.myRepositoryPath, 
                        myCurrentDirectory.myBaseProperties, myCurrentDirectory.myPropertyDiff);
            }
        }
        if (!myCurrentDirectory.myIsAdded && myEventHandler != null) {
            if (type == SVNStatusType.UNKNOWN) {
                action = SVNEventAction.UPDATE_NONE;
            }
            SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, myCurrentDirectory.myRepositoryPath, action, 
                    expectedAction, SVNStatusType.INAPPLICABLE, type, SVNNodeKind.DIR);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = new SVNFileInfo(path, true);
        myCurrentFile.myBaseProperties = Collections.EMPTY_MAP;
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
        SVNFileUtil.createEmptyFile(myCurrentFile.myBaseFile);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = new SVNFileInfo(path, false);
        myCurrentFile.loadFromRepository();
    }

    public void changeFileProperty(String commitPath, String name, String value) throws SVNException {
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        SVNAdminArea dir = null;
        try {
            dir = retrieveParent(myCurrentFile.myWCFile, true);
        } catch (SVNException e) {
            dir = null;
        }
        myCurrentFile.myFile = createTempFile(dir, SVNPathUtil.tail(commitPath));
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        SVNEventAction expectedAction = myCurrentFile.myIsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
        SVNEventAction action = expectedAction;
        SVNStatusType[] type = {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        
        try {
            retrieveParent(myCurrentFile.myWCFile, myIsDryRun);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                if (myEventHandler != null) {
                    action = SVNEventAction.SKIP;
                    SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, commitPath, action, 
                            expectedAction, SVNStatusType.MISSING, SVNStatusType.UNKNOWN, SVNNodeKind.FILE);
                    myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
                return;
            } 
            throw e;
        }
        if (myCurrentFile.myFile != null || !myCurrentFile.myPropertyDiff.isEmpty()) {
            String baseMimeType = (String) myCurrentFile.myBaseProperties.get(SVNProperty.MIME_TYPE);
            String mimeType = (String) myCurrentFile.myPropertyDiff.get(SVNProperty.MIME_TYPE);
            if (myCurrentFile.myIsAdded) {
                type = getDiffCallback().fileAdded(commitPath, 
                        myCurrentFile.myFile != null ? myCurrentFile.myBaseFile : null, myCurrentFile.myFile, 
                        0, myRevision2, baseMimeType, mimeType, 
                        myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
            } else {
                type = getDiffCallback().fileChanged(commitPath, 
                        myCurrentFile.myFile != null ? myCurrentFile.myBaseFile : null, myCurrentFile.myFile, 
                        myRevision1, myRevision2, baseMimeType, mimeType, 
                        myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
            }
        }
        if (myEventHandler != null) {
            if (type[0] == SVNStatusType.MISSING || type[0] == SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.SKIP;
            } else if (myCurrentFile.myIsAdded) {
                action = SVNEventAction.UPDATE_ADD;
            } else {
                action = SVNEventAction.UPDATE_UPDATE;
            }
            SVNEvent event = SVNEventFactory.createMergeEvent(myAdminInfo, commitPath, action, 
                    expectedAction, type[0], type[1], SVNNodeKind.FILE);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void cleanup() throws SVNException {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
            myTempDirectory = null;
        }
        if (myTempFiles != null) {
            for(Iterator files = myTempFiles.iterator(); files.hasNext();) {
                SVNFileUtil.deleteFile((File) files.next());
            }
        }
    }
    
    protected SVNAdminArea retrieve(File path, boolean lenient) throws SVNException {
        if (myAdminInfo == null) {
            return null;
        }
        try {
            return myAdminInfo.getWCAccess().retrieve(path);
        } catch (SVNException e) {
            if (lenient) {
                return null;
            }
            throw e;
        }
    }

    protected SVNAdminArea retrieveParent(File path, boolean lenient) throws SVNException {
        if (myAdminInfo == null) {
            return null;
        }
        return retrieve(path.getParentFile(), lenient);
    }
    
    protected AbstractDiffCallback getDiffCallback() {
        return myDiffCallback;
    }
    
    protected File getTempDirectory() throws SVNException {
        if (myTempDirectory == null) {
            myTempDirectory = getDiffCallback().createTempDirectory();
        }
        return myTempDirectory;
    }
    
    protected File createTempFile(SVNAdminArea dir, String name) throws SVNException {
        if (dir != null && dir.isLocked()) {
            File tmpFile = dir.getBaseFile(name, true);
            if (myTempFiles == null) {
                myTempFiles = new HashSet();
            }
            myTempFiles.add(tmpFile);
            return tmpFile;
        }
        return SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
        
    }

    private class SVNDirectoryInfo {

        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myRepositoryPath = path;
            myWCFile = myTarget != null ? new File(myTarget, path) : null;
            myIsAdded = added;
            myPropertyDiff = new HashMap();
        }

        public void loadFromRepository() throws SVNException {
            myBaseProperties = new HashMap();
            myRepos.getDir(myRepositoryPath, myRevision1, myBaseProperties, (ISVNDirEntryHandler) null);
        }

        private boolean myIsAdded;
        private String myRepositoryPath;
        private File myWCFile;
        
        private Map myBaseProperties;
        private Map myPropertyDiff;
        
        private SVNDirectoryInfo myParent;
    }

    private class SVNFileInfo {

        public SVNFileInfo(String path, boolean added) {
            myRepositoryPath = path;
            myIsAdded = added;
            myWCFile = myTarget != null ? new File(myTarget, path) : null;
            myPropertyDiff = new HashMap();
        }

        public void loadFromRepository() throws SVNException {
            myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
            OutputStream os = null;
            myBaseProperties = new HashMap();
            try {
                os = SVNFileUtil.openFileForWriting(myBaseFile);
                myRepos.getFile(myRepositoryPath, myRevision1, myBaseProperties, new SVNCancellableOutputStream(os, myCancelHandler));
            } finally {
                SVNFileUtil.closeFile(os);
            }
        }

        private String myRepositoryPath;
        private File myWCFile;
        private boolean myIsAdded;
        
        private File myFile;
        private File myBaseFile;
        private Map myBaseProperties;
        private Map myPropertyDiff;
    }
}
