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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
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
 * @author  TMate Software Ltd.
 */
public class UpdatesToCommitsEditor implements ISVNEditor {

    private static final int ACCEPT = 0;
    private static final int IGNORE = 1;
    private static final int DECIDE = 2;
    
    ISVNEditor myCommitEditor;
    
    Map myCopiedPaths;
    
    Map myChangedPaths;
    
    SVNRepository myRepos;
    
    long myCurrentRevision;
    
    LinkedList myWindowsStack = new LinkedList();
    
    Map pathsToFileBatons = new HashMap();

    Stack myDirsStack = new Stack();

    public UpdatesToCommitsEditor(SVNRepository repository,  Map changedPaths, Map copiedPaths, ISVNEditor commitEditor, long rev){
        myRepos = repository;
        myCommitEditor = commitEditor;
        myChangedPaths = changedPaths;
        myCopiedPaths = copiedPaths;
        myCurrentRevision = rev;
    }
    
    public void targetRevision(long revision) throws SVNException{
    }
    
    public void openRoot(long revision) throws SVNException{
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);
    }
    
    public void deleteEntry(String path, long revision) throws SVNException{
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath deletedPath = (SVNLogEntryPath)myChangedPaths.get(absPath);
        if(deletedPath != null && deletedPath.getType() == 'D'){
            myChangedPaths.remove(absPath);
        }
        myCommitEditor.deleteEntry(path, myCurrentRevision);
    }
    
    public void absentDir(String path) throws SVNException{
    }
    
    public void absentFile(String path) throws SVNException{
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException{
        EntryBaton baton = new EntryBaton();
        myDirsStack.push(baton);
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath changedPath = (SVNLogEntryPath)myChangedPaths.get(absPath);
        if(changedPath != null && changedPath.getType() == 'A' && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0){
            baton.myPropsAct = DECIDE;
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.getDir(changedPath.getCopyPath(), changedPath.getCopyRevision(), props, (Collection)null);
            baton.myProps = props;
            myCommitEditor.addDir(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
            return;
        }else if(changedPath != null && (changedPath.getType() == 'A' || changedPath.getType() == 'R')){
            baton.myPropsAct = ACCEPT;
            myCommitEditor.addDir(path, null, -1);
            return;
        }else if(changedPath != null && changedPath.getType() == 'M'){
            baton.myPropsAct = ACCEPT;
            myCommitEditor.openDir(path, myCurrentRevision);
            return;
        }else if(changedPath == null){
            baton.myPropsAct = IGNORE;
            myCommitEditor.openDir(path, myCurrentRevision);
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addDir()");
        SVNErrorManager.error(err);
    }
    
    public void openDir(String path, long revision) throws SVNException{
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        myDirsStack.push(baton);
        myCommitEditor.openDir(path, myCurrentRevision);
    }
    
    public void changeDirProperty(String name, String value) throws SVNException{
        if(!SVNProperty.isRegularProperty(name)){
            return;
        }
        EntryBaton baton = (EntryBaton)myDirsStack.peek();
        if(baton.myPropsAct == ACCEPT){
            myCommitEditor.changeDirProperty(name, value);
        }else if(baton.myPropsAct == DECIDE){
            Object[] names = baton.myProps.keySet().toArray();
            for(int i = 0; i < names.length; i++){
                String existingName = (String)names[i];
                String existingValue = (String)baton.myProps.get(existingName);
                if(existingName.equals(name) && existingValue.equals(value)){
                    /* The properties seem to be the same as of the copy origin, 
                     * do not reset them again.
                     */
                    baton.myPropsAct = IGNORE;
                    return;
                }
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeDirProperty(name, value);
        }
        //else ignore props of the dir being copied
    }

    public void closeDir() throws SVNException{
        if(myDirsStack.size() > 1){
            myCommitEditor.closeDir();
        }
        myDirsStack.pop();
    }
    
    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException{
        EntryBaton baton = new EntryBaton();
        pathsToFileBatons.put(path, baton);
        String absPath = myRepos.getRepositoryPath(path);
        SVNLogEntryPath changedPath = (SVNLogEntryPath)myChangedPaths.get(absPath);
        if(changedPath != null && changedPath.getType() == 'A' && changedPath.getCopyPath() != null && changedPath.getCopyRevision() >= 0){
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            if(areFileContentsEqual(absPath, myCurrentRevision, changedPath.getCopyPath(), changedPath.getCopyRevision())){
                baton.myTextAct = IGNORE;
            }
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.getDir(changedPath.getCopyPath(), changedPath.getCopyRevision(), props, (Collection)null);
            baton.myProps = props;
            myCommitEditor.addFile(path, changedPath.getCopyPath(), changedPath.getCopyRevision());
            return;
        }else if(changedPath != null && (changedPath.getType() == 'A' || changedPath.getType() == 'R')){
            baton.myPropsAct = ACCEPT;
            baton.myTextAct = ACCEPT;
            myCommitEditor.addFile(path, null, -1);
            return;
        }else if(changedPath != null && changedPath.getType() == 'M'){
            baton.myPropsAct = DECIDE;
            baton.myTextAct = ACCEPT;
            SVNLogEntryPath realPath = getFileCopyOrigin(absPath);
            if(realPath == null){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown error, can't get the copy origin of a file");
                SVNErrorManager.error(err);
            }
            if(areFileContentsEqual(absPath, myCurrentRevision, realPath.getCopyPath(), realPath.getCopyRevision())){
                baton.myTextAct = IGNORE;
            }
            HashMap props = new HashMap();
            SVNRepository rep = SVNRepositoryFactory.create(myRepos.getLocation());
            rep.getDir(realPath.getCopyPath(), realPath.getCopyRevision(), props, (Collection)null);
            baton.myProps = props;
            myCommitEditor.openFile(path, myCurrentRevision);
            return;
        }else if(changedPath == null){
            baton.myPropsAct = IGNORE;
            baton.myTextAct = IGNORE;
            myCommitEditor.openFile(path, myCurrentRevision);
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown bug in addFile()");
        SVNErrorManager.error(err);
    }
    
    private SVNLogEntryPath getFileCopyOrigin(String path) throws SVNException{
        Object[] paths = myCopiedPaths.keySet().toArray();
        SVNLogEntryPath realPath = null;
        for(int i = 0; i < paths.length; i++){
            String copiedPath = (String)paths[i];
            if(!path.startsWith(copiedPath)){
                continue;
            }
            String relativePath = path.substring(copiedPath.endsWith("/") ? copiedPath.length() : copiedPath.length() + 1);
            SVNLogEntryPath changedPath = (SVNLogEntryPath)myCopiedPaths.get(copiedPath);
            String realFilePath = SVNPathUtil.concatToAbs(changedPath.getCopyPath(), relativePath);
            SVNRepository repos = SVNRepositoryFactory.create(myRepos.getLocation());
            if(repos.checkPath(realFilePath, changedPath.getCopyRevision()) == SVNNodeKind.FILE){
                realPath = new SVNLogEntryPath(path, ' ', realFilePath, changedPath.getCopyRevision());
                break;
            }
        }
        return realPath;
    }
    
    private boolean areFileContentsEqual(String path1, long rev1, String path2, long rev2) throws SVNException {
        File file1 = SVNFileUtil.createTempFile(path1 + ".left", "tmp");
        File file2 = SVNFileUtil.createTempFile(path2 + ".right", "tmp");
        OutputStream os1 = null;
        OutputStream os2 = null;
        try{
            os1 = SVNFileUtil.openFileForWriting(file1);
            os2 = SVNFileUtil.openFileForWriting(file2);
            SVNRepository repos = SVNRepositoryFactory.create(myRepos.getLocation());
            repos.getFile(path1, rev1, null, os1);
            repos.getFile(path2, rev2, null, os2);
        }finally{
            SVNFileUtil.closeFile(os1);
            SVNFileUtil.closeFile(os2);
        }
        InputStream is1 = null;
        InputStream is2 = null;
        try{
            is1 = SVNFileUtil.openFileForReading(file1);
            is2 = SVNFileUtil.openFileForReading(file2);
            int r1 = -1;
            int r2 = -1;
            while(true){
                r1 = is1.read();
                r2 = is2.read();
                if(r1 != r2){
                    return false;
                }
                if(r1 == -1){//we've finished - files do not differ
                    break;
                }
            }
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(is1);
            SVNFileUtil.closeFile(is2);
            SVNFileUtil.deleteFile(file1);
            SVNFileUtil.deleteFile(file2);
        }
        return true;
    }
    
    public void openFile(String path, long revision) throws SVNException{
        EntryBaton baton = new EntryBaton();
        baton.myPropsAct = ACCEPT;
        baton.myTextAct = ACCEPT;
        pathsToFileBatons.put(path, baton);
        myCommitEditor.openFile(path, myCurrentRevision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException{
        EntryBaton baton = (EntryBaton)pathsToFileBatons.get(path);
        if(baton.myTextAct == ACCEPT){
            //TODO: remove later debug trace
            SVNDebugLog.logInfo("Accepting delta for " + path);
            myCommitEditor.applyTextDelta(path, baseChecksum);
        }
    }
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException{
        EntryBaton baton = (EntryBaton)pathsToFileBatons.get(path);
        if(baton.myTextAct == ACCEPT){
            //TODO: remove later debug trace
            SVNDebugLog.logInfo("Text chunk for " + path);
            return myCommitEditor.textDeltaChunk(path, diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }
    
    public void textDeltaEnd(String path) throws SVNException{
        EntryBaton baton = (EntryBaton)pathsToFileBatons.get(path);
        if(baton.myTextAct == ACCEPT){
            //TODO: remove later debug trace
            SVNDebugLog.logInfo("End of text for " + path);
            myCommitEditor.textDeltaEnd(path);
        }
    }
    
    public void changeFileProperty(String path, String name, String value) throws SVNException{
        if(!SVNProperty.isRegularProperty(name)){
            return;
        }
        EntryBaton baton = (EntryBaton)pathsToFileBatons.get(path);
        if(baton.myPropsAct == ACCEPT){
            myCommitEditor.changeFileProperty(path, name, value);
        }else if(baton.myPropsAct == DECIDE){
            Object[] names = baton.myProps.keySet().toArray();
            for(int i = 0; i < names.length; i++){
                String existingName = (String)names[i];
                String existingValue = (String)baton.myProps.get(existingName);
                if(existingName.equals(name) && existingValue.equals(value)){
                    /* The properties seem to be the same as of the copy origin, 
                     * do not reset them again.
                     */
                    baton.myPropsAct = IGNORE;
                    return;
                }
            }
            /*
             * Properties do differ, accept them.
             */
            baton.myPropsAct = ACCEPT;
            myCommitEditor.changeFileProperty(path, name, value);

        }
        //ignore props of the file being copied
    }
    
    public void closeFile(String path, String textChecksum) throws SVNException{
        myCommitEditor.closeFile(path, textChecksum);
    }
    
    public SVNCommitInfo closeEdit() throws SVNException{
        return null;
    }
    
    public void abortEdit() throws SVNException{
        myCommitEditor.abortEdit();
    }

    private class EntryBaton {
        
        int myPropsAct;
        int myTextAct;
        HashMap myProps;
    }
}
