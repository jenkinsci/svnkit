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
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNMergeCallback extends AbstractDiffCallback {

    private boolean myIsDryRun;
    private SVNURL myURL;

    private boolean myIsAddNecessitatedMerge;
    private String myAddedPath = null;
    private boolean myIsForce;
    private SVNDiffOptions myDiffOptions;
    
    public SVNMergeCallback(SVNAdminAreaInfo info, SVNURL url, boolean force, boolean dryRun, SVNDiffOptions options) {
        super(info);
        myURL = url;
        myIsDryRun = dryRun;
        myIsForce = force;
        myDiffOptions = options;
    }

    public File createTempDirectory() throws SVNException {
        return SVNFileUtil.createTempDirectory("merge");
    }

    public boolean isDiffUnversioned() {
        return false;
    }

    public SVNStatusType propertiesChanged(String path, Map originalProperties, Map diff) throws SVNException {
        Map regularProps = new HashMap();
        categorizeProperties(diff, regularProps, null, null);
        if (regularProps.isEmpty()) {
            return SVNStatusType.UNKNOWN;
        }
        try {
            File file = getFile(path);
            SVNStatusType result = SVNPropertiesManager.mergeProperties(getWCAccess(), file, originalProperties, regularProps, false, myIsDryRun);
            return result;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE || 
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND) {
                return SVNStatusType.MISSING;
            }
            throw e;
        }
    }

    public SVNStatusType directoryAdded(String path, long revision) throws SVNException {
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            if (myIsDryRun && myAddedPath != null && SVNPathUtil.isAncestor(myAddedPath, path)) {
                return SVNStatusType.CHANGED;
            } 
            return SVNStatusType.MISSING;
        }
        
        SVNURL copyFromURL = myURL.appendPath(path, false);
        // TODO protocol
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.NONE) {
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry != null && !entry.isScheduledForDeletion()) {
                return SVNStatusType.OBSTRUCTED;
            }
            if (!myIsDryRun) {
                if (!mergedFile.mkdirs()) {
                    if (SVNFileType.getType(mergedFile) != SVNFileType.DIRECTORY) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", mergedFile);
                        SVNErrorManager.error(err);
                    }
                }
                ISVNEventHandler oldEventHandler = dir.getWCAccess().getEventHandler();
                dir.getWCAccess().setEventHandler(null);                
                SVNWCManager.add(mergedFile, dir, copyFromURL, revision);
                dir.getWCAccess().setEventHandler(oldEventHandler);
            }
            if (myIsDryRun) {
                myAddedPath = path;
            }
            return SVNStatusType.CHANGED;
        } else if (fileType == SVNFileType.DIRECTORY) {
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry == null || entry.isScheduledForDeletion()) {
                if (!myIsDryRun) {
                    ISVNEventHandler oldEventHandler = dir.getWCAccess().getEventHandler();
                    dir.getWCAccess().setEventHandler(null);                
                    SVNWCManager.add(mergedFile, dir, copyFromURL, revision);
                    dir.getWCAccess().setEventHandler(oldEventHandler);
                }
                if (myIsDryRun) {
                    myAddedPath = path;
                }
                return SVNStatusType.CHANGED;
            } else if (myIsDryRun && isPathDeleted(path)) {
                return SVNStatusType.CHANGED;
                
            }
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            if (myIsDryRun) {
                myAddedPath = null;
            }
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry != null && myIsDryRun && isPathDeleted(path)) {
                return SVNStatusType.CHANGED;
            }
            return SVNStatusType.OBSTRUCTED;
        }
        if (myIsDryRun) {
            myAddedPath = null;
        }
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType directoryDeleted(final String path) throws SVNException {
        final File mergedFile = getFile(path);
        final SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.DIRECTORY) {
            final ISVNEventHandler oldEventHandler = getWCAccess().getEventHandler();            
            ISVNEventHandler handler = new ISVNEventHandler() {
                public void checkCancelled() throws SVNCancelException {
                    oldEventHandler.checkCancelled();
                }
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    if (event.getFile().equals(mergedFile)) {
                        return;
                    }
                    if (event.getAction() == SVNEventAction.DELETE) {
                        event = SVNEventFactory.createMergeEvent(getAdminInfo(), event.getFile(), 
                                SVNEventAction.UPDATE_DELETE, SVNEventAction.UPDATE_DELETE, 
                                SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN, event.getNodeKind());
                    }
                    oldEventHandler.handleEvent(event, progress);
                }
            };
            getWCAccess().setEventHandler(handler);
            try {
                delete(mergedFile, myIsForce, myIsDryRun);
            } catch (SVNException e) {
                return SVNStatusType.OBSTRUCTED;
            } finally {
                getWCAccess().setEventHandler(oldEventHandler);
            }
            return SVNStatusType.CHANGED;
        } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.NONE) {
            return SVNStatusType.MISSING;
        }
        
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, Map originalProperties, Map diff) throws SVNException {
        boolean needsMerge = true;
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), myIsDryRun);
        if (dir == null) {
            return new SVNStatusType[] {SVNStatusType.MISSING, SVNStatusType.MISSING};
        }
        SVNStatusType[] result = new SVNStatusType[] {SVNStatusType.UNCHANGED, SVNStatusType.UNCHANGED};
        SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
        SVNFileType fileType = null;
        if (entry != null) {
            fileType = SVNFileType.getType(mergedFile);
        }
        if (entry == null || fileType != SVNFileType.FILE) {
            return new SVNStatusType[] {SVNStatusType.MISSING, SVNStatusType.MISSING};
        }
        if (diff != null && !diff.isEmpty()) {
            result[1] = propertiesChanged(path, originalProperties, diff);
        } 
        String name = mergedFile.getName();
        if (file1 != null) {
            boolean textModified = dir.hasTextModifications(name, false);
            if (!textModified && 
                    (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2))) {
                boolean same = SVNFileUtil.compareFiles(!myIsAddNecessitatedMerge ? file1 : file2, mergedFile, null);
                if (same) {
                    if (!myIsDryRun && !myIsAddNecessitatedMerge) {
                        SVNFileUtil.rename(file2, mergedFile);
                    }
                    result[0] = SVNStatusType.CHANGED;
                    needsMerge = false;
                }
            }
            if (needsMerge) {
                String localLabel = ".working";
                String baseLabel = ".merge-left.r" + revision1;
                String latestLabel = ".merge-right.r" + revision2;
                SVNStatusType mergeResult = dir.mergeText(name, file1, file2, localLabel, baseLabel, latestLabel, false, myIsDryRun, myDiffOptions);
                if (mergeResult == SVNStatusType.CONFLICTED || mergeResult == SVNStatusType.CONFLICTED_UNRESOLVED) {
                    result[0] = mergeResult;
                } else if (textModified) {
                    result[0] = SVNStatusType.MERGED;
                } else if (mergeResult == SVNStatusType.MERGED) {
                    result[0] = SVNStatusType.CHANGED;
                } else {
                    result[0] = SVNStatusType.UNCHANGED;
                }
            }
        } 
        return result;
    }
    public SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, Map originalProperties, Map diff) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] {null, SVNStatusType.UNKNOWN};
        
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            if (myIsDryRun && myAddedPath != null && SVNPathUtil.isAncestor(myAddedPath, path)) {
                result[0] = SVNStatusType.CHANGED;
                result[1] = SVNStatusType.CHANGED;
            } else {
                result[0] = SVNStatusType.MISSING;
            }
            return result;
        }
        
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.NONE) {
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry != null && !entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
                return result;
            }
            if (!myIsDryRun) {
                SVNURL copyFromURL = myURL.appendPath(path, false);
                // TODO compare protocols with dir one.
                SVNWCManager.addRepositoryFile(dir, mergedFile.getName(), null, file2, null, diff, copyFromURL.toString(), revision2);
            }
            result[0] = SVNStatusType.CHANGED;
            if (diff != null && !diff.isEmpty()) {
                result[1] = SVNStatusType.CHANGED;
            }
        } else if (fileType == SVNFileType.DIRECTORY || fileType == SVNFileType.SYMLINK) {
            if (myIsDryRun && isPathDeleted(path)){
                result[0] = SVNStatusType.CHANGED;
            } else { 
                result[0] = SVNStatusType.OBSTRUCTED;
            }
        } else if (fileType == SVNFileType.FILE) {
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry == null || entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
            } else if (myIsDryRun && isPathDeleted(path)){
                result[0] = SVNStatusType.CHANGED;
            } else {
                myIsAddNecessitatedMerge = true;
                result = fileChanged(path, file1, file2, revision1, revision2, mimeType1, mimeType2, originalProperties, diff);
                myIsAddNecessitatedMerge = false;
            }
        }
        return result;
    }

    public SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, Map originalProperties) throws SVNException {
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            ISVNEventHandler oldEventHandler = getWCAccess().getEventHandler();
            getWCAccess().setEventHandler(null);
            try {
                delete(mergedFile, myIsForce, myIsDryRun);
            } catch (SVNException e) {
                return SVNStatusType.OBSTRUCTED;
            } finally {
                getWCAccess().setEventHandler(oldEventHandler);
            }
            return SVNStatusType.CHANGED;
        } else if (fileType == SVNFileType.DIRECTORY) {
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.NONE) {
            return SVNStatusType.MISSING;
        }
        return SVNStatusType.UNKNOWN;
    }
    
    protected File getFile(String path) {
        return getAdminInfo().getTarget().getFile(path);
    }
    
    protected SVNAdminArea retrieve(File path, boolean lenient) throws SVNException {
        if (getAdminInfo() == null) {
            return null;
        }
        try {
            return getAdminInfo().getWCAccess().retrieve(path);
        } catch (SVNException e) {
            if (lenient) {
                return null;
            }
            throw e;
        }
    }
    
    protected void delete(File path, boolean force, boolean dryRun) throws SVNException {
        if (!force) {
            SVNWCManager.canDelete(path, false, getWCAccess().getOptions());
        }
        SVNAdminArea root = getWCAccess().retrieve(path.getParentFile()); 
        if (!dryRun) {
            SVNWCManager.delete(getWCAccess(), root, path, true);
        }
    }

}
