/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.replicator;

import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNReplicationEditor implements ISVNEditor {

    private static final int ACCEPT = 0;
    private static final int IGNORE = 1;
    private static final int DECIDE = 2;

    private ISVNEditor myCommitEditor;

    private Map myCopiedPaths;

    private Map myChangedPaths;

    private SVNRepository myRepos;

    private Map pathsToFileBatons;

    private Stack myDirsStack;
    
    private long myTargetRevision;
    
    public SVNReplicationEditor(SVNRepository repository, ISVNEditor commitEditor) {
        myRepos = repository;
        myCommitEditor = commitEditor;
        pathsToFileBatons = new HashMap();
        myDirsStack = new Stack();
    }

    public void targetRevision(long revision) throws SVNException {
        if (!myRepos.getLocation().equals(myRepos.getRepositoryRoot(false))) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Repository location ''{0}'' should be the repository root ''{1}''", new Object[]{myRepos.getLocation(), myRepos.getRepositoryRoot(false)});
            SVNErrorManager.error(err);
        }
        
        myTargetRevision = revision;
        myCopiedPaths = new HashMap();
        //1. first investigate paths for copies if there're any 
        SVNRepository sourceRepos = SVNRepositoryFactory.create(myRepos.getLocation());
        sourceRepos.setAuthenticationManager(myRepos.getAuthenticationManager());
        Collection logEntries = sourceRepos.log(new String[] {""}, null, myTargetRevision, myTargetRevision, true, false);
        SVNLogEntry logEntry = (SVNLogEntry)logEntries.toArray()[0];
        myChangedPaths = logEntry.getChangedPaths();
        
        if(myChangedPaths == null || myChangedPaths.isEmpty()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.AUTHZ_UNREADABLE, "Have no full read permissions on ''{0}''", myRepos.getLocation());
            SVNErrorManager.error(err);
        }
        
        for(Iterator paths = myChangedPaths.keySet().iterator(); paths.hasNext();){
            String path = (String)paths.next();
            SVNLogEntryPath pathChange = (SVNLogEntryPath)myChangedPaths.get(path);
            //make sure it's a copy
            if(pathChange.getType() == SVNLogEntryPath.TYPE_ADDED && pathChange.getCopyPath() != null && pathChange.getCopyRevision() >= 0){
                myCopiedPaths.put(path, pathChange);
            }
        }
    }

    public void openRoot(long revision) throws SVNException {
        //open root
        myCommitEditor.openRoot(myTargetRevision);
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);
        SVNDebugLog.logInfo("Opening Root");

    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath deletedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        if (deletedPath != null && deletedPath.getType() == SVNLogEntryPath.TYPE_DELETED) {
            myChangedPaths.remove(absPath);
        }else{
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Expected that path ''{0}'' is deleted in revision {1,number,integer}", new Object[]{absPath, new Long(myTargetRevision)});
            SVNErrorManager.error(err);
        }
        myCommitEditor.deleteEntry(path, myTargetRevision);
        SVNDebugLog.logInfo("Deleting entry '" + absPath + "'");

    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        EntryBaton baton = new EntryBaton();
        myDirsStack.push(baton);
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath changedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_ADDED && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0) {
            baton.myPropsAct = DECIDE;
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.setAuthenticationManager(myRepos.getAuthenticationManager());
            rep.getDir(changedPath.getCopyPath(), changedPath.getCopyRevision(), props, (Collection) null);
            baton.myProps = props;
            
            myCommitEditor.addDir(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
            SVNDebugLog.logInfo("Adding dir '" + absPath + "'");
        } else if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED)) {
            baton.myPropsAct = ACCEPT;
            myCommitEditor.addDir(path, null, -1);
            SVNDebugLog.logInfo("Adding dir '" + absPath + "'");
        } else if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_MODIFIED) {
            baton.myPropsAct = ACCEPT;
            myCommitEditor.openDir(path, myTargetRevision);
            SVNDebugLog.logInfo("Opening dir '" + absPath + "'");
        } else if (changedPath == null) {
            baton.myPropsAct = IGNORE;
            myCommitEditor.openDir(path, myTargetRevision);
            SVNDebugLog.logInfo("Opening dir '" + absPath + "'");
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addDir()");
            SVNErrorManager.error(err);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);
        myCommitEditor.openDir(path, myTargetRevision);
        SVNDebugLog.logInfo("Opening dir '" + absPath + "'");
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (!SVNProperty.isRegularProperty(name)) {
            return;
        }
        EntryBaton baton = (EntryBaton) myDirsStack.peek();
        if (baton.myPropsAct == ACCEPT) {
            myCommitEditor.changeDirProperty(name, value);
            SVNDebugLog.logInfo("Changing dir property: " + name + "=" + value);
        } else if (baton.myPropsAct == DECIDE) {
            String propVal = (String)baton.myProps.get(name);
            if (propVal != null && propVal.equals(value)) {
                /*
                 * The properties seem to be the same as of the copy origin,
                 * do not reset them again.
                 */
                baton.myPropsAct = IGNORE;
                SVNDebugLog.logInfo("Ignoring copied dir property: " + name + "=" + value);
                return;
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeDirProperty(name, value);
            SVNDebugLog.logInfo("Changing dir property: " + name + "=" + value);

        }
        // else ignore props of the dir being copied
        SVNDebugLog.logInfo("Ignoring copied dir property: " + name + "=" + value);
    }

    public void closeDir() throws SVNException {
        if (myDirsStack.size() > 1) {
            myCommitEditor.closeDir();
        }
        myDirsStack.pop();
        SVNDebugLog.logInfo("Closing dir");
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        EntryBaton baton = new EntryBaton();
        pathsToFileBatons.put(path, baton);
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath changedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        
        if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_ADDED && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0) {
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            if (areFileContentsEqual(absPath, myTargetRevision, changedPath.getCopyPath(), changedPath.getCopyRevision())) {
                baton.myTextAct = IGNORE;
            }
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.setAuthenticationManager(myRepos.getAuthenticationManager());
            rep.getDir(changedPath.getCopyPath(), changedPath.getCopyRevision(), props, (Collection) null);
            baton.myProps = props;
            myCommitEditor.addFile(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
            SVNDebugLog.logInfo("Adding file '" + absPath + "'");
        } else if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED)) {
            baton.myPropsAct = ACCEPT;
            baton.myTextAct = ACCEPT;
            myCommitEditor.addFile(path, null, -1);
            SVNDebugLog.logInfo("Adding file '" + absPath + "'");
        } else if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_MODIFIED) {
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            SVNLogEntryPath realPath = getFileCopyOrigin(absPath);
            if (realPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown error, can't get the copy origin of a file");
                SVNErrorManager.error(err);
            }
            if (areFileContentsEqual(absPath, myTargetRevision, realPath.getCopyPath(), realPath.getCopyRevision())) {
                baton.myTextAct = IGNORE;
            }
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.setAuthenticationManager(myRepos.getAuthenticationManager());
            rep.getDir(realPath.getCopyPath(), realPath.getCopyRevision(), props, (Collection) null);
            baton.myProps = props;
            myCommitEditor.openFile(path, myTargetRevision);
            SVNDebugLog.logInfo("Opening file '" + absPath + "'");
        } else if (changedPath == null) {
            baton.myPropsAct = IGNORE;
            baton.myTextAct = IGNORE;
            myCommitEditor.openFile(path, myTargetRevision);
            SVNDebugLog.logInfo("Opening file '" + absPath + "'");
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addFile()");
            SVNErrorManager.error(err);
        }
    }

    private SVNLogEntryPath getFileCopyOrigin(String path) throws SVNException {
        Object[] paths = myCopiedPaths.keySet().toArray();
        SVNLogEntryPath realPath = null;
        for (int i = 0; i < paths.length; i++) {
            String copiedPath = (String) paths[i];
            
            if (!path.startsWith(copiedPath)) {
                continue;
            }
            
            String relativePath = path.substring(copiedPath.endsWith("/") ? copiedPath.length() : copiedPath.length() + 1);
            SVNLogEntryPath changedPath = (SVNLogEntryPath) myCopiedPaths.get(copiedPath);
            String realFilePath = SVNPathUtil.concatToAbs(changedPath.getCopyPath(), relativePath);
            SVNRepository repos = SVNRepositoryFactory.create(myRepos.getLocation());
            repos.setAuthenticationManager(myRepos.getAuthenticationManager());
            if (repos.checkPath(realFilePath, changedPath.getCopyRevision()) == SVNNodeKind.FILE) {
                realPath = new SVNLogEntryPath(path, ' ', realFilePath, changedPath.getCopyRevision());
                break;
            }
        }
        return realPath;
    }

    private boolean areFileContentsEqual(String path1, long rev1, String path2, long rev2) throws SVNException {
        Map props1 = new HashMap();
        Map props2 = new HashMap();

        SVNRepository repos = SVNRepositoryFactory.create(myRepos.getLocation());
        repos.setAuthenticationManager(myRepos.getAuthenticationManager());
        repos.getFile(path1, rev1, props1, null);
        repos.getFile(path2, rev2, props2, null);
        String crc1 = (String) props1.get(SVNProperty.CHECKSUM);
        String crc2 = (String) props1.get(SVNProperty.CHECKSUM);
        return crc1 != null && crc1.equals(crc2);
    }

    public void openFile(String path, long revision) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        baton.myTextAct = ACCEPT;
        pathsToFileBatons.put(path, baton);
        myCommitEditor.openFile(path, myTargetRevision);
        SVNDebugLog.logInfo("Opening file '" + absPath + "'");

    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        EntryBaton baton = (EntryBaton) pathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            SVNDebugLog.logInfo("Accepting delta for file '" + absPath + "'");
            myCommitEditor.applyTextDelta(path, baseChecksum);
        }
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        EntryBaton baton = (EntryBaton) pathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            SVNDebugLog.logInfo("Text chunk for file '" + absPath + "'");
            return myCommitEditor.textDeltaChunk(path, diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        EntryBaton baton = (EntryBaton) pathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            SVNDebugLog.logInfo("End of text for file '" + absPath + "'");
            myCommitEditor.textDeltaEnd(path);
        }
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (!SVNProperty.isRegularProperty(name)) {
            return;
        }
        EntryBaton baton = (EntryBaton) pathsToFileBatons.get(path);
        if (baton.myPropsAct == ACCEPT) {
            myCommitEditor.changeFileProperty(path, name, value);
            SVNDebugLog.logInfo("Changing file property: " + name + "=" + value);
        } else if (baton.myPropsAct == DECIDE) {
            String propVal = (String)baton.myProps.get(name);
            if (propVal != null && propVal.equals(value)) {
                /*
                 * The properties seem to be the same as of the copy origin,
                 * do not reset them again.
                 */
                baton.myPropsAct = IGNORE;
                SVNDebugLog.logInfo("Ignoring file property: " + name + "=" + value);
                return;
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeFileProperty(path, name, value);
            SVNDebugLog.logInfo("Changing file property: " + name + "=" + value);
        }
        // ignore props of the file being copied
        SVNDebugLog.logInfo("Ignoring file property: " + name + "=" + value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        String absPath = myRepos.getRepositoryPath(path);
        myCommitEditor.closeFile(path, textChecksum);
        SVNDebugLog.logInfo("Closing file '" + absPath + "'");
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        /* for now we must have all explicitly deleted
         * entries removed from changedPaths except those that
         * were deleted from a locally copied dir (they're just not 
         * sent by the server, we need to delete them for ourselves)
         */
        
        for(Iterator paths = myChangedPaths.keySet().iterator(); paths.hasNext();){
            String path = (String)paths.next();
            SVNLogEntryPath pathChange = (SVNLogEntryPath)myChangedPaths.get(path);
            //make sure it's a copy
            if(pathChange.getType() == SVNLogEntryPath.TYPE_DELETED){
                String[] entries = path.split("/");
                String currentOpened = "";
                int j = 0;
                for(j = 0; j < entries.length - 1; j++){
                    currentOpened += entries[j];
                    SVNDebugLog.logInfo("Opening dir '" + "/" + currentOpened);
                    myCommitEditor.openDir(entries[j], myTargetRevision);
                    currentOpened += "/";
                }
                String pathToDelete = currentOpened + entries[j];
                SVNDebugLog.logInfo("Deleting entry '" + "/" + pathToDelete);
                myCommitEditor.deleteEntry(pathToDelete, myTargetRevision);
                for(j = 0; j < entries.length - 1; j++){
                    SVNDebugLog.logInfo("Closing dir");
                    myCommitEditor.closeDir();
                }
            }
        }
        
        SVNDebugLog.logInfo("Closing Root");
        //close root & finish commit
        myCommitEditor.closeDir();
        SVNDebugLog.logInfo("Closing Edit");
        System.out.println("revision processed: " + myTargetRevision);
        return myCommitEditor.closeEdit();
        
    }

    public void abortEdit() throws SVNException {
        SVNDebugLog.logInfo("Aborting Edit");
        myCommitEditor.abortEdit();
    }

    private class EntryBaton {

        int myPropsAct;
        int myTextAct;
        HashMap myProps;
    }
}
