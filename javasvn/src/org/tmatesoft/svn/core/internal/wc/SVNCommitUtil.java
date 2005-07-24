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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNCommitUtil {

    public static void driveCommitEditor(ISVNCommitPathHandler handler,
            Collection paths, ISVNEditor editor, long revision)
            throws SVNException {
        if (paths == null || paths.isEmpty() || handler == null
                || editor == null) {
            return;
        }
        String[] pathsArray = (String[]) paths
                .toArray(new String[paths.size()]);
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
        for (; index < pathsArray.length; index++) {
            String commitPath = pathsArray[index];
            String commonAncestor = lastPath == null || "".equals(lastPath) ? "" 
                    : SVNPathUtil.getCommonPathAncestor(commitPath, lastPath);
            if (lastPath != null) {
                while (!lastPath.equals(commonAncestor)) {
                    editor.closeDir();
                    if (lastPath.lastIndexOf('/') >= 0) {
                        lastPath = lastPath.substring(0, lastPath
                                .lastIndexOf('/'));
                    } else {
                        lastPath = "";
                    }
                }
            }
            String relativeCommitPath = commitPath.substring(commonAncestor
                    .length());
            if (relativeCommitPath.startsWith("/")) {
                relativeCommitPath = relativeCommitPath.substring(1);
            }

            for (StringTokenizer tokens = new StringTokenizer(
                    relativeCommitPath, "/"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                commonAncestor = "".equals(commonAncestor) ? token
                        : commonAncestor + "/" + token;
                if (!commonAncestor.equals(commitPath)) {
                    editor.openDir(commonAncestor, revision);
                } else {
                    break;
                }
            }
            boolean closeDir = handler.handleCommitPath(commitPath, editor);
            if (closeDir) {
                lastPath = commitPath;
            } else {
                lastPath = SVNPathUtil.removeTail(commitPath);
            }
        }
        while (lastPath != null && !"".equals(lastPath)) {
            editor.closeDir();
            lastPath = lastPath.lastIndexOf('/') >= 0 ? lastPath.substring(0,
                    lastPath.lastIndexOf('/')) : "";
        }
    }

    public static SVNWCAccess createCommitWCAccess(File[] paths,
            boolean recursive, boolean force, Collection relativePaths,
            SVNStatusClient statusClient)
            throws SVNException {
        File wcRoot = null;
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            File newWCRoot = SVNWCUtil.getWorkingCopyRoot(path, true);
            if (wcRoot != null && !wcRoot.equals(newWCRoot)) {
                SVNErrorManager
                        .error("svn: commit targets should belong to the same working copy");
            }
            wcRoot = newWCRoot;
        }
        String[] validatedPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File file = paths[i];
            validatedPaths[i] = SVNPathUtil.validateFilePath(file
                    .getAbsolutePath());
        }

        String rootPath = SVNPathUtil.condencePaths(validatedPaths,
                relativePaths, recursive);
        if (rootPath == null) {
            return null;
        }
        File baseDir = new File(rootPath);
        Collection dirsToLock = new TreeSet(); // relative paths to lock.
        Collection dirsToLockRecursively = new TreeSet(); // relative paths to
                                                            // lock.
        boolean lockAll = false;
        if (relativePaths.isEmpty()) {
            String target = getTargetName(new File(rootPath));
            if (!"".equals(target)) {
                // we will have to lock target as well, not only base dir.
                SVNFileType targetType = SVNFileType
                        .getType(new File(rootPath));
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
                targetFile = targetFile.getParentFile();
                targetPath = SVNPathUtil.removeTail(targetPath);
                while (targetFile != null && !baseDir.equals(targetFile)
                        && !"".equals(targetPath) && !dirsToLock.contains(targetPath)) {
                    dirsToLock.add(targetPath);
                    targetFile = targetFile.getParentFile();
                    targetPath = SVNPathUtil.removeTail(targetPath);
                }
            }
        }
        SVNDirectory anchor = new SVNDirectory(null, "", baseDir);
        SVNWCAccess baseAccess = new SVNWCAccess(anchor, anchor, "");
        if (!recursive && !force) {
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                String targetPath = (String) targets.next();
                File targetFile = new File(baseDir, targetPath);
                if (SVNFileType.getType(targetFile) == SVNFileType.DIRECTORY) {
                    SVNStatus status = statusClient.doStatus(targetFile,
                            false);
                    if (status != null
                            && (status.getContentsStatus() == SVNStatusType.STATUS_DELETED || status
                                    .getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                        SVNErrorManager
                                .error("svn: Cannot non-recursively commit a directory deletion");
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
                for (Iterator nonRecusivePaths = dirsToLock.iterator(); nonRecusivePaths
                        .hasNext();) {
                    String path = (String) nonRecusivePaths.next();
                    File pathFile = new File(baseDir, path);
                    baseAccess.addDirectory(path, pathFile, false, true);
                }
                for (Iterator recusivePaths = dirsToLockRecursively.iterator(); recusivePaths
                        .hasNext();) {
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

    public static SVNCommitItem[] harvestCommitables(SVNWCAccess baseAccess,
            Collection paths, Map lockTokens, boolean justLocked,
            boolean recursive, boolean force) throws SVNException {
        Map commitables = new TreeMap();
        Collection danglers = new HashSet();
        Iterator targets = paths.iterator();

        do {
            String target = targets.hasNext() ? (String) targets.next() : "";
            // get entry for target
            File targetFile = new File(baseAccess.getAnchor().getRoot(), target);
            String targetName = "".equals(target) ? "" : SVNPathUtil.tail(target);
            String parentPath = SVNPathUtil.removeTail(target);
            SVNDirectory dir = baseAccess.getDirectory(parentPath);
            SVNEntry entry = dir == null ? null : dir.getEntries().getEntry(
                    targetName, false);
            String url = null;
            if (entry == null) {
                SVNErrorManager.error("svn: '" + targetFile + "' is not under version control");
            } else if (entry.getURL() == null) {
                SVNErrorManager.error("svn: '" + targetFile + "' has no URL");
            } else {
                url = entry.getURL();
            }
            if (entry != null
                    && (entry.isScheduledForAddition() || entry
                            .isScheduledForReplacement())) {
                // get parent (for file or dir-> get ""), otherwise open parent
                // dir and get "".
                SVNEntry parentEntry;
                if (!"".equals(targetName)) {
                    parentEntry = dir.getEntries().getEntry("", false);
                } else {
                    File parentFile = targetFile.getParentFile();
                    SVNWCAccess parentAccess = SVNWCAccess.create(parentFile);
                    parentEntry = parentAccess.getTarget().getEntries()
                            .getEntry("", false);
                }
                if (parentEntry == null) {
                    SVNErrorManager
                            .error("svn: '"
                                    + targetFile
                                    + "' is scheduled for addition within unversioned parent");
                } else if (parentEntry.isScheduledForAddition()
                        || parentEntry.isScheduledForReplacement()) {
                    danglers.add(targetFile.getParentFile());
                }
            }
            boolean recurse = recursive;
            if (entry != null && entry.isCopied()
                    && entry.getSchedule() == null) {
                // if commit is forced => we could collect this entry, assuming
                // that its parent is already included into commit
                // it will be later removed from commit anyway.
                if (!force) {
                    SVNErrorManager
                            .error("svn: Entry for '"
                                    + targetFile
                                    + "' is marked as 'copied' but is not itself scheduled\n"
                                    + "for addition.  Perhaps you're committing a target that is\n"
                                    + "inside an unversioned (or not-yet-versioned) directory?");
                } else {
                    // just do not process this item as in case of recursive
                    // commit.
                    continue;
                }
            } else if (entry != null && entry.isCopied()
                    && entry.isScheduledForAddition()) {
                if (force) {
                    recurse = true;
                }
            } else if (entry != null && entry.isScheduledForDeletion()) {
                if (force && !recursive) {
                    // if parent is also deleted -> skip this entry
                    SVNEntry parentEntry;
                    if (!"".equals(targetName)) {
                        parentEntry = dir.getEntries().getEntry("", false);
                    } else {
                        File parentFile = targetFile.getParentFile();
                        SVNWCAccess parentAccess = SVNWCAccess.create(parentFile);
                        parentEntry = parentAccess.getTarget().getEntries()
                                .getEntry("", false);
                    }
                    if (parentEntry != null && parentEntry.isScheduledForDeletion() &&
                            paths.contains(parentPath)) {
                        continue;
                    }
                    recurse = true;
                }
            }
            harvestCommitables(commitables, dir, targetFile, null, entry, url,
                    null, false, false, justLocked, lockTokens, recurse);
        } while (targets.hasNext());

        for (Iterator ds = danglers.iterator(); ds.hasNext();) {
            File file = (File) ds.next();
            if (!commitables.containsKey(file)) {
                SVNErrorManager.error("svn: '" + file
                        + "' is not under version control\n"
                        + "and is not part of the commit, \n"
                        + "yet its child is part of the commit");
            }
        }
        return (SVNCommitItem[]) commitables.values().toArray(
                new SVNCommitItem[commitables.values().size()]);
    }

    public static String translateCommitables(SVNCommitItem[] items,
            Map decodedPaths) throws SVNException {
        Map itemsMap = new TreeMap();
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem item = items[i];
            if (itemsMap.containsKey(item.getURL())) {
                SVNCommitItem oldItem = (SVNCommitItem) itemsMap.get(item
                        .getURL());
                SVNErrorManager.error("svn: Cannot commit both '"
                        + item.getFile() + "' and '" + oldItem.getFile()
                        + "' as they refer to the same URL");
            }
            itemsMap.put(item.getURL(), item);
        }

        Iterator urls = itemsMap.keySet().iterator();
        String baseURL = (String) urls.next();
        while (urls.hasNext()) {
            String url = (String) urls.next();
            baseURL = SVNPathUtil.getCommonURLAncestor(baseURL, url);
        }
        if (itemsMap.containsKey(baseURL)) {
            SVNCommitItem root = (SVNCommitItem) itemsMap.get(baseURL);
            if (root.getKind() != SVNNodeKind.DIR) {
                baseURL = SVNPathUtil.removeTail(baseURL);
            } else if (root.getKind() == SVNNodeKind.DIR
                    && (root.isAdded() || root.isDeleted() || root.isCopied() || root
                            .isLocked())) {
                baseURL = SVNPathUtil.removeTail(baseURL);
            }
        }
        urls = itemsMap.keySet().iterator();
        while (urls.hasNext()) {
            String url = (String) urls.next();
            SVNCommitItem item = (SVNCommitItem) itemsMap.get(url);
            String realPath = url.equals(baseURL) ? "" : url.substring(baseURL.length() + 1);
            decodedPaths.put(SVNEncodingUtil.uriDecode(realPath), item);
        }
        return baseURL;
    }

    public static Map translateLockTokens(Map lockTokens, String baseURL) {
        Map translatedLocks = new TreeMap();
        for (Iterator urls = lockTokens.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            String token = (String) lockTokens.get(url);
            url = url.equals(baseURL) ? "" : url.substring(baseURL.length() + 1);
            translatedLocks.put(SVNEncodingUtil.uriDecode(url), token);
        }
        lockTokens.clear();
        lockTokens.putAll(translatedLocks);
        return lockTokens;
    }

    public static void harvestCommitables(Map commitables, SVNDirectory dir,
            File path, SVNEntry parentEntry, SVNEntry entry, String url,
            String copyFromURL, boolean copyMode, boolean addsOnly,
            boolean justLocked, Map lockTokens, boolean recursive)
            throws SVNException {
        if (commitables.containsKey(path)) {
            return;
        }
        long cfRevision = entry.getCopyFromRevision();
        String cfURL = null;
        if (entry.getKind() != SVNNodeKind.DIR
                && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Unknown entry kind for '" + path + "'");
        }
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorManager.error("svn: Unknown entry kind for '" + path + "'");
        }
        boolean specialFile = fileType == SVNFileType.SYMLINK;
        if (specialFile != (dir.getProperties(entry.getName(), false)
                .getPropertyValue(SVNProperty.SPECIAL) != null)
                && fileType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: Entry '" + path
                    + "' has unexpectedly changed special status");
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
            textConflicts = entry.getConflictOld() != null
                    || entry.getConflictNew() != null
                    || entry.getConflictWorking() != null;
        }
        if (propConflicts || textConflicts) {
            SVNErrorManager.error("svn: Aborting commit: '" + path
                    + "' remains in conflict");
        }
        if (entry.getURL() != null && !copyMode) {
            url = entry.getURL();
        }
        boolean commitDeletion = !addsOnly
                && (entry.isScheduledForDeletion() || entry
                        .isScheduledForReplacement());
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
                SVNErrorManager.error("svn: Did not expect '" + path
                        + "' to be a working copy root");
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
                    SVNErrorManager.error("svn: Commit item '" + path
                            + "' has copy flag but no copyfrom URL");
                }
            }
        }
        boolean textModified = false;
        boolean propsModified = false;
        boolean commitLock;

        if (commitAddition) {
            SVNProperties props = dir.getProperties(entry.getName(), false);
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(),
                    false);
            Map propDiff = baseProps.compareTo(props);
            boolean eolChanged = textModified = propDiff != null
                    && propDiff.containsKey(SVNProperty.EOL_STYLE);
            if (entry.getKind() == SVNNodeKind.FILE) {
                if (commitCopy) {
                    textModified = propDiff != null
                            && propDiff.containsKey(SVNProperty.EOL_STYLE);
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
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(),
                    false);
            Map propDiff = baseProps.compareTo(props);
            boolean eolChanged = textModified = propDiff != null
                    && propDiff.containsKey(SVNProperty.EOL_STYLE);
            propsModified = propDiff != null && !propDiff.isEmpty();
            if (entry.getKind() == SVNNodeKind.FILE) {
                textModified = dir.hasTextModifications(entry.getName(),  eolChanged);
            }
        }

        commitLock = entry.getLockToken() != null
                && (justLocked || textModified || propsModified
                        || commitDeletion || commitAddition || commitCopy);

        if (commitAddition || commitDeletion || textModified || propsModified
                || commitCopy || commitLock) {
            SVNCommitItem item = new SVNCommitItem(path, url, cfURL, entry
                    .getKind(), cfURL != null ? SVNRevision.create(cfRevision)
                    : SVNRevision.create(entry.getRevision()), commitAddition,
                    commitDeletion, propsModified, textModified, commitCopy,
                    commitLock);
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
            for (Iterator ents = entries.entries(false); ents.hasNext();) {
                SVNEntry currentEntry = (SVNEntry) ents.next();
                if ("".equals(currentEntry.getName())) {
                    continue;
                }
                String currentCFURL = cfURL != null ? cfURL : copyFromURL;
                if (currentCFURL != null) {
                    currentCFURL = SVNPathUtil.append(currentCFURL, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                String currentURL = currentEntry.getURL();
                if (copyMode || entry.getURL() == null) {
                    currentURL = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                File currentFile = dir.getFile(currentEntry.getName());
                SVNDirectory childDir;
                if (currentEntry.getKind() == SVNNodeKind.DIR) {
                    childDir = dir.getChildDirectory(currentEntry.getName());
                    if (childDir == null) {
                        SVNFileType currentType = SVNFileType
                                .getType(currentFile);
                        if (currentType == SVNFileType.NONE
                                && currentEntry.isScheduledForDeletion()) {
                            SVNCommitItem item = new SVNCommitItem(currentFile,
                                    currentURL, null, currentEntry.getKind(),
                                    SVNRevision.UNDEFINED, false, true, false,
                                    false, false, false);
                            item.setPath(SVNPathUtil.append(dir.getPath(),
                                    currentEntry.getName()));
                            commitables.put(currentFile, item);
                            continue;
                        }
                        SVNErrorManager.error("svn: Working copy '"
                                + currentFile + "' is missing or not locked");
                    }
                }
                harvestCommitables(commitables, dir, currentFile, entry,
                        currentEntry, currentURL, currentCFURL, copyMode,
                        addsOnly, justLocked, lockTokens, true);

            }
        }
        if (lockTokens != null && entry.getKind() == SVNNodeKind.DIR
                && commitDeletion) {
            // harvest lock tokens for deleted items.
            collectLocks(dir, lockTokens);
        }
    }

    private static void collectLocks(SVNDirectory dir, Map lockTokens)
            throws SVNException {
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

    private static void removeRedundantPaths(Collection dirsToLockRecursively,
            Collection dirsToLock) {
        for (Iterator paths = dirsToLock.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            if (dirsToLockRecursively.contains(path)) {
                paths.remove();
            } else {
                for (Iterator recPaths = dirsToLockRecursively.iterator(); recPaths
                        .hasNext();) {
                    String existingPath = (String) recPaths.next();
                    if (path.startsWith(existingPath + "/")) {
                        paths.remove();
                        break;
                    }
                }
            }
        }
    }

    private static File adjustRelativePaths(File rootFile,
            Collection relativePaths) throws SVNException {
        if (relativePaths.contains("")) {
            String targetName = getTargetName(rootFile);
            if (!"".equals(targetName) && rootFile.getParentFile() != null) {
                // there is a versioned parent.
                rootFile = rootFile.getParentFile();
                Collection result = new TreeSet();
                for (Iterator paths = relativePaths.iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    path = "".equals(path) ? targetName : SVNPathUtil.append(targetName, path);
                    result.add(path);
                }
                relativePaths.clear();
                relativePaths.addAll(result);
            }
        }
        return rootFile;
    }

    private static String getTargetName(File file) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        return wcAccess.getTargetName();
    }
}
