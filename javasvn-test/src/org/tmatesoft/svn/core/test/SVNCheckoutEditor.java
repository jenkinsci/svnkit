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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public class SVNCheckoutEditor implements ISVNEditor {
    
    private File myRoot;
    private Stack myStack;
    private File myTempFile;
    private SVNDiffWindow myDiffWindow;
    
    public SVNCheckoutEditor(File root) {
        myRoot = root;
        myRoot.mkdirs();
        myStack = new Stack();
    }    

    public void targetRevision(long revision) throws SVNException {
    }
    public void openRoot(long revision) throws SVNException {
        
        myRoot.mkdirs();
        myStack.push(myRoot);
    }
    public void deleteEntry(String path, long revision) throws SVNException {
    }
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }
    
    
    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        File dir = new File(myRoot, path);
        dir.mkdir();
        myStack.push(dir);
    }
    
    public void openDir(String path, long revision) throws SVNException {
        myStack.push(new File(myRoot, path));
        ((File) myStack.peek()).mkdirs();
    }
    
    public void changeDirProperty(String name, String value) throws SVNException {
    }
    public void closeDir() throws SVNException {
        myStack.pop();
    }
    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        myStack.push(new File(myRoot, path));
    }
    public void openFile(String path, long revision) throws SVNException {
        myStack.push(new File(myRoot, path));
    }
    public void closeFile(String textChecksum) throws SVNException {
        myStack.pop();
    }
    
    public void applyTextDelta(String baseChecksum)  throws SVNException {
        // do nothing.
    }
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        try {
        //  create temp file.
            myTempFile = File.createTempFile("svn", "test");
            myTempFile.deleteOnExit();
            myDiffWindow = diffWindow;        
        
            return new FileOutputStream(myTempFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        }
    }
    
    public void textDeltaEnd() throws SVNException {
        SVNRAFileData target = null;
        SVNRAFileData source = null;
        InputStream is = null;
        File tempFile = null;
        try {
            tempFile = File.createTempFile("svn", "temp");
            tempFile.deleteOnExit();
            is = new FileInputStream(myTempFile);
            source = new SVNRAFileData((File) myStack.peek());
            target = new SVNRAFileData(tempFile);

            myDiffWindow.apply(source, target, is, 0);
            source.close();
            target.close();
            File targetFile = (File) myStack.peek();
            if (targetFile.exists() && !targetFile.delete()) {
                throw new SVNException("can't delete file: " + ((File) myStack.peek()).getAbsolutePath());
            }
            FSUtil.copyAll(tempFile, targetFile.getParentFile(), targetFile.getName(), null);
            tempFile.delete();  
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        } catch(Throwable th) {
            th.printStackTrace();
	    throw new SVNException(th);	
        } finally {
            if (myTempFile != null) {
                myTempFile.delete();
                myTempFile = null;
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e1) {
                }
            }
        }
    }
    
    public void changeFileProperty(String name, String value) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myStack.clear();
        if (myTempFile != null) {
            myTempFile.delete();
        }
        myTempFile = null;
        return null;
    }
    public void abortEdit() throws SVNException {
        closeEdit();
    }
}
