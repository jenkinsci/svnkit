package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNCommitter;
import org.tmatesoft.svn.core.internal.wc.SVNCommitMediator;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNImportMediator;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Collection;
import java.util.HashSet;

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

    public SVNCommitClient(final ISVNCredentialsProvider credentialsProvider, ISVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNCommitClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options, ISVNEventListener eventDispatcher) {
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

    public SVNCommitInfo doDelete(String[] urls, String commitMessage) throws SVNException {
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        for (int i = 0; i < urls.length; i++) {
            urls[i] = validateURL(urls[i]);
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
            commitItems[i] = new SVNCommitItem(null, PathUtil.append(rootURL, path), null, null, null, false, true, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
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
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.deleteEntry(commitPath, -1);
                return false;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(deleter, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    public SVNCommitInfo doMkDir(String[] urls, String commitMessage) throws SVNException {
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        for (int i = 0; i < urls.length; i++) {
            urls[i] = validateURL(urls[i]);
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
            commitItems[i] = new SVNCommitItem(null, PathUtil.append(rootURL, path), null, null, null, true, false, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
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
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.addDir(commitPath, null, -1);
                return true;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(creater, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    public SVNCommitInfo doImport(File path, String dstURL, String commitMessage, boolean recursive) throws SVNException {
        dstURL = validateURL(dstURL);
        // first find dstURL root.
        SVNRepository repos = null;
        String rootURL = dstURL;
        SVNFileType srcKind = SVNFileType.getType(path);
        List newPaths = new ArrayList();
        while(true) {
            try {
                repos = createRepository(rootURL); // may throw an exception.
            } catch (SVNException e) {
                SVNErrorManager.error("svn: invalid URL '" + dstURL + "'");
            }
            if (repos == null) {
                SVNErrorManager.error("svn: invalid URL '" + dstURL + "'");
            } else if (repos.checkPath("", -1) == SVNNodeKind.NONE) {
                newPaths.add(PathUtil.decode(PathUtil.tail(rootURL)));
                rootURL = PathUtil.removeTail(rootURL);
            } else {
                break;
            }
        }
        if (newPaths.isEmpty() && (srcKind == SVNFileType.FILE || srcKind == SVNFileType.SYMLINK)) {
            SVNErrorManager.error("svn: Path '" + dstURL + "' already exists");
        }
        if (newPaths.contains(".svn")) {
            SVNErrorManager.error("svn: '.svn' is a reserved name and cannot be imported");
        }
        SVNCommitItem[] items = new SVNCommitItem[1];
        items[0] =
                new SVNCommitItem(path, dstURL, null, srcKind == SVNFileType.DIRECTORY ? SVNNodeKind.DIR : SVNNodeKind.FILE,
                        SVNRevision.UNDEFINED, true, false, false, false, false, false);
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, new SVNImportMediator(srcKind == SVNFileType.DIRECTORY ? path : path.getParentFile()));
        String filePath = "";
        if (srcKind != SVNFileType.DIRECTORY) {
            filePath = (String) newPaths.remove(0);
            for(int i = 0; i < newPaths.size(); i++) {
                String newDir = (String) newPaths.get(i);
                filePath = newDir + "/" + filePath;
            }
        }
        commitEditor.openRoot(-1);
        String newDirPath = null;
        for(int i = newPaths.size() - 1; i >= 0; i--) {
            newDirPath = newDirPath == null ? (String) newPaths.get(i) : PathUtil.append(newDirPath, (String) newPaths.get(i));
            commitEditor.addDir(newDirPath, null, -1);
        }
        boolean changed;
        if (srcKind == SVNFileType.DIRECTORY) {
            changed = importDir(path, path, newDirPath, recursive, commitEditor);
        } else {
            changed = importFile(path.getParentFile(), path, srcKind, filePath, commitEditor);
        }
        if (!changed) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException e) {
                //
            }
            return SVNCommitInfo.NULL;
        }
        for(int i = 0; i < newPaths.size(); i++) {
            commitEditor.closeDir();
        }
        SVNCommitInfo info = null;
        try {
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException e1) {
                // inner
            }
            SVNErrorManager.error(0, e);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, boolean force, boolean recursive) throws SVNException {
        SVNCommitPacket packet = doCollectCommitItems(paths, keepLocks, force, recursive);
        try {
            packet = packet.removeSkippedItems();
            return doCommit(packet, keepLocks, commitMessage);
        } finally {
            if (packet != null) {
                packet.dispose();
            }
        }
    }

    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, String commitMessage) throws SVNException {
        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;

        try {
            if (commitPacket == null || commitPacket == SVNCommitPacket.EMPTY) {
                return SVNCommitInfo.NULL;
            }
            DebugLog.log("commit packet: " + commitPacket);
            commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitPacket.getCommitItems());
            if (commitMessage == null) {
                return SVNCommitInfo.NULL;
            }
            Map commitables = new TreeMap();
            String baseURL = SVNCommitUtil.translateCommitables(commitPacket.getCommitItems(), commitables);
            Map lockTokens = SVNCommitUtil.translateLockTokens(commitPacket.getLockTokens(), baseURL);
            DebugLog.log("base URL    : " + baseURL);
            DebugLog.log("commitables : " + commitables);

            SVNRepository repository = createRepository(baseURL);
            SVNCommitMediator mediator = new SVNCommitMediator(commitPacket.getWCAccess(), commitables);
            tmpFiles = mediator.getTmpFiles();
            commitEditor = repository.getCommitEditor(commitMessage, lockTokens, keepLocks, mediator);
            // commit.
            SVNWCAccess wcAccess = commitPacket.getWCAccess();
            wcAccess.setEventDispatcher(getEventDispatcher());
            String repositoryRoot = repository.getRepositoryRoot(true);
            info = SVNCommitter.commit(commitPacket.getWCAccess(), mediator.getTmpFiles(), commitables, repositoryRoot, commitEditor);
            // update wc.
            Collection processedItems = new HashSet();
            for (Iterator urls = commitables.keySet().iterator(); urls.hasNext();) {
                String url = (String) urls.next();
                SVNCommitItem item = (SVNCommitItem) commitables.get(url);
                String path = item.getPath();
                SVNDirectory dir;
                String target;

                if (item.getKind() == SVNNodeKind.DIR) {
                    dir = wcAccess.getDirectory(path);
                    target = "";
                } else {
                    dir = wcAccess.getDirectory(PathUtil.removeTail(path));
                    target = PathUtil.tail(path);
                }
                if (dir == null) {
                    if (hasProcessedParents(processedItems, path)) {
                        processedItems.add(path);
                        continue;
                    }
                    if (item.isDeleted() && item.getKind() == SVNNodeKind.DIR) {
                        String parentPath = "".equals(path) ? null : PathUtil.removeTail(path);
                        String nameInParent = "".equals(path) ? null : PathUtil.tail(path);
                        if (parentPath != null) {
                            SVNDirectory parentDir = wcAccess.getDirectory(parentPath);
                            if (parentDir != null) {
                                SVNEntry entryInParent = parentDir.getEntries().getEntry(nameInParent, true);
                                if (entryInParent != null) {
                                    entryInParent.unschedule();
                                    entryInParent.setDeleted(true);
                                    parentDir.getEntries().save(false);
                                }
                            }
                        }
                        processedItems.add(path);
                        continue;
                    }

                }
                SVNEntry entry = dir.getEntries().getEntry(target, true);
                if (entry == null && hasProcessedParents(processedItems, path)) {
                    processedItems.add(path);
                    continue;
                }
                boolean recurse = false;
                if (item.isAdded() && item.getCopyFromURL() != null && item.getKind() == SVNNodeKind.DIR) {
                    recurse = true;
                }
                boolean removeLock = !keepLocks && item.isLocked();
                // update entry in dir.
                Map wcPropChanges = mediator.getWCProperties(item);
                dir.commit(target, info, wcPropChanges, removeLock,  recurse);
                processedItems.add(path);
            }
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }
        } finally {
            if (info == null && commitEditor != null) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    //
                }
            }
            if (tmpFiles != null) {
                for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                    File file = (File) files.next();
                    file.delete();
                }
            }
            if (commitPacket != null) {
                commitPacket.dispose();
            }
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive) throws SVNException {
        Set targets = new TreeSet();
        SVNWCAccess wcAccess = SVNCommitUtil.createCommitWCAccess(paths, recursive, force, targets);
        try {
            Map lockTokens = new HashMap();
            SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(wcAccess, targets, lockTokens, !keepLocks, recursive);
            boolean hasModifications = false;
            for (int i = 0; commitItems != null && i < commitItems.length; i++) {
                SVNCommitItem commitItem = commitItems[i];
                if (commitItem.isAdded() || commitItem.isDeleted() || commitItem.isContentsModified() || commitItem.isPropertiesModified() ||
                        commitItem.isCopied()) {
                    hasModifications = true;
                    break;
                }
            }
            if (!hasModifications) {
                wcAccess.close(true);
                return SVNCommitPacket.EMPTY;
            }
            return new SVNCommitPacket(wcAccess, commitItems, lockTokens);
        } catch (SVNException e) {
            wcAccess.close(true);
            if (e.getMessage() != null && e.getMessage().startsWith("svn:")) {
                e.setMessage("svn: Commit failed (details follow):\n" + e.getMessage());
            }
            throw e;
        }
    }

    private boolean importDir(File rootFile, File dir, String importPath, boolean recursive, ISVNEditor editor) throws SVNException {
        File[] children = dir.listFiles();
        boolean changed = false;
        DebugLog.log("importing dir: " + dir + " to " + importPath);
        for (int i = 0; children != null && i < children.length; i++) {
            File file = children[i];
            if (".svn".equals(file.getName())) {
                SVNEvent skippedEvent = SVNEventFactory.createSkipEvent(rootFile, file, SVNEventAction.SKIP, SVNNodeKind.NONE);
                svnEvent(skippedEvent, ISVNEventListener.UNKNOWN);
                continue;
            }
            if (getOptions().isIgnored(file.getName())) {
                continue;
            }
            String path = importPath == null ? file.getName() : PathUtil.append(importPath, file.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY && recursive) {
                editor.addDir(path, null, -1);
                changed |= true;
                SVNEvent event = SVNEventFactory.createCommitEvent(rootFile, file, SVNEventAction.COMMIT_ADDED, SVNNodeKind.DIR, null);
                svnEvent(event, ISVNEventListener.UNKNOWN);
                importDir(rootFile, file, path, recursive, editor);
                editor.closeDir();
            } else {
                changed |= importFile(rootFile, file, fileType, path, editor);
            }

        }
        return changed;
    }

    private boolean importFile(File rootFile, File file, SVNFileType fileType, String filePath, ISVNEditor editor) throws SVNException {
        if (fileType == null || fileType == SVNFileType.UNKNOWN) {
            SVNErrorManager.error("svn: unknown or unversionable type for '" + file + "'");
        }
        DebugLog.log("importing file: " + file + " to " + filePath);
        editor.addFile(filePath, null, -1);
        String mimeType = null;
        Map autoProperties = new HashMap();
        if (fileType != SVNFileType.SYMLINK) {
            autoProperties = getOptions().applyAutoProperties(file.getName(), autoProperties);
            if (!autoProperties.containsKey(SVNProperty.MIME_TYPE)) {
                mimeType = SVNFileUtil.detectMimeType(file);
                if (mimeType != null) {
                    autoProperties.put(SVNProperty.MIME_TYPE, mimeType);
                }
            }
            if (!autoProperties.containsKey(SVNProperty.EXECUTABLE) && SVNFileUtil.isExecutable(file)) {
                autoProperties.put(SVNProperty.EXECUTABLE, "");
            }
        } else {
            autoProperties.put(SVNProperty.SPECIAL, "*");
        }
        for (Iterator names = autoProperties.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String value = (String) autoProperties.get(name);
            editor.changeFileProperty(filePath, name, value);
        }
        // send "adding"
        SVNEvent addedEvent = SVNEventFactory.createCommitEvent(rootFile, file, SVNEventAction.COMMIT_ADDED, SVNNodeKind.FILE, mimeType);
        svnEvent(addedEvent, ISVNEventListener.UNKNOWN);
        editor.applyTextDelta(filePath, null);
        // translate and send file.
        String eolStyle = (String) autoProperties.get(SVNProperty.EOL_STYLE);
        String keywords = (String) autoProperties.get(SVNProperty.KEYWORDS);
        boolean special = autoProperties.get(SVNProperty.SPECIAL) != null;
        File tmpFile = null;
        if (eolStyle != null || keywords != null || special) {
            byte[] eolBytes = SVNTranslator.getBaseEOL(eolStyle);
            Map keywordsMap = keywords != null ? SVNTranslator.computeKeywords(keywords, null, null, null, null) : null;
            tmpFile = SVNFileUtil.createUniqueFile(file.getParentFile(), file.getName(), ".tmp");
            SVNTranslator.translate(file, tmpFile, eolBytes, keywordsMap, special, false);
        }
        File importedFile = tmpFile != null ? tmpFile : file;
        String checksum = SVNFileUtil.computeChecksum(importedFile);
        OutputStream os = editor.textDeltaChunk(filePath, SVNDiffWindowBuilder.createReplacementDiffWindow(importedFile.length()));
        InputStream is = SVNFileUtil.openFileForReading(importedFile);
        int r;
        try {
            while((r = is.read()) >= 0) {
                os.write(r);
            }
        } catch (IOException e) {
            SVNErrorManager.error("svn: IO error while importing file '" + file + "': " + e.getMessage());
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                SVNErrorManager.error("svn: IO error while importing file '" + file + "': " + e.getMessage());
            }
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
        editor.textDeltaEnd(filePath);
        editor.closeFile(filePath, checksum);
        return true;
    }

    private static boolean hasProcessedParents(Collection paths, String path) {
        path = PathUtil.removeTail(path);
        if (PathUtil.isEmpty(path)) {
            path = "";
        }
        if (paths.contains(path)) {
            return true;
        }
        if (PathUtil.isEmpty(path)) {
            return false;
        }
        return hasProcessedParents(paths, path);
    }

}
