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
package org.tmatesoft.svn.core.replicator;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
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

/**
 * The <b>SVNReplicationEditor</b> is an editor implementation used by a 
 * repository replicator as a bridge between an update editor for the source 
 * repository and a commit editor of the target one. This editor is provided 
 * to an update method of a source <b>SVNRepository</b> driver to properly translate 
 * the calls of that driver to calls to a commit editor of the destination <b>SVNRepository</b> 
 * driver.   
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     org.tmatesoft.svn.core.io.SVNRepository
 * @since   1.1
 */
public class SVNReplicationEditor implements ISVNEditor {

    private static final int ACCEPT = 0;
    private static final int IGNORE = 1;
    private static final int DECIDE = 2;

    private ISVNEditor myCommitEditor;
    private Map myCopiedPaths;
    private Map myChangedPaths;
    private SVNRepository myRepos;
    private Map myPathsToFileBatons;
    private Stack myDirsStack;
    private long myPreviousRevision;
    private long myTargetRevision;
    private SVNCommitInfo myCommitInfo;
    private SVNRepository mySourceRepository;
    
    /**
     * Creates a new replication editor.
     * 
     * <p>
     * <code>repository</code> must be created for the root location of 
     * the source repository which is to be replicated. 
     * 
     * @param repository    a source repository       
     * @param commitEditor  a commit editor received from the destination
     *                      repository driver (which also must be point to the 
     *                      root location of the destination repository)
     * @param revision      log information of the revision to be copied
     */
    public SVNReplicationEditor(SVNRepository repository, ISVNEditor commitEditor, SVNLogEntry revision) {
        myRepos = repository;
        myCommitEditor = commitEditor;
        myPathsToFileBatons = new HashMap();
        myDirsStack = new Stack();
        myCopiedPaths = new HashMap();
        myChangedPaths = revision.getChangedPaths();

        for(Iterator paths = myChangedPaths.keySet().iterator(); paths.hasNext();){
            String path = (String)paths.next();
            SVNLogEntryPath pathChange = (SVNLogEntryPath)myChangedPaths.get(path);
            //make sure it's a copy
            if((pathChange.getType() == SVNLogEntryPath.TYPE_REPLACED || pathChange.getType() == SVNLogEntryPath.TYPE_ADDED) && pathChange.getCopyPath() != null && pathChange.getCopyRevision() >= 0){
                myCopiedPaths.put(path, pathChange);
            }
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myPreviousRevision = revision - 1;
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        //open root
        myCommitEditor.openRoot(myPreviousRevision);
        EntryBaton baton = new EntryBaton("/");
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);

    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String absPath = getSourceRepository().getRepositoryPath(path);
        SVNLogEntryPath deletedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        if (deletedPath != null && 
                (deletedPath.getType() == SVNLogEntryPath.TYPE_DELETED || deletedPath.getType() == SVNLogEntryPath.TYPE_REPLACED)) {
            if (deletedPath.getType() == SVNLogEntryPath.TYPE_DELETED) {
                myChangedPaths.remove(absPath);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Expected that path ''{0}'' is deleted in revision {1,number,integer}", new Object[]{absPath, new Long(myPreviousRevision)});
            SVNErrorManager.error(err);
        }
        myCommitEditor.deleteEntry(path, myPreviousRevision);
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        String absPath = getSourceRepository().getRepositoryPath(path);
        EntryBaton baton = new EntryBaton(absPath);
        myDirsStack.push(baton);
        SVNLogEntryPath changedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED) && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0) {
            baton.myPropsAct = DECIDE;
            HashMap props = new HashMap();
            getSourceRepository().getDir(changedPath.getCopyPath(), changedPath.getCopyRevision(), props, (ISVNDirEntryHandler) null);
            baton.myProps = props;
            
            if (changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED) {
                myCommitEditor.deleteEntry(path, myPreviousRevision);
                myChangedPaths.remove(absPath);
            }
            myCommitEditor.addDir(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
        } else if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED)) {
            baton.myPropsAct = ACCEPT;
            myCommitEditor.addDir(path, null, -1);
        } else if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_MODIFIED) {
            baton.myPropsAct = ACCEPT;
            myCommitEditor.openDir(path, myPreviousRevision);
        } else if (changedPath == null) {
            baton.myPropsAct = IGNORE;
            myCommitEditor.openDir(path, myPreviousRevision);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addDir()");
            SVNErrorManager.error(err);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        EntryBaton baton = new EntryBaton(getSourceRepository().getRepositoryPath(path));
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);
        myCommitEditor.openDir(path, myPreviousRevision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (!SVNProperty.isRegularProperty(name)) {
            return;
        }
        EntryBaton baton = (EntryBaton) myDirsStack.peek();
        if (baton.myPropsAct == ACCEPT) {
            myCommitEditor.changeDirProperty(name, value);
        } else if (baton.myPropsAct == DECIDE) {
            String propVal = (String)baton.myProps.get(name);
            if (propVal != null && propVal.equals(value)) {
                /*
                 * The properties seem to be the same as of the copy origin,
                 * do not reset them again.
                 */
                baton.myPropsAct = IGNORE;
                return;
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeDirProperty(name, value);

        }
    }

    public void closeDir() throws SVNException {
        if (myDirsStack.size() > 1 && !myCopiedPaths.isEmpty()) {
            EntryBaton currentDir = (EntryBaton) myDirsStack.peek();
            completeDeletion(currentDir.myPath);
        }
        myDirsStack.pop();
        myCommitEditor.closeDir();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        String absPath = getSourceRepository().getRepositoryPath(path);
        EntryBaton baton = new EntryBaton(absPath);
        myPathsToFileBatons.put(path, baton);
        SVNLogEntryPath changedPath = (SVNLogEntryPath) myChangedPaths.get(absPath);
        
        if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED) && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0) {
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            Map props = new HashMap();
            if (areFileContentsEqual(absPath, myTargetRevision, changedPath.getCopyPath(), changedPath.getCopyRevision(), props)) {
                baton.myTextAct = IGNORE;
            }
            baton.myProps = props;
            if(changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED){
                myCommitEditor.deleteEntry(path, myPreviousRevision);
                myChangedPaths.remove(absPath);
            }
            myCommitEditor.addFile(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
        } else if (changedPath != null && (changedPath.getType() == SVNLogEntryPath.TYPE_ADDED || changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED)) {
            baton.myPropsAct = ACCEPT;
            baton.myTextAct = ACCEPT;
            if(changedPath.getType() == SVNLogEntryPath.TYPE_REPLACED){
                myCommitEditor.deleteEntry(path, myPreviousRevision);
                myChangedPaths.remove(absPath);
            }
            myCommitEditor.addFile(path, null, -1);
        } else if (changedPath != null && changedPath.getType() == SVNLogEntryPath.TYPE_MODIFIED) {
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            SVNLogEntryPath realPath = getFileCopyOrigin(absPath);
            if (realPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown error, can't get the copy origin of a file");
                SVNErrorManager.error(err);
            }
            Map props = new HashMap();
            if (areFileContentsEqual(absPath, myTargetRevision, realPath.getCopyPath(), realPath.getCopyRevision(), props)) {
                baton.myTextAct = IGNORE;
            }
            baton.myProps = props;
            myCommitEditor.openFile(path, myPreviousRevision);
        } else if (changedPath == null) {
            baton.myPropsAct = IGNORE;
            baton.myTextAct = IGNORE;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addFile()");
            SVNErrorManager.error(err);
        }
    }

    private SVNLogEntryPath getFileCopyOrigin(String path) throws SVNException {
        Object[] paths = myCopiedPaths.keySet().toArray();
        Arrays.sort(paths, 0, paths.length, SVNPathUtil.PATH_COMPARATOR);
        SVNLogEntryPath realPath = null;
        List candidates = new ArrayList();
        for (int i = 0; i < paths.length; i++) {
            String copiedPath = (String) paths[i];
            
            if (!path.startsWith(copiedPath + "/")) {
                continue;
            } else if (path.equals(copiedPath)) {
                return (SVNLogEntryPath) myCopiedPaths.get(copiedPath);
            }
            candidates.add(copiedPath);
        }
        // check candidates from the end of the list
        for(int i = candidates.size() - 1; i >=0; i--) {
            String candidateParent = (String) candidates.get(i);
            if (getSourceRepository().checkPath(candidateParent, myTargetRevision) != SVNNodeKind.DIR) {
                continue;
            }
            SVNLogEntryPath changedPath = (SVNLogEntryPath) myCopiedPaths.get(candidateParent);
            String fileRelativePath = path.substring(candidateParent.length() + 1);
            fileRelativePath = SVNPathUtil.append(changedPath.getCopyPath(), fileRelativePath);
            return new SVNLogEntryPath(path, ' ', fileRelativePath, changedPath.getCopyRevision());
        }
        return realPath;
    }

    private boolean areFileContentsEqual(String path1, long rev1, String path2, long rev2, Map props2) throws SVNException {
        Map props1 = new HashMap();
        props2 = props2 == null ? new HashMap() : props2;

        SVNRepository repos = getSourceRepository();
        repos.getFile(path1, rev1, props1, null);
        repos.getFile(path2, rev2, props2, null);
        String crc1 = (String) props1.get(SVNProperty.CHECKSUM);
        String crc2 = (String) props2.get(SVNProperty.CHECKSUM);
        return crc1 != null && crc1.equals(crc2);
    }

    public void openFile(String path, long revision) throws SVNException {
        EntryBaton baton = new EntryBaton(getSourceRepository().getRepositoryPath(path));
        baton.myPropsAct = ACCEPT;
        baton.myTextAct = ACCEPT;
        myPathsToFileBatons.put(path, baton);
        myCommitEditor.openFile(path, myPreviousRevision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        EntryBaton baton = (EntryBaton) myPathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            myCommitEditor.applyTextDelta(path, baseChecksum);
        }
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        EntryBaton baton = (EntryBaton) myPathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            return myCommitEditor.textDeltaChunk(path, diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        EntryBaton baton = (EntryBaton) myPathsToFileBatons.get(path);
        if (baton.myTextAct == ACCEPT) {
            myCommitEditor.textDeltaEnd(path);
        }
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (!SVNProperty.isRegularProperty(name)) {
            return;
        }
        EntryBaton baton = (EntryBaton) myPathsToFileBatons.get(path);
        if (baton.myPropsAct == ACCEPT) {
            myCommitEditor.changeFileProperty(path, name, value);
        } else if (baton.myPropsAct == DECIDE) {
            String propVal = (String)baton.myProps.get(name);
            if (propVal != null && propVal.equals(value)) {
                /*
                 * The properties seem to be the same as of the copy origin,
                 * do not reset them again.
                 */
                baton.myPropsAct = IGNORE;
                return;
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeFileProperty(path, name, value);
        }
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        EntryBaton baton = (EntryBaton) myPathsToFileBatons.get(path);
        if (baton.myTextAct != IGNORE || baton.myTextAct != IGNORE) {
            myCommitEditor.closeFile(path, textChecksum);
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myCommitInfo = myCommitEditor.closeEdit();
        return myCommitInfo;
        
    }

    public void abortEdit() throws SVNException {
        myCommitEditor.abortEdit();
    }
    
    /**
     * Returns commit information on the revision 
     * committed to the replication destination repository.
     * 
     * @return commit info (revision, author, date)
     */
    public SVNCommitInfo getCommitInfo() {
        return myCommitInfo;
    }
    
    private SVNRepository getSourceRepository() throws SVNException {
        if (mySourceRepository == null) {
            mySourceRepository = SVNRepositoryFactory.create(myRepos.getLocation());
            mySourceRepository.setAuthenticationManager(myRepos.getAuthenticationManager());
        }
        return mySourceRepository;

    }
    
    private void completeDeletion(String dirPath) throws SVNException {
        Collection pathsToDelete = new ArrayList();
        for(Iterator paths = myChangedPaths.keySet().iterator(); paths.hasNext();){
            String path = (String)paths.next();
            if (!path.startsWith(dirPath + "/")) {
                continue;
            }
            SVNLogEntryPath pathChange = (SVNLogEntryPath)myChangedPaths.get(path);
            if(pathChange.getType() == SVNLogEntryPath.TYPE_DELETED){
                String relativePath = path.substring(dirPath.length() + 1);
                pathsToDelete.add(relativePath);
            }
        }
        String[] pathsArray = (String[]) pathsToDelete.toArray(new String[pathsToDelete.size()]);
        Arrays.sort(pathsArray, SVNPathUtil.PATH_COMPARATOR);
        String currentOpened = "";
        for(int i = 0; i < pathsArray.length; i++) {
            String nextRelativePath = pathsArray[i];
            while(!"".equals(currentOpened) && nextRelativePath.indexOf(currentOpened) == -1){
                myCommitEditor.closeDir();
                currentOpened = SVNPathUtil.removeTail(currentOpened);
            }
            
            String nextRelativePathToDelete = null;
            if(!"".equals(currentOpened)){
                nextRelativePathToDelete = nextRelativePath.substring(currentOpened.length() + 1);
            }else{
                nextRelativePathToDelete = nextRelativePath;
            }

            String[] entries = nextRelativePathToDelete.split("/");
            int j = 0;
            for(j = 0; j < entries.length - 1; j++){
                currentOpened = SVNPathUtil.append(currentOpened, entries[j]);
                myCommitEditor.openDir(SVNPathUtil.append(dirPath, currentOpened), myPreviousRevision);
            }
            String pathToDelete = SVNPathUtil.append(currentOpened, entries[j]);
            String absPathToDelete = SVNPathUtil.append(dirPath, pathToDelete);
            myCommitEditor.deleteEntry(absPathToDelete, myPreviousRevision);
            myChangedPaths.remove(absPathToDelete);
        }
        while(!"".equals(currentOpened)){
            myCommitEditor.closeDir();
            currentOpened = SVNPathUtil.removeTail(currentOpened);
        }
    }

    private static class EntryBaton {
        
        public EntryBaton(String path) {
            myPath = path;
        }

        private String myPath;
        private int myPropsAct;
        private int myTextAct;
        private Map myProps;
    }
}

