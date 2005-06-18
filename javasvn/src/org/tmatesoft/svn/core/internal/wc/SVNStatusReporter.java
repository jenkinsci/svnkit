package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 18.06.2005
 * Time: 0:55:55
 * To change this template use File | Settings | File Templates.
 */
public class SVNStatusReporter implements ISVNReporterBaton, ISVNReporter {

    private ISVNReporter myReporter;
    private ISVNReporterBaton myBaton;
    private String myRepositoryLocation;
    private SVNRepository myRepository;
    private String myRepositoryRoot;
    private Map myLocks;
    private SVNStatusEditor myEditor;

    public SVNStatusReporter(SVNRepository repos, ISVNReporterBaton baton, SVNStatusEditor editor) {
        myBaton = baton;
        myRepository = repos;
        myRepositoryLocation = repos.getLocation().toString();
        myEditor = editor;
        myLocks = new HashMap();
    }

    public SVNLock getLock(String url) {
        DebugLog.log("fetching lock for " + url);
        if (myRepositoryRoot == null || myLocks.isEmpty()) {
            return null;
        }
        url = url.substring(url.indexOf("://") + 3);
        url = url.substring(url.indexOf("/") + 1);
        url = url.substring(myRepositoryRoot.length());
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        url = PathUtil.decode(url);
        return (SVNLock) myLocks.get(url);
    }

    public void report(ISVNReporter reporter) throws SVNException {
        myReporter = reporter;
        myBaton.report(this);
    }

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        myReporter.setPath(path, lockToken, revision, startEmpty);
    }

    public void deletePath(String path) throws SVNException {
        myReporter.deletePath(path);
    }

    public void linkPath(SVNRepositoryLocation repository, String path, String lockToken, long revison, boolean startEmtpy) throws SVNException {
        String url = repository.toString();
        String rootURL = SVNPathUtil.getCommonURLAncestor(url, myRepositoryLocation);
        if (rootURL.length() < myRepositoryLocation.length()) {
            myRepositoryLocation = rootURL;
        }
        myReporter.linkPath(repository, path, lockToken, revison, startEmtpy);
    }

    public void finishReport() throws SVNException {
        myReporter.finishReport();
        // collect locks
        String path = myRepositoryLocation.substring(myRepository.getLocation().toString().length());
        SVNLock[] locks = null;
        try {
            myRepositoryRoot = myRepository.getRepositoryRoot(true);
            myRepositoryRoot = PathUtil.encode(myRepositoryRoot);
            locks = myRepository.getLocks(path);
        } catch (SVNException e) {
            //
        }
        if (locks != null) {
            for (int i = 0; i < locks.length; i++) {
                SVNLock lock = locks[i];
                myLocks.put(lock.getPath(), lock);
            }
        }
        DebugLog.log("collected locks : " + myLocks);
        DebugLog.log("status call root: " + myRepositoryLocation);
        DebugLog.log("repository root : " + myRepositoryRoot);
        myEditor.setStatusReporter(this);
    }

    public void abortReport() throws SVNException {
        myReporter.abortReport();
    }
}
