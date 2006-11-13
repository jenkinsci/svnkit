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

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNStatusReporter implements ISVNReporterBaton, ISVNReporter {

    private ISVNReporter myReporter;
    private ISVNReporterBaton myBaton;
    private String myRepositoryLocation;
    private SVNRepository myRepository;
    private SVNURL myRepositoryRoot;
    private Map myLocks;

    private SVNStatusEditor myEditor;

    public SVNStatusReporter(SVNRepository repos, ISVNReporterBaton baton, SVNStatusEditor editor) {
        myBaton = baton;
        myRepository = repos;
        myRepositoryLocation = repos.getLocation().toString();
        myEditor = editor;
        myLocks = new HashMap();
    }

    public SVNLock getLock(SVNURL url) {
        // get decoded path
        if (myRepositoryRoot == null || myLocks.isEmpty() || url == null) {
            return null;
        }
        String urlString = url.getPath();
        String root = myRepositoryRoot.getPath();
        String path;
        if (urlString.equals(root)) {
            path = "/";
        } else {
            path = urlString.substring(root.length());
        }
        return (SVNLock) myLocks.get(path);
    }

    public void report(ISVNReporter reporter) throws SVNException {
        myReporter = reporter;
        myBaton.report(this);
    }

    public void setPath(String path, String lockToken, long revision,
            boolean startEmpty) throws SVNException {
        myReporter.setPath(path, lockToken, revision, startEmpty);
    }

    public void deletePath(String path) throws SVNException {
        myReporter.deletePath(path);
    }

    public void linkPath(SVNURL url, String path,
            String lockToken, long revison, boolean startEmpty)
            throws SVNException {
        String rootURL = SVNPathUtil.getCommonURLAncestor(url.toString(), myRepositoryLocation);
        if (rootURL.length() < myRepositoryLocation.length()) {
            myRepositoryLocation = rootURL;
        }
        myReporter.linkPath(url, path, lockToken, revison, startEmpty);
    }

    public void finishReport() throws SVNException {
        // collect locks
        SVNLock[] locks = null;
        try {
            myRepositoryRoot = myRepository.getRepositoryRoot(true);
            locks = myRepository.getLocks("");
        } catch (SVNException e) {
            if (!(e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
                throw e;
            }
        }
        if (locks != null) {
            for (int i = 0; i < locks.length; i++) {
                SVNLock lock = locks[i];
                myLocks.put(lock.getPath(), lock);
            }
        }
        myEditor.setRepositoryInfo(myRepositoryRoot, myLocks);
        myReporter.finishReport();
    }

    public void abortReport() throws SVNException {
        myReporter.abortReport();
    }
}
