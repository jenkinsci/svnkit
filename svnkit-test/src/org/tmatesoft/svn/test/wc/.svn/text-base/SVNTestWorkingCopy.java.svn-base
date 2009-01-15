/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestWorkingCopy implements ISVNWorkingCopy {

    private SVNWCAccess myWCAccess;

    private File myRoot;

    public SVNTestWorkingCopy(File root) {
        myRoot = root;
    }

    private File getRootDirectory() {
        return myRoot;
    }

    private File getFile(String path) {
        return new File(getRootDirectory(), path);
    }

    public AbstractSVNTestFile getRoot() {
        return new SVNTestFile(myRoot, "");
    }

    public AbstractSVNTestFile getTestFile(String path) throws SVNException {
        return new SVNTestFile(getRootDirectory(), path).reload();
    }

    public AbstractSVNTestFile[] getChildren(AbstractSVNTestFile file) throws SVNException {
        File[] children = SVNFileListUtil.listFiles(getFile(file.getPath()));
        AbstractSVNTestFile[] result = new SVNTestFile[children.length];
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            String path = SVNPathUtil.getRelativePath(getRootDirectory().getAbsolutePath(), child.getAbsolutePath());
            SVNTestFile testFile = new SVNTestFile(getRootDirectory(), path);
            testFile = testFile.loadContent();
            result[i] = testFile;
        }
        return result;
    }

    public SVNWCAccess getWCAccess() {
        if (myWCAccess == null) {
            myWCAccess = SVNWCAccess.newInstance(SVNTestDebugLog.getEventHandler());
        }
        return myWCAccess;
    }

    public void walk(ISVNWorkingCopyWalker walker) throws SVNException {
        getWCAccess().open(getRootDirectory(), false, -1);
        getWCAccess().walkEntries(getRootDirectory(), new SVNTestEntryHandler(walker), true, SVNDepth.INFINITY);
    }

    private class SVNTestEntryHandler implements ISVNEntryHandler {

        private ISVNWorkingCopyWalker myWalker;


        private SVNTestEntryHandler(ISVNWorkingCopyWalker walker) {
            myWalker = walker;
        }

        private ISVNWorkingCopyWalker getWalker() {
            return myWalker;
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            String entryPath = SVNPathUtil.getRelativePath(getRootDirectory().getAbsolutePath(), path.getAbsolutePath());
            SVNTestFile testFile = (SVNTestFile) getTestFile(entryPath);
            SVNAdminArea parentDir = getWCAccess().retrieve(path.getParentFile());
            testFile.reload(entry, parentDir);
            getWalker().handleEntry(testFile);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }
}
