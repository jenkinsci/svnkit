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
    
    private Collection myExternals;

    private String myTarget;
    
    public SVNCheckoutEditor(ISVNWorkspaceMediator mediator, SVNWorkspace workspace, ISVNEntry rootEntry, boolean export,
            String target) {
        myRootEntry = rootEntry;
        myStack = new Stack();
        myMediator = mediator;
        myWorkspace = workspace;
        myIsExport = export;
        myTargetRevision = -1;
        myPropertiesMap = new HashMap();
        myTarget = target;
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
        getCurrentEntry().deleteChild(PathUtil.tail(path), false);
    }
    
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }
    
    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        DebugLog.log("UPDATED: ADD DIR: " + path);
        ISVNEntry newDir = getCurrentEntry().addDirectory(PathUtil.tail(path), -1);
        myStack.push(newDir);
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
        if (!myIsExport) {
            getCurrentProperties().put(name, value);
            if (SVNProperty.EXTERNALS.equals(name) && value != null) {
                myExternals = SVNExternal.create(getCurrentEntry(), value, myExternals);
            }
            myIsTimestampsChanged = true;
        }
    }

    public void closeDir() throws SVNException {
        int propsStatus = 0;
        if (getCurrentProperties().remove("added") == null) {
            if (!getCurrentProperties().isEmpty()) {
                //  props are modified.
                propsStatus = SVNStatus.UPDATED;
            }
        }
        if (!myIsExport) {
            if (myTarget == null) {
                getCurrentProperties().put(SVNProperty.REVISION, Long.toString(myTargetRevision));
                getCurrentEntry().applyChangedProperties(getCurrentProperties());
            }
            myPropertiesMap.remove(getCurrentEntry());            
        }
        if (propsStatus != 0) {
            long revision = SVNProperty.longValue(getCurrentEntry().getPropertyValue(SVNProperty.COMMITTED_REVISION));
            myWorkspace.fireEntryUpdated(getCurrentEntry(), 0, propsStatus, revision);
        }
        DebugLog.log("UPDATED: CLOSED DIR: " + getCurrentEntry().getPath());
        myStack.pop();
    }
    
    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        DebugLog.log("UPDATED: ADD FILE: " + path);
        myCurrentFile = getCurrentEntry().addFile(PathUtil.tail(path), -1);
        if (!myIsExport) {
            myChangedProperties = new HashMap();
        }
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
        if (!myIsExport) {
            myChangedProperties.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            myChangedProperties.put(SVNProperty.CHECKSUM, textChecksum);
            myPropertiesStatus = myCurrentFile.applyChangedProperties(myChangedProperties);
            myChangedProperties = null;
        }
        long revision = SVNProperty.longValue(myCurrentFile.getPropertyValue(SVNProperty.COMMITTED_REVISION));
        myWorkspace.fireEntryUpdated(myCurrentFile, myContentsStatus, myPropertiesStatus, revision);
        DebugLog.log("UPDATED: CLOSED FILE: " + myCurrentFile.getPath());
        myContentsStatus = 0;
        myPropertiesStatus = 0;
        myCurrentFile = null;
    }
    
    public void applyTextDelta(String baseChecksum)  throws SVNException {
        // do nothing.
    }
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        DebugLog.log("UPDATED: TEXTDELTACHUNK: " + myCurrentFile.getPath());
        if (myDiffWindow == null) {
            myDiffWindow = new LinkedList();
        }
        myDiffWindow.add(diffWindow);
        try {
            return myMediator.createTemporaryLocation(diffWindow);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        } finally {
                DebugLog.log("UPDATED: TEXTDELTACHUNK: SAVED");
        }
    }    
    
    public void textDeltaEnd() throws SVNException {
        DebugLog.log("UPDATED: APPLING DELTA: " + myCurrentFile.getPath());
        int status = 0;
        long t = System.currentTimeMillis();
        if (myDiffWindow == null) {
            // create empty file?
            myCurrentFile.applyDelta(null, null, myIsExport);
        } else {
            for(int i = 0; i < myDiffWindow.size(); i++) {
                SVNDiffWindow window = (SVNDiffWindow) myDiffWindow.get(i);
                InputStream newData = null;
                try {
                    newData = myMediator.getTemporaryLocation(window);
                    myCurrentFile.applyDelta(window, newData, myIsExport);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new SVNException(e);
                } catch (SVNException e) {
                    e.printStackTrace();
                    throw e;
                } catch (Throwable th) {
                    th.printStackTrace();
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
                myRootEntry.merge();
            } else {
                if (myRootEntry.asDirectory().getChild(myTarget) != null) {
                    myRootEntry.asDirectory().getChild(myTarget).merge();
                }
                myRootEntry.save(false);
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

    