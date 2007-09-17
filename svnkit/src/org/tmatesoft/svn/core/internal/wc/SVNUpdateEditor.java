/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNCleanupHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNUpdateEditor implements ISVNEditor, ISVNCleanupHandler {

    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private String myRootURL;
    private SVNAdminAreaInfo myAdminInfo;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    private boolean myIsLeaveConflicts;
    private boolean myIsUnversionedObstructionsAllowed;
    //File objects
    private Collection mySkippedPaths;
    private SVNWCAccess myWCAccess; 
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNDepth myDepth;
    private String[] myExtensionPatterns;
    private ISVNConflictHandler myConflictHandler;
    private ISVNFileFetcher myFileFetcher;
    
    public SVNUpdateEditor(SVNAdminAreaInfo info, String switchURL, boolean recursive, boolean leaveConflicts, boolean allowUnversionedObstructions) throws SVNException {
        this(info, switchURL, leaveConflicts, allowUnversionedObstructions, 
             SVNDepth.fromRecurse(recursive), null, null, null);
    }

    public SVNUpdateEditor(SVNAdminAreaInfo info, String switchURL, boolean leaveConflicts, 
                           boolean allowUnversionedObstructions, SVNDepth depth, 
                           String[] preservedExtensions, ISVNConflictHandler conflictHandler,
                           ISVNFileFetcher fileFetcher) throws SVNException {
        myAdminInfo = info;
        myWCAccess = info.getWCAccess();
        myIsUnversionedObstructionsAllowed = allowUnversionedObstructions;
        myTarget = info.getTargetName();
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myIsLeaveConflicts = leaveConflicts;
        myDepth = depth;
        myDeltaProcessor = new SVNDeltaProcessor();
        myExtensionPatterns = preservedExtensions;
        myConflictHandler = conflictHandler;
        myFileFetcher = fileFetcher;
        
        SVNEntry entry = info.getAnchor().getEntry(info.getAnchor().getThisDirName(), false);
        myTargetURL = entry != null ? entry.getURL() : null;
        myRootURL = entry != null ? entry.getRepositoryRoot() : null;
        if (myTarget != null) {
            myTargetURL = SVNPathUtil.append(myTargetURL, SVNEncodingUtil.uriEncode(myTarget));
        }
        if (mySwitchURL != null && entry != null && entry.getRepositoryRoot() != null) {
            if (!SVNPathUtil.isAncestor(entry.getRepositoryRoot(), mySwitchURL)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] {mySwitchURL, entry.getRepositoryRoot()});
                SVNErrorManager.error(err);
            }
        }
        myAdminInfo.getTarget().closeEntries();

        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, "", false);
        myWCAccess.registerCleanupHandler(myCurrentDirectory.getAdminArea(), myCurrentDirectory);
        if (myTarget == null) {
            SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
            SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), false);
            if (entry != null) {
                myCurrentDirectory.myDepth = entry.getDepth();
            }
            Map attributes = new HashMap();
            attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            attributes.put(SVNProperty.URL, myCurrentDirectory.URL);
            attributes.put(SVNProperty.INCOMPLETE, Boolean.TRUE.toString());
            if (myRootURL != null && SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
                attributes.put(SVNProperty.REPOS, myRootURL);
            }
            adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, false);
            
            if (mySwitchURL != null) {
                clearWCProperty(myCurrentDirectory.getAdminArea(), null);
            }
        }  else if (mySwitchURL != null) {
            if (myAdminInfo.getTarget() == myAdminInfo.getAnchor()) {
                clearWCProperty(myAdminInfo.getTarget(), myTarget);
            } else {
                clearWCProperty(myAdminInfo.getTarget(), null);
            }
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        checkIfPathIsUnderRoot(path);
        String name = SVNPathUtil.tail(path);
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = parentArea.getEntry(name, true);
        if (entry == null) {
            return;
        }

        SVNLog log = myCurrentDirectory.getLog();
        Map attributes = new HashMap();

        attributes.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.DELETE_ENTRY, attributes, false);
        SVNNodeKind kind = entry.getKind();
        boolean isDeleted = entry.isDeleted();
        if (path.equals(myTarget)) {
            attributes.put(SVNLog.NAME_ATTR, name);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.KIND), kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.TRUE.toString());
            log.addCommand(SVNLog.MODIFY_ENTRY, attributes, false);
            myIsTargetDeleted = true;
        }
        try {
            myCurrentDirectory.flushLog(); 
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Error writing log file for ''{0}''", myCurrentDirectory.getPath());
            SVNErrorManager.error(err, svne);
        }

        if (mySwitchURL != null && kind == SVNNodeKind.DIR) {
            SVNAdminArea childArea = myWCAccess.retrieve(parentArea.getFile(name));
            try {
                childArea.removeFromRevisionControl(childArea.getThisDirName(), true, true);
            } catch (SVNException svne) {
                handleLeftLocalModificationsError(svne, log, childArea);
            }
        }
        try {
            myCurrentDirectory.runLogs();
        } catch (SVNException svne) {
            handleLeftLocalModificationsError(svne, log, parentArea);
        }

        if (isDeleted) {
            // entry was deleted, but it was already deleted, no need to make a
            // notification.
            return;
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(parentArea, kind, name));
    }

    private void handleLeftLocalModificationsError(SVNException originalError, SVNLog log, SVNAdminArea adminArea) throws SVNException {
        SVNException error = null;
        for (error = originalError; error != null;) {
            if (error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                break;
            }
            error = (error.getCause() instanceof SVNException) ? (SVNException) error.getCause() : null; 
        }
        if (error != null) {
            log.delete();
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Won''t delete locally modified directory ''{0}''", adminArea.getRoot());
            SVNErrorManager.error(err, error);
        }
        throw originalError;
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        SVNDirectoryInfo parentDirectory = myCurrentDirectory;
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);
        parentDirectory.flushLog();
        checkIfPathIsUnderRoot(path);
        String name = SVNPathUtil.tail(path);
        File childDir = parentArea.getFile(name);
        SVNFileType kind = SVNFileType.getType(childDir);
        if (kind == SVNFileType.FILE || kind == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': " +
                    "a non-directory object of the same name already exists", path);
            SVNErrorManager.error(err);
        } 

        if (kind == SVNFileType.DIRECTORY) {
            SVNAdminArea adminArea = null;
            try {
                adminArea = SVNWCAccess.newInstance(null).open(childDir, false, 0);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                    throw svne;
                }
                if (myIsUnversionedObstructionsAllowed) {
                    myCurrentDirectory.isExisted = true;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': an unversioned directory of the same name already exists", myCurrentDirectory.getPath());
                    SVNErrorManager.error(err);
                }
            }

            if (adminArea != null) {
                SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), false);
                if (entry != null && entry.isScheduledForAddition() && !entry.isCopied()) {
                    myCurrentDirectory.isAddExisted = true;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': a versioned directory of the same name already exists", myCurrentDirectory.getPath());
                    SVNErrorManager.error(err);
                }
            }
        }
        
        if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'':  object of the same name as the administrative directory", path);
            SVNErrorManager.error(err);
        }
        if (copyFromPath != null || SVNRevision.isValidRevisionNumber(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Failed to add directory ''{0}'': copyfrom arguments not yet supported", path);
            SVNErrorManager.error(err);
        }
        
        SVNEntry entry = parentArea.getEntry(name, false);
        Map attributes = new HashMap();
        attributes.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
        attributes.put(SVNProperty.ABSENT, null);
        attributes.put(SVNProperty.DELETED, null);
        boolean force = false;
        if (myCurrentDirectory.isAddExisted) {
            attributes.put(SVNProperty.SCHEDULE, null);
            force = true;
        }
        entry = parentArea.modifyEntry(name, attributes, true, force);
        
        if (myCurrentDirectory.isAddExisted) {
            attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            if (mySwitchURL != null) {
                attributes.put(SVNProperty.URL, SVNPathUtil.append(mySwitchURL, name));
            }
            SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
            adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, true);
        }
        
        String rootURL = null;
        if (SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            rootURL = myRootURL;
        }
        if (myWCAccess.getAdminArea(childDir) != null) {
            myWCAccess.closeAdminArea(childDir);
        }
        
        if (SVNWCManager.ensureAdminAreaExists(childDir, myCurrentDirectory.URL, rootURL, null, 
                myTargetRevision, myCurrentDirectory.myDepth)) {
            // hack : remove created lock file.
            SVNFileUtil.deleteFile(new File(childDir, SVNFileUtil.getAdminDirectoryName() + "/lock"));
        }
        SVNAdminArea childArea = myWCAccess.open(childDir, true, 0);
        myWCAccess.registerCleanupHandler(childArea, myCurrentDirectory);
        if (!myCurrentDirectory.isAddExisted) {
            if (myCurrentDirectory.isExisted) {
                SVNEvent event = new SVNEvent(parentArea, entry.getName(), SVNEventAction.UPDATE_EXISTS, 
                                              SVNNodeKind.DIR, entry.getRevision(), null, null, null, null, null, null);
                myWCAccess.handleEvent(event);
            } else {
		        myWCAccess.handleEvent(SVNEventFactory.createUpdateAddEvent(parentArea, SVNNodeKind.DIR, entry));
            }
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory.flushLog();
        checkIfPathIsUnderRoot(path);
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea(); 
        myWCAccess.registerCleanupHandler(adminArea, myCurrentDirectory);
        SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), true);
        if (entry != null) {
            myCurrentDirectory.myDepth = entry.getDepth();
            boolean hasPropConflicts = adminArea.hasPropConflict(adminArea.getThisDirName());
            if (hasPropConflicts) {
                myCurrentDirectory.isSkipped = true;
                Collection skippedPaths = getSkippedPaths();
                skippedPaths.add(adminArea.getRoot());
                SVNEvent event = SVNEventFactory.createSkipEvent(adminArea, "", SVNEventAction.SKIP, SVNEventAction.UPDATE_UPDATE, SVNNodeKind.DIR, -1, SVNStatusType.INAPPLICABLE, SVNStatusType.CONFLICTED);
                myWCAccess.handleEvent(event);
                return;
            }
        }
        
        Map attributes = new HashMap();
        attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
        attributes.put(SVNProperty.URL, myCurrentDirectory.URL);
        attributes.put(SVNProperty.INCOMPLETE, Boolean.TRUE.toString());
        
        if (myRootURL != null && SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            attributes.put(SVNProperty.REPOS, myRootURL);
        }
        entry = adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, false);

        if (mySwitchURL != null) {
            clearWCProperty(myCurrentDirectory.getAdminArea(), null);
        }
    }

    private Collection getSkippedPaths() {
        if (mySkippedPaths == null) {
            mySkippedPaths = new LinkedList();
        }
        return mySkippedPaths;
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = adminArea.getEntry(name, false);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition", path);
            SVNErrorManager.error(err);
        }

        Map attributes = new HashMap();
        attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
        attributes.put(SVNProperty.KIND, kind.toString());
        attributes.put(SVNProperty.DELETED, null);
        attributes.put(SVNProperty.ABSENT, Boolean.TRUE.toString());
        entry = adminArea.modifyEntry(name, attributes, true, false);
        }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (!myCurrentDirectory.isSkipped) {
        myCurrentDirectory.propertyChanged(name, value);
        }
    }

    private void clearWCProperty(SVNAdminArea adminArea, String target) throws SVNException {
        if (adminArea == null) {
            return;
        }
        for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (target != null) {
                if (entry.isFile() && target.equals(entry.getName())) {
                    SVNVersionedProperties props = adminArea.getWCProperties(entry.getName());
                    props.setPropertyValue(SVNProperty.WC_URL, null);
                    adminArea.saveWCProperties(false);
                }
                continue;
            }
            if (entry.isFile() || adminArea.getThisDirName().equals(entry.getName())) {
                SVNVersionedProperties props = adminArea.getWCProperties(entry.getName());
                props.setPropertyValue(SVNProperty.WC_URL, null);
                adminArea.saveWCProperties(false);
            } else {
                SVNAdminArea childArea = myAdminInfo.getWCAccess().getAdminArea(adminArea.getFile(entry.getName()));
                clearWCProperty(childArea, null);
            }
        }
    }

    public void closeDir() throws SVNException {
        Map modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        Map modifiedProps = myCurrentDirectory.getChangedProperties();

        SVNStatusType propStatus = SVNStatusType.UNKNOWN;
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        if (modifiedWCProps != null || modifiedEntryProps != null || modifiedProps != null) {
            SVNLog log = myCurrentDirectory.getLog();
            if (modifiedProps != null && !modifiedProps.isEmpty()) {
                myAdminInfo.addExternals(adminArea, (String) modifiedProps.get(SVNProperty.EXTERNALS));

                SVNVersionedProperties oldBaseProps = adminArea.getBaseProperties(adminArea.getThisDirName());
                try {
                    propStatus = adminArea.mergeProperties(adminArea.getThisDirName(), oldBaseProps.asMap(), modifiedProps, true, false, log);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Couldn't do property merge");
                    SVNErrorManager.error(err, svne);
                }
            }
            log.logChangedEntryProperties(adminArea.getThisDirName(), modifiedEntryProps);
            log.logChangedWCProperties(adminArea.getThisDirName(), modifiedWCProps);
        }

        myCurrentDirectory.flushLog();
        myCurrentDirectory.runLogs();
        completeDirectory(myCurrentDirectory);
        if (!myCurrentDirectory.isSkipped && (myCurrentDirectory.isAddExisted || !myCurrentDirectory.IsAdded)) {
            if (!(adminArea == myAdminInfo.getAnchor() && !"".equals(myAdminInfo.getTargetName()))) {
                // skip event for anchor when there is a target.
                SVNEventAction action = myCurrentDirectory.isAddExisted || myCurrentDirectory.isExisted ? SVNEventAction.UPDATE_EXISTS : SVNEventAction.UPDATE_UPDATE;
                if (propStatus == SVNStatusType.UNKNOWN && action != SVNEventAction.UPDATE_EXISTS) {
                    action = SVNEventAction.UPDATE_NONE;
                }
                myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(adminArea, "", SVNNodeKind.DIR, action, null, SVNStatusType.UNKNOWN, propStatus, null));
            }
        }
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTarget != null && myWCAccess.isMissing(myAdminInfo.getAnchor().getFile(myTarget))) {
            myCurrentDirectory = createDirectoryInfo(null, "", false);
            myWCAccess.registerCleanupHandler(myCurrentDirectory.getAdminArea(), myCurrentDirectory);
            deleteEntry(myTarget, myTargetRevision);
        }

        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            File targetFile = myTarget != null ? myAdminInfo.getAnchor().getFile(myTarget) : myAdminInfo.getAnchor().getRoot(); 
            SVNWCManager.updateCleanup(targetFile, myWCAccess, mySwitchURL, myRootURL, myTargetRevision, true, mySkippedPaths, myDepth);
        }
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = addFile(myCurrentDirectory, path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = openFile(path, myCurrentDirectory);
    }

    public void changeFileProperty(String commitPath, String name, String value) throws SVNException {
        if (!myCurrentFile.isSkipped) {
            myCurrentFile.propertyChanged(name, value);
            if (myWCAccess.getOptions().isUseCommitTimes() && SVNProperty.COMMITTED_DATE.equals(name)) {
                myCurrentFile.CommitTime = value;
            }
        }
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        if (myCurrentFile.isSkipped) {
            return;
        }
        
        SVNAdminArea adminArea = myCurrentFile.getAdminArea();
        SVNEntry entry = adminArea.getEntry(myCurrentFile.Name, false);
        boolean replaced = entry != null && entry.isScheduledForReplacement();
        boolean useRevertBase = replaced && entry.getCopyFromURL() != null;

        if (useRevertBase) {
            myCurrentFile.baseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, false));
            myCurrentFile.newBaseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, true));
        } else {
            myCurrentFile.baseFile = adminArea.getBaseFile(myCurrentFile.Name, false);
            myCurrentFile.newBaseFile = adminArea.getBaseFile(myCurrentFile.Name, true);
        }
        
        if (entry != null && entry.getChecksum() != null) {
            String realChecksum = SVNFileUtil.computeChecksum(myCurrentFile.baseFile);
            if (baseChecksum != null) {
                if (!baseChecksum.equals(realChecksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                            new Object[] {myCurrentFile.baseFile, baseChecksum, realChecksum}); 
                    SVNErrorManager.error(err);
                }
            }
            
            String realChecksumSafe = realChecksum == null ? "" : realChecksum;
            String entryChecksumSafe = entry.getChecksum() == null ? "" : entry.getChecksum();
            if (!replaced && !realChecksumSafe.equals(entryChecksumSafe)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; recorded: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.baseFile, entry.getChecksum(), realChecksum}); 
                SVNErrorManager.error(err);
            }
        }
        
        File baseSrcFile = null;
        if (!myCurrentFile.IsAdded) {
            baseSrcFile = myCurrentFile.baseFile;
        }
        File baseTmpFile = myCurrentFile.newBaseFile;
        myDeltaProcessor.applyTextDelta(baseSrcFile, baseTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        if (!myCurrentFile.isSkipped) {
            try {
                return myDeltaProcessor.textDeltaChunk(diffWindow);
            } catch (SVNException svne) {
                myDeltaProcessor.textDeltaEnd();
                SVNFileUtil.deleteFile(myCurrentFile.newBaseFile);
                myCurrentFile.newBaseFile = null;
            }
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        if (!myCurrentFile.isSkipped) {
            myCurrentFile.Checksum = myDeltaProcessor.textDeltaEnd();
        }
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (myCurrentFile.isSkipped) {
            completeDirectory(myCurrentDirectory);
            return;
        }
        // check checksum.
        String checksum = null;
        boolean isTextUpdated = myCurrentFile.newBaseFile != null;
        if (textChecksum != null && isTextUpdated) {            
            if (myCurrentFile.Checksum != null && !textChecksum.equals(myCurrentFile.Checksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.getPath(), textChecksum, checksum}); 
                SVNErrorManager.error(err);
            }
            checksum = textChecksum;
        }
        SVNAdminArea adminArea = myCurrentFile.getAdminArea();
        SVNLog log = myCurrentDirectory.getLog();
        String name = myCurrentFile.Name;
        SVNEntry fileEntry = adminArea.getEntry(name, false);
        if (fileEntry == null && !myCurrentFile.IsAdded) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", myCurrentFile.getPath());
            SVNErrorManager.error(err);
        }

        // merge props.
        Map modifiedWCProps = myCurrentFile.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentFile.getChangedEntryProperties();
        Map modifiedProps = myCurrentFile.getChangedProperties();
        String commitTime = myCurrentFile.CommitTime;

        Map command = new HashMap();

        SVNStatusType textStatus = SVNStatusType.UNCHANGED;
        SVNStatusType lockStatus = SVNStatusType.LOCK_UNCHANGED;

        boolean magicPropsChanged = false;
        if (modifiedProps != null && !modifiedProps.isEmpty()) {
            magicPropsChanged = modifiedProps.containsKey(SVNProperty.EXECUTABLE) || 
            modifiedProps.containsKey(SVNProperty.NEEDS_LOCK) || 
            modifiedProps.containsKey(SVNProperty.KEYWORDS) || 
            modifiedProps.containsKey(SVNProperty.EOL_STYLE) || 
            modifiedProps.containsKey(SVNProperty.SPECIAL);
        }
        
        SVNVersionedProperties baseProps = adminArea.getBaseProperties(name);
        Map oldBaseProps = baseProps != null ? baseProps.asMap() : null;
        SVNStatusType propStatus = SVNStatusType.UNCHANGED;
        if (modifiedProps != null) {
            propStatus = adminArea.mergeProperties(name, oldBaseProps, modifiedProps, true, false, log);
        }
        if (modifiedEntryProps != null) {
            lockStatus = log.logChangedEntryProperties(name, modifiedEntryProps);
        }
        if (modifiedWCProps != null) {
            log.logChangedWCProperties(name, modifiedWCProps);
        }

        boolean isLocallyModified = false;
        if (!myCurrentFile.isExisted) { 
            isLocallyModified = adminArea.hasTextModifications(name, false, false, false);
        } else if (isTextUpdated) {
            isLocallyModified = adminArea.hasVersionedFileTextChanges(adminArea.getFile(name), myCurrentFile.newBaseFile, false);
            }
        
        boolean isReplaced = fileEntry != null && fileEntry.isScheduledForReplacement();
        
        Map logAttributes = new HashMap();
        if (myCurrentFile.isAddExisted) {
            logAttributes.put(SVNLog.FORCE_ATTR, "true");
            logAttributes.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), "");
        } 
        
        log.logTweakEntry(name, myCurrentFile.URL, myTargetRevision);
        
        String absDirPath = adminArea.getRoot().getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = null;
        if (myCurrentFile.baseFile != null) {
            String absBasePath = myCurrentFile.baseFile.getAbsolutePath().replace(File.separatorChar, '/');
            basePath = absBasePath.substring(absDirPath.length());
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1);
            }
        }
        
        String tmpBasePath = null;
        if (myCurrentFile.newBaseFile != null) {
            String absTmpBasePath = myCurrentFile.newBaseFile.getAbsolutePath().replace(File.separatorChar, '/');
            tmpBasePath = absTmpBasePath.substring(absDirPath.length());
            if (tmpBasePath.startsWith("/")) {
                tmpBasePath = tmpBasePath.substring(1);
            }
        }

        SVNStatusType mergeOutcome = SVNStatusType.UNCHANGED;
        File workingFile = adminArea.getFile(name);
        if (tmpBasePath != null) {
            textStatus = SVNStatusType.CHANGED;
            // there is a text to replace the working copy with.
            if (!isLocallyModified && !isReplaced) {
                command.put(SVNLog.NAME_ATTR, tmpBasePath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            } else {
                SVNFileType kind = SVNFileType.getType(workingFile);
                if (kind == SVNFileType.NONE) {
                    command.put(SVNLog.NAME_ATTR, tmpBasePath);
                    command.put(SVNLog.DEST_ATTR, name);
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                } else if (!myCurrentFile.isExisted) {
                    File mergeLeftFile = myCurrentFile.baseFile;
                    boolean usedTmpFile = false;

                    String pathExt = null;
                    if (myExtensionPatterns != null && myExtensionPatterns.length > 0) {
                        int dotInd = name.lastIndexOf('.'); 
                        if (dotInd != -1 && dotInd != 0 && dotInd != name.length() - 1) {
                            pathExt = name.substring(dotInd + 1);
                        }
                        if (pathExt != null && !"".equals(pathExt)) {
                            boolean matches = false;
                            for (int i = 0; i < myExtensionPatterns.length; i++) {
                                String extPattern = myExtensionPatterns[i];
                                matches = DefaultSVNOptions.matches(extPattern, pathExt);    
                                if (matches) {
                                    break;
                                }
                            }
                            if (!matches) {
                                pathExt = null;
                            }
                    }
                        }
                    
                    if (myCurrentFile.isAddExisted && !isReplaced) {
                        usedTmpFile = true;
                        mergeLeftFile = SVNAdminUtil.createTmpFile(adminArea);
                        }

                    String absMergeLeftFilePath = mergeLeftFile.getAbsolutePath().replace(File.separatorChar, '/');
                    String mergeLeftFilePath = absMergeLeftFilePath.substring(absDirPath.length());
                    if (mergeLeftFilePath.startsWith("/")) {
                        mergeLeftFilePath = mergeLeftFilePath.substring(1);
                    }

                    String leftLabel = ".r" + fileEntry.getRevision() + (pathExt != null ? "." + pathExt : "");
                    String rightLabel = ".r" + myTargetRevision + (pathExt != null ? "." + pathExt : "");
                    String mineLabel = ".mine" + (pathExt != null ? "." + pathExt : "");
                    // do test merge.
                    mergeOutcome = adminArea.mergeText(name, mergeLeftFile, 
                                                       adminArea.getFile(tmpBasePath), 
                                                       mineLabel, leftLabel, rightLabel, 
                                                       modifiedProps, myIsLeaveConflicts, 
                                                       false, null, log, myConflictHandler);
                    if (mergeOutcome == SVNStatusType.UNCHANGED) {
                        textStatus = SVNStatusType.MERGED;
                    }

                    if (usedTmpFile) {
                        command.put(SVNLog.NAME_ATTR, mergeLeftFilePath);
                        log.addCommand(SVNLog.DELETE, command, false);
                        command.clear();
                    }
                }
            }
        } else {
            if (magicPropsChanged && workingFile.exists()) {
                // only props were changed, but we have to retranslate file.
                // only if wc file exists (may be locally deleted), otherwise no
                // need to retranslate...
                String tmpPath = SVNAdminUtil.getTextBasePath(name, true);
                    command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.DEST_ATTR, tmpPath);
                log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                }
            if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
                command.put(SVNLog.NAME_ATTR, name);
                log.addCommand(SVNLog.MAYBE_READONLY, command, false);
                command.clear();
            }
        }
        
        if (tmpBasePath != null) {
            command.put(SVNLog.NAME_ATTR, tmpBasePath);
            command.put(SVNLog.DEST_ATTR, basePath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, basePath);
            log.addCommand(SVNLog.READONLY, command, false);
            command.clear();
            if (!isReplaced) {
                logAttributes.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            }
        }

        if (logAttributes.size() > 0) {
            logAttributes.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MODIFY_ENTRY, logAttributes, false);
        }
        if (!isLocallyModified) {
            if (commitTime != null && !myCurrentFile.isExisted) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.TIMESTAMP_ATTR, commitTime);
                log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                command.clear();
            }
            
            if (tmpBasePath != null || magicPropsChanged) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();
            }

            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.WORKING_SIZE), SVNLog.WC_WORKING_SIZE);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        // bump.
        completeDirectory(myCurrentDirectory);

        if (mergeOutcome == SVNStatusType.CONFLICTED_UNRESOLVED) {
            textStatus = SVNStatusType.CONFLICTED_UNRESOLVED;
        } else if (mergeOutcome == SVNStatusType.CONFLICTED) {
            textStatus = SVNStatusType.CONFLICTED;
        } else if (myCurrentFile.newBaseFile != null) {
            if (isLocallyModified) {
                textStatus = SVNStatusType.MERGED;
            } else {
                textStatus = SVNStatusType.CHANGED;
            }
        }

        // notify.
        if (myCurrentFile.sendNotification && (textStatus != SVNStatusType.UNCHANGED || 
                propStatus != SVNStatusType.UNCHANGED || 
                lockStatus != SVNStatusType.LOCK_UNCHANGED)) {
            SVNEventAction action = SVNEventAction.UPDATE_UPDATE;
            if (myCurrentFile.isExisted || myCurrentFile.isAddExisted) {
                if (textStatus != SVNStatusType.CONFLICTED_UNRESOLVED && textStatus != SVNStatusType.CONFLICTED) {
                    action = SVNEventAction.UPDATE_EXISTS;
                } 
            } else if (myCurrentFile.IsAdded) {
                action = SVNEventAction.UPDATE_ADD;
            }
            myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(adminArea, myCurrentFile.Name, SVNNodeKind.FILE, action, null, textStatus, propStatus, lockStatus));
        }
        
        myCurrentFile = null;
    }

    public void abortEdit() throws SVNException {
    }

    private void checkIfPathIsUnderRoot(String path) throws SVNException {
        if (SVNFileUtil.isWindows && path != null) { 
            String testPath = path.replace(File.separatorChar, '/');
            int ind = -1;
            
            while (testPath.length() > 0 && (ind = testPath.indexOf("..")) != -1) {
                if (ind == 0 || testPath.charAt(ind - 1) == '/') {
                    int i;
                    for (i = ind + 2; i < testPath.length(); i++) {
                        if (testPath.charAt(i) == '.') {
                            continue;
                        } else if (testPath.charAt(i) == '/') {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                                                         "Path ''{0}'' is not in the working copy",
                                                                         path);
                            SVNErrorManager.error(err);
                            
                        } else {
                            break;
                        }
                    }
                    if (i == testPath.length()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                                                     "Path ''{0}'' is not in the working copy",
                                                                     path);
                        SVNErrorManager.error(err);
                        
                    }
                    testPath = testPath.substring(i);
                } else {
                    testPath = testPath.substring(ind + 2);
                }
            }
        }
    }
    
    private void completeDirectory(SVNDirectoryInfo dirInfo) throws SVNException {
        while (dirInfo != null) {
            dirInfo.RefCount--;
            if (dirInfo.RefCount > 0) {
                return;
            }

            if (!dirInfo.isSkipped) {
                if (dirInfo.Parent == null && myTarget != null) {
                    return;
                }
                SVNAdminArea adminArea = dirInfo.getAdminArea();
                SVNEntry thisEntry = adminArea.getEntry(adminArea.getThisDirName(), true); 
                if (thisEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No ''.'' entry found in ''{0}''", adminArea.getRoot());
                    SVNErrorManager.error(err);
                }
                
                thisEntry.setIncomplete(false);
                if (myDepth == SVNDepth.INFINITY || ( adminArea.getRoot().equals(myTarget) && 
                        myDepth.compareTo(thisEntry.getDepth()) > 0)) {
                    thisEntry.setDepth(myDepth);
                }
                
                for (Iterator ents = adminArea.entries(true); ents.hasNext();) {
                    SVNEntry entry = (SVNEntry) ents.next();
                    if (entry.isDeleted()) {
                        if (!entry.isScheduledForAddition()) {
                            adminArea.deleteEntry(entry.getName());
                        } else {
                            Map attributes = new HashMap();
                            attributes.put(SVNProperty.DELETED, null);
                            adminArea.modifyEntry(entry.getName(), attributes, false, false);
                        }
                    } else if (entry.isAbsent() && entry.getRevision() != myTargetRevision) {
                        adminArea.deleteEntry(entry.getName());
                    } else if (entry.getKind() == SVNNodeKind.DIR) {
                        if (myWCAccess.isMissing(adminArea.getFile(entry.getName())) && !entry.isAbsent() && !entry.isScheduledForAddition()) {
                            adminArea.deleteEntry(entry.getName());
                            myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(adminArea, entry));
                        }
                    }
                }
                adminArea.saveEntries(true);
            }
            dirInfo = dirInfo.Parent;
        }
    }

    private SVNFileInfo addFile(SVNDirectoryInfo parent, String path, String copyFromPath, 
            long copyFromRevision) throws SVNException {
        if (copyFromPath != null || SVNRevision.isValidRevisionNumber(copyFromRevision)) {
            if (copyFromPath == null || !SVNRevision.isValidRevisionNumber(copyFromRevision)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, 
                        "Bad copyfrom arguments received.");
                SVNErrorManager.error(err);
            }
            return addFileWithHistory(parent, path, copyFromPath, copyFromRevision);
        }

        checkIfPathIsUnderRoot(path);

        SVNFileInfo info = createFileInfo(parent, path, true);
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNFileType kind = SVNFileType.getType(adminArea.getFile(info.Name));
        SVNEntry entry = adminArea.getEntry(info.Name, true);
        
        if (kind != SVNFileType.NONE) {
            if (myIsUnversionedObstructionsAllowed || (entry != null && 
                    entry.isScheduledForAddition())) {
                if (entry != null && entry.isCopied()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add file ''{0}'': a file of the same name is already scheduled for addition with history", 
                            path);
                    SVNErrorManager.error(err);
                }
                if (kind != SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add file ''{0}'': a non-file object of the same name already exists", 
                            path);
                    SVNErrorManager.error(err);
                }
                if (entry != null) {
                    info.isAddExisted = true;
                } else {
                    info.isExisted = true;
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                        "Failed to add file ''{0}'': object of the same name already exists", path);
                SVNErrorManager.error(err);
            }
        }

        return info;
    }

    private SVNFileInfo addFileWithHistory(SVNDirectoryInfo parent, String path, 
            String copyFromPath, long copyFromRevision) throws SVNException {
        if (myFileFetcher == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, 
                    "No fetch_func supplied to update_editor.");
            SVNErrorManager.error(err);
        }
        
        SVNFileInfo info = addFile(parent, path, null, SVNRepository.INVALID_REVISION);
        SVNAdminArea adminArea = parent.getAdminArea();
        info.baseFile = adminArea.getBaseFile(info.Name, false);
        info.newBaseFile = adminArea.getBaseFile(info.Name, true);
        Map fileProps = new HashMap();
        OutputStream baseTextOS = null;
        try {
            baseTextOS = SVNFileUtil.openFileForWriting(info.baseFile);
            myFileFetcher.fetchFile(copyFromPath, copyFromRevision, baseTextOS, fileProps);
        } finally {
            SVNFileUtil.closeFile(baseTextOS);
        }
        
        for (Iterator propNames = fileProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propVal = (String) fileProps.get(propName);
            changeFileProperty(path, propName, propVal);
        }
        
        parent.flushLog();
        parent.runLogs();
        info = openFile(path, myCurrentDirectory);
        info.sendNotification = false;
        return info;
    }
    
    private SVNFileInfo openFile(String path, SVNDirectoryInfo parent) throws SVNException {
        checkIfPathIsUnderRoot(path);
        SVNFileInfo info = createFileInfo(parent, path, false);
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNEntry entry = adminArea.getEntry(info.Name, true);

        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, 
                    "File ''{0}'' in directory ''{1}'' is not a versioned resource", 
                    new Object[] {info.Name, adminArea.getRoot()});
            SVNErrorManager.error(err);
        }
            
        boolean hasTextConflicts = adminArea.hasTextConflict(info.Name);
        boolean hasPropConflicts = adminArea.hasPropConflict(info.Name);
        if (hasTextConflicts || hasPropConflicts) {
            info.isSkipped = true;
            Collection skippedPaths = getSkippedPaths();
            File file = new File(myAdminInfo.getAnchor().getRoot(), path);
            skippedPaths.add(file);
            SVNEvent event = SVNEventFactory.createSkipEvent(adminArea, info.Name, 
                    SVNEventAction.SKIP, SVNEventAction.UPDATE_UPDATE, SVNNodeKind.FILE, -1, 
                    hasTextConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN, 
                            hasPropConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN);
            myWCAccess.handleEvent(event);
        }
        return info;
    }
    
    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        info.isExisted = false;
        info.isAddExisted = false;
        info.isSkipped = false;
        info.baseFile = null;
        info.newBaseFile = null;
        info.sendNotification = true;

        SVNAdminArea adminArea = parent.getAdminArea();
        SVNEntry entry = adminArea.getEntry(info.Name, true);

        if (mySwitchURL != null || entry == null) {
            info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
        } else {
            info.URL = entry.getURL();
        }
        parent.RefCount++;
        return info;
    }
    
    private SVNFileInfo createFileInfoOld(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        checkIfPathIsUnderRoot(path);
        
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        info.isExisted = false;
        info.isAddExisted = false;
        info.isSkipped = false;
        info.baseFile = null;
        info.newBaseFile = null;
        info.sendNotification = true;
        
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNFileType kind = SVNFileType.getType(adminArea.getFile(info.Name));
        SVNEntry entry = adminArea.getEntry(info.Name, true);

        if (added && kind != SVNFileType.NONE) {
            if (myIsUnversionedObstructionsAllowed || (entry != null && entry.isScheduledForAddition())) {
                if (entry != null && entry.isCopied()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': a file of the same name is already scheduled for addition with history", path);
                    SVNErrorManager.error(err);
                }
                if (kind != SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': a non-file object of the same name already exists", path);
                    SVNErrorManager.error(err);
                }
                if (entry != null) {
                    info.isAddExisted = true;
                } else {
                    info.isExisted = true;
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': object of the same name already exists", path);
                SVNErrorManager.error(err);
            }
        }

        if (!added && entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "File ''{0}'' in directory ''{1}'' is not a versioned resource", 
                    new Object[] {info.Name, adminArea.getRoot()});
            SVNErrorManager.error(err);
        }
            
        if (!added) {
            boolean hasTextConflicts = adminArea.hasTextConflict(info.Name);
            boolean hasPropConflicts = adminArea.hasPropConflict(info.Name);
            if (hasTextConflicts || hasPropConflicts) {
                info.isSkipped = true;
                Collection skippedPaths = getSkippedPaths();
                File file = new File(myAdminInfo.getAnchor().getRoot(), path);
                skippedPaths.add(file);
                SVNEvent event = SVNEventFactory.createSkipEvent(adminArea, info.Name, SVNEventAction.SKIP, added ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE, SVNNodeKind.FILE, -1, hasTextConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN, hasPropConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN);
                myWCAccess.handleEvent(event);
            }
        }

        if (mySwitchURL != null || entry == null) {
            info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
        } else {
            info.URL = entry.getURL();
        }
        parent.RefCount++;
        return info;
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, 
            boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? SVNPathUtil.tail(path) : "";

        if (added) {
            if ((myTarget == null && path == null) || (myTarget != null && myTarget.equals(path))) {
                info.myDepth = myDepth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : myDepth;
            } else if (myDepth == SVNDepth.IMMEDIATES || (myDepth == SVNDepth.UNKNOWN && 
                    parent.myDepth == SVNDepth.UNKNOWN)) {
                info.myDepth = SVNDepth.EMPTY;
            } else {
                info.myDepth = SVNDepth.INFINITY;
            }
        } else {
            info.myDepth = SVNDepth.INFINITY;
        }
        
        if (mySwitchURL == null) {
            SVNAdminArea area = null;
            SVNEntry dirEntry = null;
            try {
                area = info.getAdminArea();
                if (area != null) {
                    // could be missing.
                    dirEntry = area.getEntry(area.getThisDirName(), false);
                }
            } catch (SVNException svne) {
                //
            }
            
            if (area != null && dirEntry != null) {
                info.URL = dirEntry.getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : SVNPathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
                }
            }
        }
        info.RefCount = 1;
        info.isSkipped = false;
        if (info.Parent != null) {
            info.Parent.RefCount++;
        }
        info.isExisted = false;
        info.isAddExisted = false;
        info.log = null;
        return info;
    }

    private class SVNEntryInfo {

        public String URL;
        public boolean IsAdded;
        public boolean isExisted;
        public boolean isAddExisted;
        public SVNDirectoryInfo Parent;
        public boolean isSkipped;

        private String myPath;
        private Map myChangedProperties;
        private Map myChangedEntryProperties;
        private Map myChangedWCProperties;

        protected SVNEntryInfo(String path) {
            myPath = path;
        }

        protected String getPath() {
            return myPath;
        }

        public void propertyChanged(String name, String value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new HashMap() : myChangedEntryProperties;
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new HashMap() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new HashMap() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }

        public Map getChangedWCProperties() {
            return myChangedWCProperties;
        }

        public Map getChangedEntryProperties() {
            return myChangedEntryProperties;
        }

        public Map getChangedProperties() {
            return myChangedProperties;
        }
    }

    private class SVNFileInfo extends SVNEntryInfo {

        public String Name;
        public String CommitTime;
        public String Checksum;
        public File baseFile;
        public File newBaseFile;
        public boolean sendNotification;
        
        public SVNFileInfo(SVNDirectoryInfo parent, String path) {
            super(path);
            this.Parent = parent;
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            return Parent.getAdminArea();
        }
    }

    private class SVNDirectoryInfo extends SVNEntryInfo implements ISVNCleanupHandler {

        public int RefCount;
        private SVNLog log;
        public int LogCount;
        private SVNDepth myDepth;
        
        public SVNDirectoryInfo(String path) {
            super(path);
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            String path = getPath();
            File file = new File(myAdminInfo.getAnchor().getRoot(), path);
            return myAdminInfo.getWCAccess().retrieve(file);
        }

        public SVNLog getLog() throws SVNException {
            if (log == null) {
                log = getAdminArea().getLog();    
                LogCount++;
            }
            return log;
        }

        public void flushLog() throws SVNException {
            if (log != null) {
                log.save();
                log = null;
        }
        }

        public void runLogs() throws SVNException {
            LogCount = 0;
            getAdminArea().runLogs();
        }

        public void cleanup(SVNAdminArea area) throws SVNException {
            if (area != null && LogCount > 0) {
                LogCount = 0;
                area.runLogs();
            }
        }
    }

    public void cleanup(SVNAdminArea area) throws SVNException {
        area.runLogs();
    }
}
