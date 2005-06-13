package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 14.06.2005
 * Time: 0:15:15
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;

    public SVNCommitClient() {
    }

    public SVNCommitClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNCommitClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNCommitClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNCommitClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNCommitClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void setCommitHander(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    public long doDelete(String[] urls, String commitMessage) throws SVNException {
        if (urls == null || urls.length == 0) {
            return -1;
        }
        List paths = new ArrayList();
        String rootURL = SVNPathUtil.condenceURLs(urls, paths, true);
        if (rootURL == null || "".equals(rootURL)) {
            // something strange, t
            SVNErrorManager.error("svn: Cannot deleted passed URLs as part of a single commit, probably they are refer to the different repositories");
        }
        if (paths.isEmpty()) {
            // there is just root.
            paths.add(PathUtil.tail(rootURL));
            rootURL = PathUtil.removeTail(rootURL);
        }
        SVNCommitItem[] commitItems = new SVNCommitItem[paths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) paths.get(i);
            commitItems[i] = new SVNCommitItem(null, PathUtil.append(rootURL, path), null, null, null, false, true, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return -1;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(PathUtil.decode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL);
        for (Iterator commitPath = paths.iterator(); commitPath.hasNext();) {
            String path = (String) commitPath.next();
            SVNNodeKind kind = repos.checkPath(path, -1);
            if (kind == SVNNodeKind.NONE) {
                String url = PathUtil.append(rootURL, path);
                SVNErrorManager.error("svn: URL '" + url + "' does not exist");
            }
        }
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        ISVNCommitPathHandler deleter = new ISVNCommitPathHandler() {
            public void handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.deleteEntry(commitPath, -1);
            }
        };
        SVNCommitInfo info;
        try {
            info = SVNCommitUtil.driveCommitEditor(deleter, paths, commitEditor, -1);
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        return info != null ? info.getNewRevision() : -1;
    }

    public long doMkDir(String[] urls, String commitMessage) throws SVNException {
        if (urls == null || urls.length == 0) {
            return -1;
        }
        List paths = new ArrayList();
        String rootURL = SVNPathUtil.condenceURLs(urls, paths, false);
        if (rootURL == null || "".equals(rootURL)) {
            SVNErrorManager.error("svn: Cannot create passed URLs as part of a single commit, probably they are refer to the different repositories");
        }
        if (paths.isEmpty()) {
            paths.add(PathUtil.tail(rootURL));
            rootURL = PathUtil.removeTail(rootURL);
        }
        if (paths.contains("")) {
            List convertedPaths = new ArrayList();
            String tail = PathUtil.tail(rootURL);
            rootURL = PathUtil.removeTail(rootURL);
            for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
                String path = (String) commitPaths.next();
                if ("".equals(path)) {
                    convertedPaths.add(tail);
                } else {
                    convertedPaths.add(PathUtil.append(tail, path));
                }
            }
            paths = convertedPaths;
        }
        SVNCommitItem[] commitItems = new SVNCommitItem[paths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) paths.get(i);
            commitItems[i] = new SVNCommitItem(null, PathUtil.append(rootURL, path), null, null, null, true, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return -1;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(PathUtil.decode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        ISVNCommitPathHandler creater = new ISVNCommitPathHandler() {
            public void handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.addDir(commitPath, null, -1);
            }
        };
        SVNCommitInfo info;
        try {
            info = SVNCommitUtil.driveCommitEditor(creater, paths, commitEditor, -1);
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        return info != null ? info.getNewRevision() : -1;
    }

    public long doImport(File path, String dstURL, String commitMessage, boolean recursive) throws SVNException {
        return -1;
    }

    public long doCommit(File[] paths, boolean keepLocks, String commitMessage, boolean recursive) throws SVNException {
        return -1;
    }

    public SVNCommitItem[] doCollectCommitItems(File[] paths, boolean recursive) throws SVNException {
        return null;
    }

}
