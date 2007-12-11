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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNTestUpdateEditor implements ISVNEditor {

    private Stack myDirsStack;
    private Map myItems;
    private SVNRepository myRepository;
    private int myNumberOfChanges;

    public SVNTestUpdateEditor(SVNRepository repository, Map items) {
        myRepository = repository;
        myItems = items == null ? new HashMap() : items;
        myDirsStack = new Stack();
        myNumberOfChanges = 0;
    }

    public int getNumberOfChanges() {
        return myNumberOfChanges;
    }

    public Map getItems() {
        return myItems;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        SVNItem item = new SVNItem("/", SVNNodeKind.DIR);
        myDirsStack.push(item);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        myNumberOfChanges++;
        // sanity removal action
        myItems.remove(absPath);
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem item = new SVNItem(absPath, SVNNodeKind.DIR);
        myDirsStack.push(item);
        myItems.put(absPath, item);
        myNumberOfChanges++;
    }

    public void openDir(String path, long revision) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem item = new SVNItem(absPath, SVNNodeKind.DIR);
        myDirsStack.push(item);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        SVNItem curDir = (SVNItem) myDirsStack.peek();
        String absPath = curDir.getRepositoryPath();
        curDir.changeProperty(name, value);

        if (myItems.get(absPath) == null) {
            myItems.put(absPath, curDir);
        }

        myNumberOfChanges++;
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        SVNItem curDir = (SVNItem) myDirsStack.peek();
        String absPath = curDir.getRepositoryPath();
        curDir.changeProperty(name, value);

        if (myItems.get(absPath) == null) {
            myItems.put(absPath, curDir);
        }

        myNumberOfChanges++;
    }

    public void closeDir() throws SVNException {
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem item = new SVNItem(absPath, SVNNodeKind.FILE);
        myItems.put(absPath, item);
        myNumberOfChanges++;
    }

    public void openFile(String path, long revision) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem item = new SVNItem(absPath, SVNNodeKind.FILE);
        myItems.put(absPath, item);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem fileItem = (SVNItem) myItems.get(absPath);
        fileItem.changeProperty(name, value);
        myNumberOfChanges++;
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem fileItem = (SVNItem) myItems.get(absPath);
        fileItem.changeProperty(name, value);
        myNumberOfChanges++;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem fileItem = (SVNItem) myItems.get(absPath);
        fileItem.setChecksum(textChecksum);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myNumberOfChanges++;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        String absPath = myRepository.getRepositoryPath(path);
        SVNItem fileItem = (SVNItem) myItems.get(absPath);
        fileItem.incrementDeltaChunk();
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

}
