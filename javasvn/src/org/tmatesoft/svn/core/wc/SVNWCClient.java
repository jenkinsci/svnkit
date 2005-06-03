/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNWCClient extends SVNBasicClient {

    public SVNWCClient(final ISVNCredentialsProvider credentials, ISVNEventListener eventDispatcher) {
        super(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
                repos.setCredentialsProvider(credentials);
                return repos;
            }
        }, null, eventDispatcher);
    }

    public SVNWCClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }
    
    public void doCleanup(File path) throws SVNException {
        if (!SVNWCAccess.isVersionedDirectory(path)) {
            SVNErrorManager.error(0, null);
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, true, true);
        wcAccess.getAnchor().cleanup();
        wcAccess.close(true, true);
    }
    
    public void doDelete(File path, boolean force, boolean dryRun) {
    }

    public void doAdd(File path, boolean mkdir) {
    }

    public void doRevert(File path, boolean recursive) {
    }

    public void doResolve(File path, boolean recursive) {
    }

    public void doLock(File[] paths, boolean stealLock, String lockMessage) throws SVNException {
        Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
                if (entry == null || entry.isHidden()) {
                    SVNErrorManager.error("svn: '" + entry.getName() + "' is not under version control");
                }
                if (entry.getURL() == null) {
                    SVNErrorManager.error("svn: '" + entry.getName() + "' has no URL");
                }
                SVNRevision revision = stealLock ? SVNRevision.UNDEFINED : SVNRevision.create(entry.getRevision());
                entriesMap.put(entry.getURL(), new LockInfo(paths[i], revision));
                wcAccess.getAnchor().getEntries().close();
            } finally {
                wcAccess.close(true, false);
            }
        }
        for (Iterator urls = entriesMap.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            LockInfo info = (LockInfo) entriesMap.get(url);
            SVNWCAccess wcAccess = createWCAccess(info.myFile);

            SVNRepository repos = createRepository(url);
            SVNLock lock = null;
            try {
                lock = repos.setLock("", lockMessage, stealLock, info.myRevision.getNumber());
            } catch (SVNException error) {
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.LOCK_FAILED, null,
                        error.getMessage()),
                        ISVNEventListener.UNKNOWN);
                continue;
            }
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
                entry.setLockToken(lock.getID());
                entry.setLockComment(lock.getComment());
                entry.setLockOwner(lock.getOwner());
                entry.setLockCreationDate(TimeUtil.formatDate(lock.getCreationDate()));
                wcAccess.getAnchor().getEntries().save(true);
                wcAccess.getAnchor().getEntries().close();
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.LOCKED, lock, null),
                        ISVNEventListener.UNKNOWN);
            } catch (SVNException e) {
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.LOCK_FAILED, lock, e.getMessage()),
                        ISVNEventListener.UNKNOWN);
            } finally {
                wcAccess.close(true, false);
            }
        }
    }

    public void doLock(String[] urls, boolean stealLock, String lockMessage) throws SVNException  {
        for (int i = 0; i < urls.length; i++) {           
            String url = validateURL(urls[i]);
            SVNRepository repos = createRepository(url);
            SVNLock lock = null;
            try {
                lock = repos.setLock("", lockMessage, stealLock, -1);
            } catch (SVNException error) {
                svnEvent(SVNEventFactory.createLockEvent(url, SVNEventAction.LOCK_FAILED, lock, null),
                        ISVNEventListener.UNKNOWN);
                continue;
            }
            svnEvent(SVNEventFactory.createLockEvent(url, SVNEventAction.LOCKED, lock, null),
                    ISVNEventListener.UNKNOWN);
        }
    }
    
    public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
        Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
                if (entry == null || entry.isHidden()) {
                    SVNErrorManager.error("svn: '" + entry.getName() + "' is not under version control");
                }
                if (entry.getURL() == null) {
                    SVNErrorManager.error("svn: '" + entry.getName() + "' has no URL");
                }
                String lockToken = entry.getLockToken();
                if (!breakLock && lockToken == null) {
                    SVNErrorManager.error("svn: '" + entry.getName() + "' is not locked in this working copy");
                }
                entriesMap.put(entry.getURL(), new LockInfo(paths[i], lockToken));
                wcAccess.getAnchor().getEntries().close();
            } finally {
                wcAccess.close(true, false);
            }
        }
        for (Iterator urls = entriesMap.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            LockInfo info = (LockInfo) entriesMap.get(url);
            SVNWCAccess wcAccess = createWCAccess(info.myFile);

            SVNRepository repos = createRepository(url);
            SVNLock lock = null;
            boolean removeLock = false;
            try {
                repos.removeLock("", info.myToken, breakLock);
                removeLock = true;
            } catch (SVNException error) {
                // remove lock if error is owner_mismatch.
                removeLock = true;
            }
            if (!removeLock) {
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.UNLOCK_FAILED, null, 
                        "unlock failed"), ISVNEventListener.UNKNOWN);
                continue;
            }
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
                entry.setLockToken(null);
                entry.setLockComment(null);
                entry.setLockOwner(null);
                entry.setLockCreationDate(null);
                wcAccess.getAnchor().getEntries().save(true);
                wcAccess.getAnchor().getEntries().close();
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.UNLOCKED, lock, null),
                        ISVNEventListener.UNKNOWN);
            } catch (SVNException e) {
                svnEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess.getTargetName(), SVNEventAction.UNLOCK_FAILED, lock, 
                        e.getMessage()),
                        ISVNEventListener.UNKNOWN);
            } finally {
                wcAccess.close(true, false);
            }
        }
    }

    public void doUnlock(String[] urls, boolean breakLock) throws SVNException  {
        Map lockTokens = new HashMap();
        if (!breakLock) {
            for (int i = 0; i < urls.length; i++) {
                String url = validateURL(urls[i]);
                // get lock token for url
                SVNRepository repos = createRepository(url);
                SVNLock lock = repos.getLock("");
                if (lock == null) {
                    SVNErrorManager.error("svn: '" + url+ "' is not locked");
                }                
                lockTokens.put(url, lock.getID());
            }
        }
        for (int i = 0; i < urls.length; i++) {
            String url = validateURL(urls[i]);
            // get lock token for url
            SVNRepository repos = createRepository(url);
            String id = (String) lockTokens.get(url);
            try {
                repos.removeLock("", id, breakLock);
            } catch (SVNException e) {
                svnEvent(SVNEventFactory.createLockEvent(url, SVNEventAction.UNLOCK_FAILED, null, null),
                        ISVNEventListener.UNKNOWN);
                continue;
            }
            svnEvent(SVNEventFactory.createLockEvent(url, SVNEventAction.UNLOCKED, null, null),
                    ISVNEventListener.UNKNOWN);
        }
    }
    
    public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!(revision == null || !revision.isValid() || revision == SVNRevision.WORKING)) {
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNRevision wcRevision = null;
            String url = null;
            try {
                wcAccess.open(true, false);
                url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                if (url == null) {
                    SVNErrorManager.error("svn: '" + path.getAbsolutePath() + "' has no URL");
                }
                wcRevision = SVNRevision.parse(wcAccess.getTargetEntryProperty(SVNProperty.REVISION));
            } finally {
                wcAccess.close(true, false);
            }
            doInfo(url, wcRevision, revision, recursive, handler);
            return;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            collectInfo(wcAccess.getAnchor(), wcAccess.getTargetName(), recursive, handler);
        } finally {
            wcAccess.close(true, recursive);
        }
    }
    
    public void doInfo(String url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        url = validateURL(url);
        url = getURL(url, pegRevision, revision);
        long revNum = getRevisionNumber(url, revision);

        SVNRepository repos = createRepository(url);
        SVNDirEntry rootEntry = repos.info("", revNum);
        if (rootEntry == null || rootEntry.getKind() == SVNNodeKind.NONE) {
            SVNErrorManager.error("'" + url + "' non-existent in revision " + revNum);
        }
        String reposRoot = repos.getRepositoryRoot();
        String reposUUID = repos.getRepositoryUUID();
        // 1. get locks for this dir and below.
        SVNLock[] locks = null;
        try {
            locks = repos.getLocks("");
        } catch (SVNException e) {
            // may be not supported.
            locks = new SVNLock[0];
        }
        locks = locks == null ? new SVNLock[0] : locks;
        Map locksMap = new HashMap();
        for (int i = 0; i < locks.length; i++) {
            SVNLock lock = locks[i];
            locksMap.put(lock.getPath(), lock);
        }
        String fullPath = SVNRepositoryLocation.parseURL(PathUtil.decode(url)).getPath();
        String rootPath = fullPath.substring(reposRoot.length());
        if (!rootPath.startsWith("/")) {
            rootPath = "/" + rootPath;
        }
        reposRoot = PathUtil.append(url.substring(0, url.length() - fullPath.length()), reposRoot);
        collectInfo(repos, rootEntry, SVNRevision.create(revNum), rootPath, reposRoot, reposUUID, url, locksMap, recursive, handler);
    }

    public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(path, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    public SVNInfo doInfo(String url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(url, pegRevision, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }
    
    private static void collectInfo(SVNDirectory dir, String name, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        try {
            if (entry != null && !entry.isHidden()) {
                if (entry.isFile()) {
                    // child file
                    File file = dir.getFile(name, false);
                    handler.handleInfo(SVNInfo.createInfo(file, entry));
                    return;
                } else if (entry.isDirectory() && !"".equals(name)) {
                    // child dir
                    collectInfo(dir, "", recursive, handler);
                    return;
                } else if ("".equals(name)) {
                    // report root.
                    handler.handleInfo(SVNInfo.createInfo(dir.getRoot(), entry));
                }
              
                if (recursive) {
                    for (Iterator ents = entries.entries(); ents.hasNext();) {
                        SVNEntry childEntry = (SVNEntry) ents.next();
                        if ("".equals(childEntry.getName())) {
                            continue;
                        }
                        if (entry.isDirectory()) {
                            SVNDirectory childDir = dir.getChildDirectory(childEntry.getName());
                            if (childDir != null) {
                                collectInfo(childDir, "", recursive, handler);
                            }
                        } else if (entry.isFile()) {
                            handler.handleInfo(SVNInfo.createInfo(dir.getFile(childEntry.getName(), false), entry));
                        }
                    }
                }
            } 
        } finally {
            entries.close();
        }
        
    }
    
    private static void collectInfo(SVNRepository repos, SVNDirEntry entry, SVNRevision rev, String path, String root, String uuid, String url, Map locks, boolean recursive,
            ISVNInfoHandler handler) throws SVNException {
        String rootPath = repos.getLocation().getPath();
        rootPath = PathUtil.decode(rootPath);
        String displayPath = PathUtil.append(repos.getRepositoryRoot(), path).substring(rootPath.length());
        if ("".equals(displayPath) || "/".equals(displayPath)) {
            displayPath = path;
        }
        displayPath = PathUtil.removeLeadingSlash(displayPath);
        handler.handleInfo(SVNInfo.createInfo(displayPath, root, uuid, url, rev, entry, (SVNLock) locks.get(path)));
        if (entry.getKind() == SVNNodeKind.DIR && recursive) {
            Collection children = repos.getDir(path, rev.getNumber(), null, new ArrayList());
            for (Iterator ents = children.iterator(); ents.hasNext();) {
                SVNDirEntry child = (SVNDirEntry) ents.next();
                String childURL = PathUtil.append(url, PathUtil.encode(child.getName()));
                collectInfo(repos, child, rev, PathUtil.append(path, child.getName()), root, uuid, childURL, locks,
                        recursive,
                        handler);                
            }
        }
    }
    
    // add, del@path, revert, resolved, *lock, *unlock, *info, +cleanup -> "wc" client
    
    // copy, move -> "copy" client
    // log, cat, blame, ls -> "repos" client
    // commit, mkdir, import, del@url -> "commit" client
    // status -> "status" client
    // export, update, switch -> "update" client
    // diff, merge -> "diff" client
    
    // (?) ps,pg,pe,pl -> "prop" client
    
    private static class LockInfo {
        
        public LockInfo(File file, SVNRevision rev) {
            myFile = file;
            myRevision = rev;
        }

        public LockInfo(File file, String token) {
            myFile = file;
            myToken = token;
        }
        
        private File myFile;
        private SVNRevision myRevision;
        private String myToken;
    }
}
