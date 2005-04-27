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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNFileEntry;
import org.tmatesoft.svn.core.ISVNRootEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNCheckoutEditor implements ISVNEditor {
    
    private Stack myStack;
    
    private long myTargetRevision;    
    private ISVNEntry myRootEntry;
	private ISVNFileEntry myCurrentFile;
    private List myDiffWindow;
    private ISVNWorkspaceMediator myMediator;

    private boolean myIsExport;
    private Map myChangedProperties;

    private Map myPropertiesMap;
    
    private int myContentsStatus;
    private int myPropertiesStatus;
    private boolean myIsTimestampsChanged;

    private SVNWorkspace myWorkspace;
    private long myUpdateTime;
    private int myFileCount;
    private SVNCheckoutProgressProcessor myProgressProcessor;

    private Collection myExternals;
    private String myTarget;
    private boolean myIsRecursive;
    private int myObstructedCount;
    
    public SVNCheckoutEditor(ISVNWorkspaceMediator mediator, SVNWorkspace workspace, ISVNEntry rootEntry, boolean export,
            String target) {
        this(mediator, workspace, rootEntry, export, target, true, null);
    }

    public SVNCheckoutEditor(ISVNWorkspaceMediator mediator, SVNWorkspace workspace, ISVNEntry rootEntry, boolean export,
            String target, boolean recursive, SVNCheckoutProgressProcessor progressProcessor) {
        myRootEntry = rootEntry;
	      myStack = new Stack();
        myMediator = mediator;
        myWorkspace = workspace;
        myIsExport = export;
        myTargetRevision = -1;
        myPropertiesMap = new HashMap();
        myTarget = target;
        myIsRecursive = recursive;
	      myProgressProcessor = progressProcessor;
    }
    
    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }
    
    public void openRoot(long revision) throws SVNException {
        // do not change when target is used???
        if (myTarget == null) {
            myRootEntry.setPropertyValue(SVNProperty.COMMITTED_REVISION, SVNProperty.toString(revision));
        }
        myStack.push(myRootEntry);
    }
    public void deleteEntry(String path, long revision) throws SVNException {
        DebugLog.log("DELETE ENTRY: " + path + " : " + revision);
        myWorkspace.fireEntryUpdated(getCurrentEntry().getChild(PathUtil.tail(path)), SVNStatus.DELETED, SVNStatus.DELETED, revision);
        getCurrentEntry().deleteChild(PathUtil.tail(path), true);
    }
    
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }
    
    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        if (myObstructedCount > 0) {
            myObstructedCount++;
            return;
        }
        DebugLog.log("UPDATED: ADD DIR: " + path);
        ISVNEntry existingChild = getCurrentEntry().getUnmanagedChild(PathUtil.tail(path));
        if (existingChild != null && !existingChild.isDirectory()) {
            myWorkspace.fireEntryUpdated(existingChild, SVNStatus.OBSTRUCTED, 0, myTargetRevision);
            myObstructedCount = 1;
            return;
        }
        ISVNEntry newDir = getCurrentEntry().addDirectory(PathUtil.tail(path), -1);
        myStack.push(newDir);
        newDir.setPropertyValue(SVNProperty.REVISION, "0");
        getCurrentProperties().put("added", "true");
        myWorkspace.fireEntryUpdated(newDir, SVNStatus.ADDED, 0, myTargetRevision);        
        myIsTimestampsChanged = !myIsExport;
    }
    
    public void openDir(String path, long revision) throws SVNException {
        String name = PathUtil.tail(path);
        ISVNEntry dir = getCurrentEntry().getChild(name);
        if (dir != null) {
            myStack.push(dir);
        }
    }
    public void changeDirProperty(String name, String value) throws SVNException {
        if (myObstructedCount > 0) {
            return;
        }
        if (!myIsExport) {
            getCurrentProperties().put(name, value);
            if (SVNProperty.EXTERNALS.equals(name) && value != null) {
                myExternals = SVNExternal.create(getCurrentEntry(), value, myExternals);
            }
            myIsTimestampsChanged = true;
        }
        DebugLog.log("UPDATED: DIR PROPERTY CHANGED");
    }

    public void closeDir() throws SVNException {
        myObstructedCount--;
        if (myObstructedCount > 0) {
            return;
        }
        int propsStatus = 0;
        boolean wasAdded = getCurrentProperties().remove("added") != null;
        if (!myIsExport) {
            // do not update props and revision when target is not null 
            // and we are closing root entry.
            if (!(myTarget != null && getCurrentEntry() == myRootEntry)) {
                getCurrentProperties().put(SVNProperty.REVISION, Long.toString(myTargetRevision));
                propsStatus = getCurrentEntry().applyChangedProperties(getCurrentProperties());
            }
            myPropertiesMap.remove(getCurrentEntry());            
        }
        if (propsStatus != 0) {
            long revision = SVNProperty.longValue(getCurrentEntry().getPropertyValue(SVNProperty.COMMITTED_REVISION));
            if (!wasAdded) {
                myWorkspace.fireEntryUpdated(getCurrentEntry(), 0, propsStatus, revision);
            }
        }
        if (myIsExport) {
            ((ISVNRootEntry) myRootEntry).deleteAdminFiles(getCurrentEntry().getPath());
        }
        DebugLog.log("UPDATED: CLOSED DIR: " + getCurrentEntry().getPath());
	    if (myProgressProcessor != null) {
		    myProgressProcessor.entryProcessed(getCurrentEntry().getPath());
	    }
        myStack.pop();
    }
    
    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        if (myObstructedCount > 0) {
            return;
        }
        DebugLog.log("UPDATED: ADD FILE: " + path);
        myCurrentFile = getCurrentEntry().addFile(PathUtil.tail(path), -1);
        if (!myIsExport) {
            myChangedProperties = new HashMap();
        }
        myCurrentFile.setPropertyValue(SVNProperty.REVISION, "0");
        myContentsStatus = SVNStatus.ADDED;
        myIsTimestampsChanged = !myIsExport;
        DebugLog.log("UPDATED: FILE ADDED");
    }
    
    public void openFile(String path, long revision) throws SVNException {
        String name = PathUtil.tail(path);
        myCurrentFile = (ISVNFileEntry) getCurrentEntry().getChild(name);
        if (!myIsExport) {
            myChangedProperties = new HashMap();
        }
        myIsTimestampsChanged = !myIsExport;
    }
    
    public void closeFile(String textChecksum) throws SVNException {
        if (myContentsStatus == SVNStatus.CORRUPTED) {
            DebugLog.log("UPDATED: CLOSE FILE: skipping corrupted file: " + myCurrentFile.getPath());
            myCurrentFile = null;
            myPropertiesStatus = 0;
            myContentsStatus = 0;
            return;
        }
        if (!myIsExport) {
            myChangedProperties.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            if (myChangedProperties.get(SVNProperty.URL) == null) {
                ISVNDirectoryEntry parent = (ISVNDirectoryEntry) myStack.peek();
                myChangedProperties.put(SVNProperty.URL, PathUtil.append(parent.getPropertyValue(SVNProperty.URL), PathUtil.encode(myCurrentFile.getName())));
            }
            myChangedProperties.put(SVNProperty.CHECKSUM, textChecksum);
            myPropertiesStatus = myCurrentFile.applyChangedProperties(myChangedProperties);
            myChangedProperties = null;
        }
        long revision = SVNProperty.longValue(myCurrentFile.getPropertyValue(SVNProperty.COMMITTED_REVISION));
        myWorkspace.fireEntryUpdated(myCurrentFile, myContentsStatus, myPropertiesStatus, revision);
        DebugLog.log("UPDATED: CLOSED FILE: " + myCurrentFile.getPath());
	    if (myProgressProcessor != null) {
		    myProgressProcessor.entryProcessed(myCurrentFile.getPath());
	    }
        myContentsStatus = 0;
        myPropertiesStatus = 0;
        myCurrentFile = null;
    }
    
    public void applyTextDelta(String baseChecksum)  throws SVNException {
        if (myContentsStatus == 0) {
            if (myCurrentFile.isCorrupted()) {
                myCurrentFile.setPropertyValue(SVNProperty.CORRUPTED, Boolean.TRUE.toString());
                myWorkspace.fireEntryUpdated(myCurrentFile, SVNStatus.CORRUPTED, myPropertiesStatus, myTargetRevision);
                myContentsStatus = SVNStatus.CORRUPTED;
            }
        }
    }
    
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        if (myContentsStatus == SVNStatus.CORRUPTED) {
            DebugLog.log("UPDATED: TEXTDELTACHUNK: skipping corrupted file: " + myCurrentFile.getPath());
            return null;
        }
        DebugLog.log("UPDATED: TEXTDELTACHUNK: " + myCurrentFile.getPath());
        if (myDiffWindow == null) {
            myDiffWindow = new LinkedList();
        }
        myDiffWindow.add(diffWindow);
        try {
            return myMediator.createTemporaryLocation(myCurrentFile.getPath(), diffWindow);
        } catch (Throwable e) {
            DebugLog.error(e);
            throw new SVNException(e);
        } finally {
            DebugLog.log("UPDATED: TEXTDELTACHUNK: SAVED");
        }
    }    
    
    public void textDeltaEnd() throws SVNException {
        if (myContentsStatus == SVNStatus.CORRUPTED) {
            DebugLog.log("UPDATED: TEXTDELTAEND: skipping corrupted file: " + myCurrentFile.getPath());
            return;
        }
        DebugLog.log("UPDATED: APPLING DELTA: " + myCurrentFile.getPath());
        int status = 0;
        long t = System.currentTimeMillis();
        if (myDiffWindow == null) {
            // create empty file?
            DebugLog.log("UPDATED: NO DIFF WINDOW TO APPLY");
            myCurrentFile.applyDelta(null, null, myIsExport);
        } else {
            DebugLog.log("UPDATED: APPLYING DIFF WINDOWS: " + myDiffWindow.size());
            for(int i = 0; i < myDiffWindow.size(); i++) {
                SVNDiffWindow window = (SVNDiffWindow) myDiffWindow.get(i);
                InputStream newData = null;
                try {
                    newData = myMediator.getTemporaryLocation(window);
                    myCurrentFile.applyDelta(window, newData, myIsExport);
                } catch (IOException e) {
                    DebugLog.error(e);
                    throw new SVNException(e);
                } catch (SVNException e) {
                    DebugLog.error(e);
                    throw e;
                } catch (Throwable th) {
                    DebugLog.error(th);
                    throw new SVNException(th);
                } finally {        
                    if (newData != null) {
                        try {
                            newData.close();
                        } catch (IOException e1) {
                        }
                    }            
                }
                myMediator.deleteTemporaryLocation(window);
            }
            myDiffWindow = null;
        }
        status = myCurrentFile.deltaApplied(myIsExport);
        if (myContentsStatus == SVNStatus.NOT_MODIFIED) {
            myContentsStatus = status;
        }
        myUpdateTime += System.currentTimeMillis() - t;
        myFileCount++;
        DebugLog.log("UPDATED: DELTA APPLIED: " + myCurrentFile.getPath());
    }
    
    public void changeFileProperty(String name, String value) throws SVNException {
        if (!myIsExport) {
            myChangedProperties.put(name, value);
        } else {
            myCurrentFile.setPropertyValue(name, value);
        }
        myIsTimestampsChanged = !myIsExport;
        DebugLog.log("UPDATED: FILE PROPERTY CHANGED");
    }

    public SVNCommitInfo closeEdit() throws SVNException {        
        if (myFileCount > 0) {
            DebugLog.benchmark("UPDATED IN " + (myUpdateTime)/myFileCount + " ms. (" + myFileCount + ")");
        }
        long start = System.currentTimeMillis();
        if (!myIsExport) {
            if (myTarget == null) {
                myRootEntry.merge(myIsRecursive);
            } else {
                DebugLog.log("UPDATED: MERGING TARGET: " + myTarget);
                ISVNEntry targetEntry = myRootEntry.asDirectory().getChild(myTarget); 
                if (targetEntry != null) {
                    if (targetEntry.getPropertyValue(SVNProperty.REVISION) == null) {
                        // only if it was missing before and missing now.
                        myRootEntry.asDirectory().deleteChild(targetEntry.getName(), true);
                        DebugLog.log("UPDATED: TARGET DELETED");
                    } else {
                        targetEntry.merge(myIsRecursive);
                        DebugLog.log("UPDATED: TARGET MERGED");
                    }
                }
                myRootEntry.save(false);
                DebugLog.log("UPDATED: PARENT SAVED");
            }
        }
        myRootEntry.dispose();
        myStack.clear();
        DebugLog.benchmark("MERGED IN " + (System.currentTimeMillis() - start) + " ms.");
        DebugLog.log("CLOSE EDIT");
        return new SVNCommitInfo(myTargetRevision, null, null);
    }
    
    public void abortEdit() throws SVNException {
        DebugLog.error("ABORT EDIT");
        myRootEntry.dispose();
        myStack.clear();
    }
    
    public boolean isTimestampsChanged() {
        return myIsTimestampsChanged;
    }
    
    public Collection getExternals() {
        return myExternals == null ? Collections.EMPTY_SET : myExternals;
    }
    
    private ISVNDirectoryEntry getCurrentEntry() {
        return (ISVNDirectoryEntry) myStack.peek();
    }
    
    private Map getCurrentProperties() {
        if (!myPropertiesMap.containsKey(getCurrentEntry())) {
            myPropertiesMap.put(getCurrentEntry(), new HashMap());
        }
        return (Map) myPropertiesMap.get(getCurrentEntry());
    }
}

    