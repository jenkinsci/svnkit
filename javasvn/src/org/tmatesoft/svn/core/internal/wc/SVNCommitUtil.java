package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 22:00:36
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitUtil {

    public static void driveCommitEditor(ISVNCommitPathHandler handler, Collection paths, ISVNEditor editor, long revision) throws SVNException {
        if (paths == null || paths.isEmpty() || handler == null || editor == null) {
            return;
        }
        String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        Arrays.sort(pathsArray);
        int index = 0;
        String lastPath = null;
        if ("".equals(pathsArray[index])) {
            handler.handleCommitPath("", editor);
            lastPath = pathsArray[index];
            index++;
        } else {
            editor.openRoot(revision);
        }
        DebugLog.log("driver: paths count: " + pathsArray.length);
        for (int i = 0; i < pathsArray.length; i++) {
            DebugLog.log("driver: commit path: " + pathsArray[i]);
        }
        for (; index < pathsArray.length; index++) {
            String commitPath = pathsArray[index];
            DebugLog.log("driver: processing path: " + commitPath);
            String commonAncestor = lastPath == null || "".equals(lastPath) ? "" /* root or first path */ :
                    SVNPathUtil.getCommonPathAncestor(commitPath, lastPath);
            if (lastPath != null) {
                while(!lastPath.equals(commonAncestor)) {
                    editor.closeDir();
                    if (lastPath.lastIndexOf('/') >= 0) {
                        lastPath = lastPath.substring(0, lastPath.lastIndexOf('/'));
                    } else {
                        lastPath = "";
                    }
                }
            }
            DebugLog.log("driver: last path: " + lastPath);
            DebugLog.log("driver: common ancestor: " + commonAncestor);
            String relativeCommitPath = commitPath.substring(commonAncestor.length());
            if (relativeCommitPath.startsWith("/")) {
                relativeCommitPath = relativeCommitPath.substring(1);
            }
            DebugLog.log("driver: relative commit path: " + relativeCommitPath);

            for(StringTokenizer tokens = new StringTokenizer(relativeCommitPath, "/"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                commonAncestor = "".equals(commonAncestor) ? token : commonAncestor + "/" + token;
                if (!commonAncestor.equals(commitPath)) {
                    DebugLog.log("driver: open dir: " + commonAncestor);
                    editor.openDir(commonAncestor, revision);
                } else {
                    break;
                }
            }
            boolean closeDir = handler.handleCommitPath(commitPath, editor);
            if (closeDir) {
                lastPath = commitPath;
            } else {
                lastPath = PathUtil.removeTail(commitPath);
                if (PathUtil.isEmpty(lastPath)) {
                    lastPath = "";
                }
            }
            DebugLog.log("driver: last open path: " + lastPath);
        }
        while(lastPath != null && !"".equals(lastPath)) {
            editor.closeDir();
            lastPath = lastPath.lastIndexOf('/') >= 0 ? lastPath.substring(0, lastPath.lastIndexOf('/')) : "";
        }
    }

    public static SVNWCAccess createCommitWCAccess(File[] paths, boolean recursive, Collection relativePaths) throws SVNException {
        File wcRoot = null;
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            File newWCRoot = SVNWCUtil.getWorkingCopyRoot(path, true);
            if (wcRoot != null && !wcRoot.equals(newWCRoot)) {
                SVNErrorManager.error("svn: commit targets should belong to the same working copy");
            }
            wcRoot = newWCRoot;
        }
        String[] validatedPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File file = paths[i];
            validatedPaths[i] = SVNPathUtil.validateFilePath(file.getAbsolutePath());
        }

        String rootPath = SVNPathUtil.condencePaths(validatedPaths, relativePaths, recursive);
        if (rootPath == null) {
            return null;
        }
        File baseDir = new File(rootPath);
        Collection dirsToLock = new TreeSet(); // relative paths to lock.
        Collection dirsToLockRecursively = new TreeSet(); // relative paths to lock.
        boolean lockAll = false;
        if (relativePaths.isEmpty()) {
            String target = getTargetName(new File(rootPath));
            if (!"".equals(target)) {
                // we will have to lock target as well, not only base dir.
                SVNFileType targetType = SVNFileType.getType(new File(rootPath));
                relativePaths.add(target);
                if (targetType == SVNFileType.DIRECTORY) {
                    if (recursive) {
                        dirsToLockRecursively.add(target);
                    } else {
                        dirsToLock.add(target);
                    }
                }
                baseDir = baseDir.getParentFile();
            } else {
                lockAll = true;
            }
        } else {
            baseDir = adjustRelativePaths(baseDir, relativePaths);
            // there are multiple paths.
            DebugLog.log("targets : " + relativePaths);
            DebugLog.log("base dir: " + baseDir);
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                String targetPath = (String) targets.next();
                File targetFile = new File(baseDir, targetPath);
                String target = getTargetName(targetFile);
                if (!"".equals(target)) {
                    SVNFileType targetType = SVNFileType.getType(targetFile);
                    if (targetType == SVNFileType.DIRECTORY) {
                        if (recursive) {
                            dirsToLockRecursively.add(targetPath);
                        } else {
                            dirsToLock.add(targetPath);
                        }
                    }
                }
                // now lock all dirs from anchor to base dir (non-recursive).
                while(targetFile != null && !baseDir.equals(targetFile) && !PathUtil.isEmpty(targetPath)) {
                    dirsToLock.add(targetPath);
                    targetFile = targetFile.getParentFile();
                    targetPath = PathUtil.removeTail(targetPath);
                }
            }
        }
        SVNDirectory anchor = new SVNDirectory(null, "", baseDir);
        SVNWCAccess baseAccess = new SVNWCAccess(anchor, anchor, "");
        if (!recursive) {
            SVNStatusClient statusClient = new SVNStatusClient();
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                String targetPath = (String) targets.next();
                File targetFile = new File(baseDir, targetPath);
                if (SVNFileType.getType(targetFile) == SVNFileType.DIRECTORY) {
                    try {
                        SVNStatus status = statusClient.doStatus(targetFile, false);
                        if (status != null &&
                                (status.getContentsStatus() == SVNStatusType.STATUS_DELETED || status.getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                            SVNErrorManager.error("svn: Cannot non-recusively commit a directory deletion");
                        }
                    } catch (SVNException e) {
                        SVNErrorManager.error(0, e);
                    }
                }
            }
        }
        try {
            if (lockAll) {
                baseAccess.open(true, recursive);
            } else {
                baseAccess.open(true, false);
                removeRedundantPaths(dirsToLockRecursively, dirsToLock);
                for (Iterator nonRecusivePaths = dirsToLock.iterator(); nonRecusivePaths.hasNext();) {
                    String path = (String) nonRecusivePaths.next();
                    File pathFile = new File(baseDir, path);
                    baseAccess.addDirectory(path, pathFile, false, true);
                }
                for (Iterator recusivePaths = dirsToLockRecursively.iterator(); recusivePaths.hasNext();) {
                    String path = (String) recusivePaths.next();
                    File pathFile = new File(baseDir, path);
                    baseAccess.addDirectory(path, pathFile, true, true);
                }
            }
        } catch (SVNException e) {
            baseAccess.close(true);
            throw e;
        }

        return baseAccess;
    }

    public static SVNCommitItem[] harvestCommitables(SVNWCAccess baseAccess, Collection paths, Map lockTokens, boolean justLocked, boolean recursive) throws SVNException {
        Map commitables = new TreeMap();
        Collection danglers = new HashSet();
        Iterator targets = paths.iterator();

        do {
            String target = targets.hasNext() ? (String) targets.next() : "";
            // get entry for target
            File targetFile = new File(baseAccess.getAnchor().getRoot(), target);
            String targetName = "".equals(target) ? "" : PathUtil.tail(target);
            String parentPath = "".equals(target) ? "" : PathUtil.removeTail(target);
            if (parentPath.startsWith("/")) {
                parentPath = PathUtil.removeLeadingSlash(parentPath);
            }
            SVNDirectory dir = baseAccess.getDirectory(parentPath);
            SVNEntry entry = dir == null ? null : dir.getEntries().getEntry(targetName, false);
            String url = null;
            if (entry == null) {
                SVNErrorManager.error("svn: '" + targetFile + "' is not under version control");
            } else if (entry.getURL() == null) {
                SVNErrorManager.error("svn: '" + targetFile + "' has no URL");
            } else {
                url = entry.getURL();
            }
            if (entry != null && (entry.isScheduledForAddition() || entry.isScheduledForReplacement())) {
                // get parent (for file or dir-> get ""), otherwise open parent dir and get "".
                SVNEntry parentEntry;
                if (!"".equals(targetName)) {
                    parentEntry = dir.getEntries().getEntry("", false);
                } else {
                    File parentFile = targetFile.getParentFile();
                    SVNWCAccess parentAccess = SVNWCAccess.create(parentFile);
                    parentEntry = parentAccess.getTarget().getEntries().getEntry("", false);
                }
                if (parentEntry == null) {
                    SVNErrorManager.error("svn: '" + targetFile + "' is scheduled for addition within unversioned parent");
                } else if (parentEntry.isScheduledForAddition() || parentEntry.isScheduledForReplacement()) {
                    danglers.add(targetFile.getParentFile());
                }
            }
            if (entry != null && entry.isCopied() && entry.getSchedule() == null) {
                SVNErrorManager.error("svn: Entry for '" + targetFile + "' is marked as 'copied' but is not itself scheduled\n" +
                                      "for addition.  Perhaps you're committing a target that is\n" +
                                      "inside an unversioned (or not-yet-versioned) directory?");
            }
            DebugLog.log("collecting commitables for " + targetFile);
            harvestCommitables(commitables, dir, targetFile, null, entry, url, null, false, false, justLocked, lockTokens, recursive);
        } while(targets.hasNext());


        for (Iterator ds = danglers.iterator(); ds.hasNext();) {
            File file = (File) ds.next();
            if (!commitables.containsKey(file)) {
                SVNErrorManager.error("svn: '" + file + "' is not under version control\n" +
                                      "and is not part of the commit, \n" +
                                      "yet its child is part of the commit");
            }
        }
        return (SVNCommitItem[]) commitables.values().toArray(new SVNCommitItem[commitables.values().size()]);
    }

    public static String translateCommitables(SVNCommitItem[] items, Map decodedPaths) throws SVNException {
        Map itemsMap = new TreeMap();
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem item = items[i];
            if (itemsMap.containsKey(item.getURL())) {
                SVNCommitItem oldItem = (SVNCommitItem) itemsMap.get(item.getURL());
                SVNErrorManager.error("svn: Cannot commit both '" + item.getFile() + "' and '" + oldItem.getFile() + "' as they refer to the same URL");
            }
            itemsMap.put(item.getURL(), item);
        }
        Iterator urls = itemsMap.keySet().iterator();
        String baseURL = (String) urls.next();
        while(urls.hasNext()) {
            String url = (String) urls.next();
            baseURL = SVNPathUtil.getCommonURLAncestor(baseURL, url);
        }
        if (itemsMap.containsKey(baseURL)) {
            SVNCommitItem root = (SVNCommitItem) itemsMap.get(baseURL);
            if (root.getKind() != SVNNodeKind.DIR) {
                baseURL = PathUtil.removeTail(baseURL);
            } else if (root.getKind() == SVNNodeKind.DIR && (root.isAdded() || root.isDeleted() || root.isCopied() || root.isLocked())) {
                baseURL = PathUtil.removeTail(baseURL);
            }
        }
        urls = itemsMap.keySet().iterator();
        while(urls.hasNext()) {
            String url = (String) urls.next();
            SVNCommitItem item = (SVNCommitItem) itemsMap.get(url);
            String realPath = url.substring(baseURL.length());
            if (realPath.startsWith("/")) {
                realPath = PathUtil.removeLeadingSlash(realPath);
            }
            decodedPaths.put(PathUtil.decode(realPath), item);
        }
        return baseURL;
    }

    public static Map translateLockTokens(Map lockTokens, String baseURL) {
        Map translatedLocks = new TreeMap();
        for (Iterator urls = lockTokens.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            String token = (String) lockTokens.get(url);
            url = url.substring(baseURL.length());
            if (url.startsWith("/")) {
                url = PathUtil.removeLeadingSlash(url);
                translatedLocks.put(PathUtil.decode(url), token);
            }
        }
        lockTokens.clear();
        lockTokens.putAll(translatedLocks);
        return lockTokens;
    }

    public static void harvestCommitables(Map commitables, SVNDirectory dir, File path, SVNEntry parentEntry, SVNEntry entry,
                                           String url, String copyFromURL, boolean copyMode, boolean addsOnly, boolean justLocked,
                                           Map lockTokens, boolean recursive) throws SVNException {
        if (commitables.containsKey(path)) {
            return;
        }
        long cfRevision = entry.getCopyFromRevision();
        String cfURL = null;
        if (entry.getKind() != SVNNodeKind.DIR && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Unknown entry kind for '" + path + "'");
        }
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorManager.error("svn: Unknown entry kind for '" + path + "'");
        }
        boolean specialFile = fileType == SVNFileType.SYMLINK;
        if (specialFile != (dir.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.SPECIAL) != null)) {
            SVNErrorManager.error("svn: Entry '" + path + "' has unexpectedly changed special status");
        }
        boolean propConflicts;
        boolean textConflicts = false;
        SVNEntries entries = null;
        if (entry.getKind() == SVNNodeKind.DIR) {
            SVNDirectory childDir = dir.getChildDirectory(entry.getName());
            if (childDir != null && childDir.getEntries() != null) {
                entries = childDir.getEntries();
                if (entries.getEntry("", false) != null) {
                    entry = entries.getEntry("", false);
                    dir = childDir;
                }
                propConflicts = entry.getPropRejectFile() != null;
            } else {
                propConflicts = entry.getPropRejectFile() != null;
            }
        } else {
            propConflicts = entry.getPropRejectFile() != null;
            textConflicts = entry.getConflictOld() != null || entry.getConflictNew() != null || entry.getConflictWorking() != null;
        }
        if (propConflicts || textConflicts) {
            SVNErrorManager.error("svn: Aborting commit: '" + path + "' remains in conflict");
        }
        if (entry.getURL() != null && !copyMode) {
            url = entry.getURL();
        }
        boolean commitDeletion = !addsOnly && (entry.isScheduledForDeletion() || entry.isScheduledForReplacement());
        boolean commitAddition = false;
        boolean commitCopy = false;
        if (entry.isScheduledForAddition() || entry.isScheduledForReplacement()) {
            commitAddition = true;
            if (entry.getCopyFromURL() != null) {
                cfURL = entry.getCopyFromURL();
                addsOnly = false;
                commitCopy = true;
            } else {
                addsOnly = true;
            }
        }
        if ((entry.isCopied() || copyMode) && entry.getSchedule() == null) {
            long parentRevision = entry.getRevision() - 1;
            if (!SVNWCUtil.isWorkingCopyRoot(path, true)) {
                if (parentEntry != null) {
                    parentRevision = parentEntry.getRevision();
                }

            } else if (!copyMode) {
                SVNErrorManager.error("svn: Did not expect '" + path + "' to be a working copy root");
            }
            if (parentRevision != entry.getRevision()) {
                commitAddition = true;
                commitCopy = true;
                addsOnly = false;
                cfRevision = entry.getRevision();
                if (copyMode) {
                    cfURL = entry.getURL();
                } else if (copyFromURL != null) {
                    cfURL = copyFromURL;
                } else {
                    SVNErrorManager.error("svn: Commit item '" + path + "' has copy flag but no copyfrom URL");
                }
            }
        }
        boolean textModified = false;
        boolean propsModified = false;
        boolean commitLock;

        if (commitAddition) {
            SVNProperties props = dir.getProperties(entry.getName(), false);
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(), false);
            Map propDiff = baseProps.compareTo(props);
            boolean eolChanged = textModified = propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE);
            if (entry.getKind() == SVNNodeKind.FILE) {
                if (commitCopy) {
                    textModified = propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE);
                    if (!textModified) {
                        textModified = dir.hasTextModifications(entry.getName(), eolChanged);
                    }
                } else {
                    textModified = true;
                }
            }
            propsModified = propDiff != null && !propDiff.isEmpty();
        } else if (!commitDeletion) {
            SVNProperties props = dir.getProperties(entry.getName(), false);
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(), false);
            Map propDiff = baseProps.compareTo(props);
            boolean eolChanged = textModified = propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE);
            propsModified = propDiff != null && !propDiff.isEmpty();
            if (entry.getKind() == SVNNodeKind.FILE) {
                textModified = dir.hasTextModifications(entry.getName(), eolChanged);
            }
        }

        commitLock = entry.getLockToken() != null &&
                (justLocked || textModified || propsModified || commitDeletion || commitAddition || commitCopy);

        if (commitAddition || commitDeletion || textModified || propsModified || commitCopy || commitLock) {
            SVNCommitItem item = new SVNCommitItem(path, url, cfURL, entry.getKind(),
                    cfURL != null ? SVNRevision.create(cfRevision) : SVNRevision.create(entry.getRevision()),
                    commitAddition, commitDeletion, propsModified, textModified, commitCopy, commitLock);
            String itemPath = dir.getPath();
            if ("".equals(itemPath)) {
                itemPath += entry.getName();
            } else if (!"".equals(entry.getName())) {
                itemPath += "/" + entry.getName();
            }
            item.setPath(itemPath);
            commitables.put(path, item);
            if (lockTokens != null && entry.getLockToken() != null) {
                lockTokens.put(url, entry.getLockToken());
            }
        }
        if (entries != null && recursive && (commitAddition || !commitDeletion)) {
            // recurse.
            for(Iterator ents = entries.entries(false); ents.hasNext();) {
                SVNEntry currentEntry = (SVNEntry) ents.next();
                if ("".equals(currentEntry.getName())) {
                    continue;
                }
                String currentCFURL = cfURL != null ? cfURL : copyFromURL;
                if (currentCFURL != null) {
                    currentCFURL = PathUtil.append(currentCFURL, PathUtil.encode(currentEntry.getName()));
                }
                String currentURL = currentEntry.getURL();
                if (copyMode || entry.getURL() == null) {
                    currentURL = PathUtil.append(url, PathUtil.encode(currentEntry.getName()));
                }
                File currentFile = dir.getFile(currentEntry.getName(), false);
                SVNDirectory childDir;
                if (currentEntry.getKind() == SVNNodeKind.DIR) {
                    childDir = dir.getChildDirectory(currentEntry.getName());
                    if (childDir == null) {
                        SVNFileType currentType = SVNFileType.getType(currentFile);
                        if (currentType == SVNFileType.NONE && entry.isScheduledForDeletion()) {
                            SVNCommitItem item = new SVNCommitItem(currentFile, currentURL, null, currentEntry.getKind(), SVNRevision.UNDEFINED,
                                    false, true, false, false, false, false);
                            item.setPath(PathUtil.append(dir.getPath(), currentEntry.getName()));
                            commitables.put(path, item);
                            continue;
                        }
                        // error.
                        SVNErrorManager.error("svn: Aborting commit, invalid entry '" + currentFile + "'");
                    }
                }
                harvestCommitables(commitables, dir, currentFile, entry, currentEntry, currentURL,
                                    currentCFURL, copyMode, addsOnly, justLocked, lockTokens, true);

            }
        }
        if (lockTokens != null && entry.getKind() == SVNNodeKind.DIR && commitDeletion) {
            // harvest lock tokens for deleted items.
            collectLocks(dir, lockTokens);
        }
    }

    private static void collectLocks(SVNDirectory dir, Map lockTokens) throws SVNException {
        SVNEntries entries = dir.getEntries();
        if (entries == null) {
            return;
        }
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.getURL() != null && entry.getLockToken() != null) {
                lockTokens.put(entry.getURL(), entry.getLockToken());
            }
            if (!"".equals(entry.getName()) && entry.isDirectory()) {
                SVNDirectory child = dir.getChildDirectory(entry.getName());
                if (child != null) {
                    collectLocks(child, lockTokens);
                }
            }
        }
        entries.close();
    }

    private static void removeRedundantPaths(Collection dirsToLockRecursively, Collection dirsToLock) {
        for(Iterator paths = dirsToLock.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            if (dirsToLockRecursively.contains(path)) {
                paths.remove();
            } else {
                for (Iterator recPaths = dirsToLockRecursively.iterator(); recPaths.hasNext();) {
                    String existingPath = (String) recPaths.next();
                    if (path.startsWith(existingPath + "/")) {
                        paths.remove();
                        break;
                    }
                }
            }
        }
    }

    private static File adjustRelativePaths(File rootFile, Collection relativePaths) throws SVNException {
        if (relativePaths.contains("")) {
            String targetName = getTargetName(rootFile);
            if (!"".equals(targetName) && rootFile.getParentFile() != null) {
                rootFile = rootFile.getParentFile();
            }
            Collection result = new TreeSet();
            for (Iterator paths = relativePaths.iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                path = "".equals(path) ? targetName : PathUtil.append(targetName, path);
                result.add(path);
            }
            relativePaths.clear();
            relativePaths.addAll(result);
        }
        return rootFile;
    }

    private static String getTargetName(File file) throws SVNException {
        DebugLog.log("creating wc access for: " + file);
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        return wcAccess.getTargetName();
    }
}
