/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNRootEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.progress.SVNProgressProcessor;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNCommitUtil {
    
    private static final String SVN_ENTRY_REPLACED = "svn:entry:replaced";
    
    public static String buildCommitTree(Collection modifiedEntries, Map map) throws SVNException {
        map = map == null ? new HashMap() : map;
        ISVNEntry root = null;
        for(Iterator entries = modifiedEntries.iterator(); entries.hasNext();) {
            ISVNEntry entry = (ISVNEntry) entries.next();
            String url = PathUtil.decode(entry.getPropertyValue(SVNProperty.URL));
            if (entry instanceof ISVNRootEntry) {
                root = entry;
            }
            map.put(url, entry);
        }
        // now add common root entry ?
        String commonRoot = null;
        String[] urls = (String[]) map.keySet().toArray(new String[map.size()]);
        if (root == null) {
            String[] paths = new String[urls.length];
            String host = null;
            // convert urls to path, removing host part of the url (always a common root).
            for(int i = 0; i < paths.length; i++) {
                // put path part of the URL only, without leading slash.
                int index = urls[i].indexOf("://");
                index = urls[i].indexOf('/', index + "://".length());
                if (index < 0) {
                    index = urls[i].length();
                }
                host = urls[i].substring(0, index);
                paths[i] = PathUtil.removeLeadingSlash(urls[i].substring(index));
            }
            // we may have "repo/trunk" (root entry)
            // and files like "repo/trunk/" 
            
            if (map.size() == 1) {
                ISVNEntry rootEntry = (ISVNEntry) map.get(urls[0]);
                // if entry is already a folder, let it be root.
                if (!rootEntry.isDirectory() || rootEntry.isScheduledForAddition() || rootEntry.isScheduledForDeletion()
                    || rootEntry.isPropertiesModified()) {
                    commonRoot = PathUtil.getCommonRoot(paths);
                } else {
                    commonRoot = paths[0];
                }
            } else {
                commonRoot = PathUtil.getCommonRoot(paths);
            }        
            commonRoot = "".equals(commonRoot) ? host : PathUtil.append(host, commonRoot);
        } else {
            commonRoot = root.getPropertyValue(SVNProperty.URL);
            commonRoot = PathUtil.decode(commonRoot);
        }
        // this root may or may not exist in the map. 
        if (!map.containsKey(commonRoot)) {
            map.put(commonRoot, null);
        }
        // replace map urls with paths
        for(int i = 0; i < urls.length; i++) {
            String key = urls[i];
            Object value = map.get(key);
            
            String newKey = key.substring(commonRoot.length());
            newKey = PathUtil.removeLeadingSlash(newKey);
            
            map.put(newKey, value);
            map.remove(key);
        }
        if (map.containsKey(commonRoot)) {
            map.put("", map.get(commonRoot));
            map.remove(commonRoot);
        }
        
        return commonRoot;        
    }
    
    public static void harvestCommitables(ISVNEntry root, String[] paths, boolean recursive, Collection modified) throws SVNException {
        String entryPath = root.getPath();
        DebugLog.log("HV: processing " + entryPath);
        boolean harvest = false;
        for(int i = 0; i < paths.length; i++) {
            harvest = (!recursive && entryPath.startsWith(paths[i])) ||
            (recursive && (entryPath.startsWith(paths[i]) || paths[i].startsWith(entryPath)));
            if (harvest) {
                break;
            }
        }
        if (!harvest) {
            DebugLog.log("HV: processing " + entryPath + " => not below commit roots" );
            return;
        }
        if (root.isMissing()) {
            DebugLog.log("HV: processing " + entryPath + " => missing, skipped" );
            return;
        }
		
		boolean copy = root.getPropertyValue(SVNProperty.COPIED) != null;
		long revision = SVNProperty.longValue(root.getPropertyValue(SVNProperty.REVISION));

		if (root.isDirectory()) {
            if (root.isScheduledForAddition() || root.isScheduledForDeletion() || root.isPropertiesModified()) {
                // add to modified only if it is below one of the paths.
                for(int i = 0; i < paths.length; i++) {
                    if (entryPath.startsWith(paths[i])) {
                        DebugLog.log("HV: processing " + entryPath + " => added to modified as directory" );
                        modified.add(root);
                        break;
                    }
                }
            } 
			if (root.isScheduledForDeletion() && !root.isScheduledForAddition()) {
                DebugLog.log("HV: processing " + entryPath + " => children are not collected for deleted directory " );
				return;
			}
            if (recursive) {
                for(Iterator children = root.asDirectory().childEntries(); children.hasNext();) {
                    ISVNEntry child = (ISVNEntry) children.next();
                    DebugLog.log("HV: processing " + entryPath + " => collecting child: " + child.getPath() );
                    long childRevision = SVNProperty.longValue(child.getPropertyValue(SVNProperty.REVISION));
                    if (copy) {
                        DebugLog.log("HV: processing unmodified copied child " + child.getPath() );
                        if (child.getPropertyValue(SVNProperty.COPYFROM_URL) == null) { 
                            String parentCopyFromURL = root.getPropertyValue(SVNProperty.COPYFROM_URL);
                            child.setPropertyValue(SVNProperty.COPYFROM_URL, PathUtil.append(parentCopyFromURL, PathUtil.encode(child.getName())));
                            child.setPropertyValue(SVNProperty.COPYFROM_REVISION, SVNProperty.toString(childRevision));
                        }
                    }
                    harvestCommitables(child, paths, recursive, modified);
					if (copy) {
						if (!modified.contains(child) && revision != childRevision) {
		                    DebugLog.log("HV: copied child collected, revision differs from parent " + child.getPath() );
							modified.add(child);
						}
					}
                }
            }
        } else {
            if (root.isScheduledForAddition() || 
                    root.isScheduledForDeletion() || 
                    root.isPropertiesModified() ||
                    root.asFile().isContentsModified()) {
                DebugLog.log("HV: processing " + entryPath + " => added to modified as file" );
                modified.add(root);
            }
        }
    }

		public static void doCommit(String path, String url, Map entries, ISVNEditor editor, SVNWorkspace ws, SVNProgressProcessor progressProcessor) throws SVNException {
			doCommit(path, url, entries, editor, ws, progressProcessor, new HashSet());
		}

    public static void doCommit(String path, String url, Map entries, ISVNEditor editor, SVNWorkspace ws, SVNProgressProcessor progressProcessor, Set committedPaths) throws SVNException {
        ISVNEntry root = (ISVNEntry) entries.get(path);

        long revision = root == null ? -1 : SVNProperty.longValue(root.getPropertyValue(SVNProperty.COMMITTED_REVISION));
		boolean copied = false;
        if (root != null) {
            root.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
			copied = Boolean.TRUE.toString().equals(root.getPropertyValue(SVNProperty.COPIED));
        }
        if ("".equals(path)) {
            DebugLog.log("ROOT OPEN: " + path);
            editor.openRoot(-1);
            if (root != null) {
                if (root.sendChangedProperties(editor)) {
                    ws.fireEntryCommitted(root, SVNStatus.MODIFIED);
                }
            }
        } else if (root != null && (root.isScheduledForAddition() || copied)) {
            DebugLog.log("DIR ADD: " + path);
            String copyFromURL = root.getPropertyValue(SVNProperty.COPYFROM_URL);
            long copyFromRevision = -1;
            if (copyFromURL != null) {
                copyFromURL = SVNRepositoryLocation.parseURL(copyFromURL).toCanonicalForm();
                copyFromRevision = Long.parseLong(root.getPropertyValue(SVNProperty.COPYFROM_REVISION));
                DebugLog.log("copyfrom path:" + copyFromURL);
                DebugLog.log("parent's url: " + url);
                copyFromURL = copyFromURL.substring(url.length());
                if (!copyFromURL.startsWith("/")) {
                    copyFromURL = "/" + copyFromURL;
                }
                DebugLog.log("copyfrom path:" + copyFromURL);
            }
            editor.addDir(path, copyFromURL, copyFromRevision);
			root.sendChangedProperties(editor);
            ws.fireEntryCommitted(root, SVNStatus.ADDED);
        } else if (root != null && root.isPropertiesModified()) {
            DebugLog.log("DIR OPEN: " + path + ", " + revision);
            editor.openDir(path, revision); 
            if (root.sendChangedProperties(editor)) {
                ws.fireEntryCommitted(root, SVNStatus.MODIFIED);
            }
        } else if (root == null) {
            DebugLog.log("DIR OPEN (virtual): " + path + ", " + revision);
            editor.openDir(path, revision);
        } 
        String[] virtualChildren = getVirtualChildren(entries, path);
        DebugLog.log("virtual children count: " + virtualChildren.length);
        for(int i = 0; i < virtualChildren.length; i++) {
            String childPath = virtualChildren[i];
            doCommit(childPath, url, entries, editor, ws, progressProcessor, committedPaths);
        }
        ISVNEntry[] children = getDirectChildren(entries, path);
        DebugLog.log("direct children count: " + children.length);
        ISVNEntry parent = null;
        for(int i = 0; i < children.length; i++) {
            ISVNEntry child = children[i];
            if (parent == null) {
                parent = ws.locateParentEntry(child.getPath());
            }
            String childPath = PathUtil.append(path, child.getName());
            childPath = PathUtil.removeLeadingSlash(childPath);
            childPath = PathUtil.removeTrailingSlash(childPath);
            revision = SVNProperty.longValue(child.getPropertyValue(SVNProperty.COMMITTED_REVISION));

	          committedPaths.add(childPath);
	          progressProcessor.setProgress((double)committedPaths.size() / (double)entries.keySet().size());

            if (child.isScheduledForAddition() && child.isScheduledForDeletion()) {
                DebugLog.log("FILE REPLACE: " + childPath);
                child.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
                editor.deleteEntry(childPath, revision);
                updateReplacementSchedule(child);
                if (child.isDirectory()) {
                    doCommit(childPath, url, entries, editor, ws, progressProcessor, committedPaths);
                } else {
                    editor.addFile(childPath, null, -1);
                    child.sendChangedProperties(editor);
                    child.asFile().generateDelta(editor);
                    editor.closeFile(null);
                }
                ws.fireEntryCommitted(child, SVNStatus.REPLACED);
                DebugLog.log("FILE REPLACE: DONE");
            } else if (child.isScheduledForDeletion()) {
                DebugLog.log("FILE DELETE: " + childPath);
                child.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
                if (child.getPropertyValue(SVN_ENTRY_REPLACED) != null) {
                    DebugLog.log("FILE NOT DELETED, PARENT WAS REPLACED");
                } else {
                    editor.deleteEntry(childPath, revision);
                    ws.fireEntryCommitted(child, SVNStatus.DELETED);
                    DebugLog.log("FILE DELETE: DONE");
                }
            } else if (child.isDirectory()) {
                doCommit(childPath, url, entries, editor, ws, progressProcessor, committedPaths);
            } else {
				boolean childIsCopied = Boolean.TRUE.toString().equals(child.getPropertyValue(SVNProperty.COPIED));
				String digest = null;
				if (childIsCopied && !child.isScheduledForAddition()) {
                    DebugLog.log("FILE COPY: " + childPath);
					// first copy it to have in place for modifications if required.
					// get copyfrom url and copyfrom rev
					String copyFromURL = child.getPropertyValue(SVNProperty.COPYFROM_URL);
					long copyFromRev = Long.parseLong(child.getPropertyValue(SVNProperty.COPYFROM_REVISION));
                    if (copyFromURL != null) {
                        copyFromURL = SVNRepositoryLocation.parseURL(copyFromURL).toCanonicalForm();
                        DebugLog.log("copyfrom path:" + copyFromURL);
                        DebugLog.log("parent's url: " + url);
                        // should be relative to repos root.
                        copyFromURL = copyFromURL.substring(url.length());
                        if (!copyFromURL.startsWith("/")) {
                            copyFromURL = "/" + copyFromURL;
                        }
                        DebugLog.log("copyfrom path:" + copyFromURL);
                    }
                    editor.addFile(childPath, copyFromURL, copyFromRev);
					editor.closeFile(null);
                    ws.fireEntryCommitted(child, SVNStatus.ADDED);
                    DebugLog.log("FILE COPY: DONE");
				}
                if (child.isScheduledForAddition()) {
                    DebugLog.log("FILE ADD: " + childPath);
                    child.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
                    String copyFromURL = child.getPropertyValue(SVNProperty.COPYFROM_URL);
                    long copyFromRevision = -1;
                    if (copyFromURL != null) {
                        copyFromURL = SVNRepositoryLocation.parseURL(copyFromURL).toCanonicalForm();
                        copyFromRevision = Long.parseLong(child.getPropertyValue(SVNProperty.COPYFROM_REVISION));
                        DebugLog.log("copyfrom path:" + copyFromURL);
                        DebugLog.log("parent's url: " + url);
                        // should be relative to repos root.
                        copyFromURL = copyFromURL.substring(url.length());
                        if (!copyFromURL.startsWith("/")) {
                            copyFromURL = "/" + copyFromURL;
                        }
                        DebugLog.log("copyfrom path:" + copyFromURL);
                    }
                    editor.addFile(childPath, copyFromURL, copyFromRevision);
					child.sendChangedProperties(editor);
					digest = child.asFile().generateDelta(editor);
                    editor.closeFile(digest);
                    ws.fireEntryCommitted(child, SVNStatus.ADDED);
                    DebugLog.log("FILE ADD: DONE");
                } else if (child.asFile().isContentsModified() || child.isPropertiesModified()) {
                    DebugLog.log("FILE COMMIT: " + childPath + " : " + revision);
                    child.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
                    editor.openFile(childPath, revision);
                    child.sendChangedProperties(editor);
                    if (child.asFile().isContentsModified()) {
                        digest = child.asFile().generateDelta(editor);
                    }
                    editor.closeFile(digest);
                    ws.fireEntryCommitted(child, SVNStatus.MODIFIED);
                    DebugLog.log("FILE COMMIT: DONE: " + digest);
                }
            }
        }
        DebugLog.log("DIR CLOSE: " + path);
        editor.closeDir();
    }
    
    public static void updateWorkingCopy(SVNCommitInfo info, String uuid, Map commitTree, SVNWorkspace ws) throws SVNException {
        Set parents = new HashSet();
        LinkedList sorted = new LinkedList();
        for(Iterator entries = commitTree.values().iterator(); entries.hasNext();) {
            ISVNEntry entry = (ISVNEntry) entries.next();
            if (entry == null) {
                continue;
            }
            int index = 0;
            for(Iterator els = sorted.iterator(); els.hasNext();) {
                ISVNEntry current = (ISVNEntry) els.next();
                if (entry.getPath().compareTo(current.getPath()) >= 0) {
                    sorted.add(index, entry);
                    entry = null;
                    break;
                }            
                index++;
            }
            if (entry != null) {
                sorted.add(entry);
            }
        }
        for(Iterator entries = sorted.iterator(); entries.hasNext();) {
            ISVNEntry entry = (ISVNEntry) entries.next();
            if (entry == null) {
                continue;
            }
            updateWCEntry(entry, info, uuid, parents, ws);
        }
        for(Iterator entries = parents.iterator(); entries.hasNext();) {
            ISVNEntry parent = (ISVNEntry) entries.next();
            if (!commitTree.containsValue(parent)) {
                DebugLog.log("UPDATE (save): " + parent.getPath());
                parent.save(false);
            }
        }
    }

    private static void updateWCEntry(ISVNEntry entry, SVNCommitInfo info, String uuid, Collection parents, SVNWorkspace ws) throws SVNException {
        String revStr = Long.toString(info.getNewRevision());
        entry.setPropertyValue(SVNProperty.REVISION, revStr);
        if (entry.getPropertyValue(SVNProperty.COPIED) != null && entry.isDirectory()) {
            DebugLog.log("PROCESSING COPIED DIR: " + entry.getPath());
            for(Iterator copied = entry.asDirectory().childEntries(); copied.hasNext();) {
                ISVNEntry child = (ISVNEntry) copied.next();
                parents.add(child);
                DebugLog.log("PROCESSING COPIED CHILD: " + child.getPath());
                updateWCEntry(child, info, uuid, parents, ws);
            }
        }
        ISVNEntry parent = ws.locateParentEntry(entry.getPath());
        if (parent != null) {
            // have to be deleted.
            parents.add(parent);
            if (entry.isScheduledForDeletion() && !entry.isScheduledForAddition()) {
                DebugLog.log("UPDATE (delete): " + entry.getPath());
                boolean storeInfo = entry.getPropertyValue(SVN_ENTRY_REPLACED) == null;
                parent.asDirectory().deleteChild(entry.getName(), storeInfo);
                return;
            }
        }
        if (entry.getPropertyValue(SVNProperty.COMMITTED_REVISION) == null || entry.getPropertyValue(SVNProperty.COPIED) != null) {
            entry.setPropertyValue(SVNProperty.COPIED, null);
            entry.setPropertyValue(SVNProperty.COPYFROM_URL, null);
            entry.setPropertyValue(SVNProperty.COPYFROM_REVISION, null);
            entry.setPropertyValue(SVNProperty.SCHEDULE, null);
            entry.setPropertyValue(SVNProperty.DELETED, null);
            entry.setPropertyValue(SVNProperty.COMMITTED_REVISION, revStr);
            entry.setPropertyValue(SVNProperty.LAST_AUTHOR, info.getAuthor());
            entry.setPropertyValue(SVNProperty.COMMITTED_DATE, TimeUtil.formatDate(info.getDate()));
            entry.setPropertyValue(SVNProperty.UUID, uuid);
        }
        if (parent != null) {
            parent.asDirectory().unschedule(entry.getName());
        }
        DebugLog.log("UPDATE (commit): " + entry.getPath());
        entry.commit();
    }

    private static void updateReplacementSchedule(ISVNEntry entry) throws SVNException {
        if (SVNProperty.SCHEDULE_REPLACE.equals(entry.getPropertyValue(SVNProperty.SCHEDULE))) {
            entry.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
            if (entry.isDirectory()) {
                for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
                    updateReplacementSchedule((ISVNEntry) children.next());
                }
            }
        } else if (SVNProperty.SCHEDULE_DELETE.equals(entry.getPropertyValue(SVNProperty.SCHEDULE))) {
            entry.setPropertyValue(SVN_ENTRY_REPLACED, "true");
        }
    }
    
    private static ISVNEntry[] getDirectChildren(Map map, String url) {
        Collection children = new ArrayList();
        for(Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            String childURL = (String) keys.next();
            String parentURL = PathUtil.removeTail(childURL);
            parentURL = PathUtil.removeLeadingSlash(parentURL);
            if (parentURL != null && parentURL.equals(url) && map.get(childURL) != null && !childURL.equals(url)) {
                if (!children.contains(map.get(childURL))) {
                    children.add(map.get(childURL));
                }
            }
        }
        return (ISVNEntry[]) children.toArray(new ISVNEntry[children.size()]);
    }

    private static String[] getVirtualChildren(Map map, String url) {
        // if url is root then only return entries with '/'
        Collection children = new ArrayList();
        for(Iterator keys = map.keySet().iterator(); keys.hasNext();) {
            String childURL = (String) keys.next();
            if (childURL.startsWith(url) && !childURL.equals(url)) {
                childURL = childURL.substring(url.length());
                if (!childURL.startsWith("/") && !"".equals(url)) {
                    // otherwise it may be just a and abc
                    continue;
                }
                childURL = PathUtil.removeLeadingSlash(childURL);
                String vChild = PathUtil.append(url, PathUtil.head(childURL));
                vChild = PathUtil.removeLeadingSlash(vChild);
                if (!vChild.equals(url) && map.get(vChild) == null && !children.contains(vChild))  {
                    children.add(vChild);
                }
            }
        }
        return (String[]) children.toArray(new String[children.size()]);
    }
}
