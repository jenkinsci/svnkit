/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNStatusReporter implements ISVNReporterBaton, ISVNReporter {

    private ISVNReporter myReporter;
    private ISVNReporterBaton myBaton;
    private String myRepositoryLocation;
    private SVNRepository myRepository;
    private String myRepositoryRoot;
    private Map myLocks;

    private SVNStatusEditor myEditor;

    public SVNStatusReporter(SVNRepository repos, ISVNReporterBaton baton,
            SVNStatusEditor editor) {
        myBaton = baton;
        myRepository = repos;
        myRepositoryLocation = repos.getLocation().toString();
        myEditor = editor;
        myLocks = new HashMap();
    }

    public SVNLock getLock(String url) {
        if (myRepositoryRoot == null || myLocks.isEmpty()) {
            return null;
        }
        url = url.substring(url.indexOf("://") + 3);
        url = url.substring(url.indexOf("/") + 1);
        url = url.substring(myRepositoryRoot.length());
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        url = SVNEncodingUtil.uriDecode(url);
        return (SVNLock) myLocks.get(url);
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
            String lockToken, long revison, boolean startEmtpy)
            throws SVNException {
        String rootURL = SVNPathUtil.getCommonURLAncestor(url.toString(), myRepositoryLocation);
        if (rootURL.length() < myRepositoryLocation.length()) {
            myRepositoryLocation = rootURL;
        }
        myReporter.linkPath(url, path, lockToken, revison, startEmtpy);
    }

    public void finishReport() throws SVNException {
        myReporter.finishReport();
        // collect locks
        SVNLock[] locks = null;
        try {
            myRepositoryRoot = myRepository.getRepositoryRoot(true).getURIEncodedPath();
            locks = myRepository.getLocks("");
        } catch (SVNException e) {
            //
        }
        if (locks != null) {
            for (int i = 0; i < locks.length; i++) {
                SVNLock lock = locks[i];
                myLocks.put(lock.getPath(), lock);
            }
        }
        myEditor.setStatusReporter(this);
    }

    public void abortReport() throws SVNException {
        myReporter.abortReport();
    }
}
