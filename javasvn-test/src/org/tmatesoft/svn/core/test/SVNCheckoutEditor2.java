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

package org.tmatesoft.svn.core.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNFileEntry;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNCheckoutEditor2 implements ISVNEditor {
    
    private Stack myStack;
    
    private long myTargetRevision;    
    private ISVNEntry myRootEntry;
    private ISVNFileEntry myCurrentFile;
    private SVNDiffWindow myDiffWindow;
    private ISVNWorkspaceMediator myMediator;
    
    public SVNCheckoutEditor2(ISVNWorkspaceMediator mediator, ISVNEntry rootEntry) {
        myRootEntry = rootEntry;
        myStack = new Stack();
        myMediator = mediator;
    }    

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }
    
    public void openRoot(long revision) throws SVNException {
        myRootEntry.setPropertyValue("svn:entry:committed-rev", Long.toString(revision));
        myStack.push(myRootEntry);
    }
    public void deleteEntry(String path, long revision) throws SVNException {
        getCurrentEntry().deleteChild(PathUtil.tail(path), false);
    }
    
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }
    
    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        ISVNEntry newDir = getCurrentEntry().addDirectory(PathUtil.tail(path), -1);
        myStack.push(newDir);
    }
    public void openDir(String path, long revision) throws SVNException {
        String name = PathUtil.tail(path);
        ISVNEntry dir = getCurrentEntry().getChild(name);
        if (dir != null) {
            myStack.push(dir);
        }
    }
    public void changeDirProperty(String name, String value) throws SVNException {
        getCurrentEntry().setPropertyValue(name, value);
    }
    public void closeDir() throws SVNException {
        getCurrentEntry().setPropertyValue("svn:entry:revision", Long.toString(myTargetRevision));
        getCurrentEntry().merge(true);
        getCurrentEntry().dispose();
        myStack.pop();
    }
    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        myCurrentFile = getCurrentEntry().addFile(PathUtil.tail(path), -1);
    }
    public void openFile(String path, long revision) throws SVNException {
        String name = PathUtil.tail(path);
        myCurrentFile = (ISVNFileEntry) getCurrentEntry().getChild(name);
    }
    
    public void closeFile(String textChecksum) throws SVNException {
        myCurrentFile.setPropertyValue("svn:entry:checksum", textChecksum);
        myCurrentFile.merge(true);
        myCurrentFile.dispose();
        myCurrentFile = null;
    }
    
    public void applyTextDelta(String baseChecksum)  throws SVNException {
        // do nothing.
    }
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        myDiffWindow = diffWindow;
        try {
            return myMediator.createTemporaryLocation(diffWindow);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        }
    }    
    public void textDeltaEnd() throws SVNException {
        
        InputStream newData = null;
        try {
            newData = myMediator.getTemporaryLocation(myDiffWindow);
            myCurrentFile.applyDelta(myDiffWindow, newData, false);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        } finally {
            if (newData != null) {
                try {
                    newData.close();
                } catch (IOException e1) {
                }
            }            
        }
        myMediator.deleteTemporaryLocation(myDiffWindow);
        myDiffWindow = null;
    }
    
    
    public void changeFileProperty(String name, String value) throws SVNException {
        myCurrentFile.setPropertyValue(name, value);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myStack.clear();
        return new SVNCommitInfo(myTargetRevision, null, null);
    }
    
    public void abortEdit() throws SVNException {
        myStack.clear();
    }
    
    private ISVNDirectoryEntry getCurrentEntry() {
        return (ISVNDirectoryEntry) myStack.peek();
    }
}
