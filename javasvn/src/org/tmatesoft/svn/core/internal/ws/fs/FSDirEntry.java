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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.FileTypeUtil;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class FSDirEntry extends FSEntry implements ISVNDirectoryEntry {

    private Map myChildEntries;
    private Map myDeletedEntries;
    private Map myChildren;
    private Map myDirEntry;
    
    public FSDirEntry(FSAdminArea area, FSRootEntry root, String path, String location) {
        super(area, root, path);
        if (location != null) {
            try {
                String url = getPropertyValue(SVNProperty.URL);
                if (url == null) {
                    setPropertyValue(SVNProperty.URL, location);
                }
            } catch (SVNException e) {}
        }
    }
    
    public ISVNEntry getChild(String name) throws SVNException {
        loadEntries();
        return (ISVNEntry) myChildren.get(name);
    }

    public ISVNEntry getUnmanagedChild(String name) throws SVNException {
        ISVNEntry managed = getChild(name);
        if (managed != null) {
            return managed;
        }
        loadUnmanagedChildren();
        return (ISVNEntry) myAllUnmanagedChildren.get(name);
    }

    public void deleteChild(String name, boolean storeInfo) throws SVNException {
        loadEntries();
        ISVNEntry child = (ISVNEntry) myChildren.remove(name);
        Map oldMap = (Map) myChildEntries.remove(name);

        if (child != null) {
            doDeleteFiles(child);
        }
        if (storeInfo) {
            Map map = new HashMap();
            map.put(SVNProperty.NAME, name);
            map.put(SVNProperty.DELETED, Boolean.TRUE.toString());
            map.put(SVNProperty.KIND, oldMap.get(SVNProperty.KIND));
            if (oldMap.containsKey(SVNProperty.REVISION)) {
                map.put(SVNProperty.REVISION, oldMap.get(SVNProperty.REVISION));
            }
            if (myDeletedEntries == null) {
                myDeletedEntries = new HashMap();
            }
            myDeletedEntries.put(name, map);
        }        
    }
    
    public boolean isObstructed() {
        return super.isObstructed() || 
         getRootEntry().getWorkingCopyFile(this).isFile() ||
         !getAdminArea().getAdminArea(this).exists();
    }

    public boolean isDirectory() {
        return true;
    }
    
    public ISVNEntry copy(String asName, ISVNEntry toCopy) throws SVNException {
        File dst = getRootEntry().getWorkingCopyFile(this);
        doCopyFiles(toCopy, dst, asName);
        
        long revision = SVNProperty.longValue(toCopy.getPropertyValue(SVNProperty.COMMITTED_REVISION));
        String url = toCopy.getPropertyValue(SVNProperty.URL);
        ISVNEntry added = null;
        if (toCopy.isDirectory()) {
            added = addDirectory(asName, revision);
            Map childEntry = (Map) myChildEntries.get(asName);
            childEntry.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
            if (!toCopy.isScheduledForAddition()) {
                childEntry.put(SVNProperty.COPIED, SVNProperty.toString(true));
                childEntry.put(SVNProperty.COPYFROM_REVISION, SVNProperty.toString(revision));
                childEntry.put(SVNProperty.COPYFROM_URL, url);
            } 
            updateURL(added, getPropertyValue(SVNProperty.URL));
        } else {
            added = addFile(asName, revision);
            added.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
        }
        // do not mark as copied if source was just locally scheduled for addition
        added.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        if (!toCopy.isScheduledForAddition()) {
            added.setPropertyValue(SVNProperty.COPYFROM_REVISION, SVNProperty.toString(revision));
            added.setPropertyValue(SVNProperty.COPYFROM_URL, url);
        }
        if (myDeletedEntries != null && myDeletedEntries.containsKey(asName)) {
            added.setPropertyValue(SVNProperty.DELETED, Boolean.TRUE.toString());
            myDeletedEntries.remove(asName);
        }
        if (!toCopy.isScheduledForAddition()) {
            setPropertyValueRecursively(added, SVNProperty.COPIED, SVNProperty.toString(true));
        } else {
            setPropertyValueRecursively(added, SVNProperty.REVISION, SVNProperty.toString(0));
        }
        return added;    
    }
    
    public ISVNDirectoryEntry addDirectory(String name, long revision) throws SVNException {
        if (getChild(name) != null) {
            return (ISVNDirectoryEntry) getChild(name);
        }
        loadEntries();
        
        String url = (String) myDirEntry.get(SVNProperty.URL);
        url = PathUtil.append(url, PathUtil.encode(name));
        FSDirEntry entry = new FSDirEntry(getAdminArea(), getRootEntry(), PathUtil.append(getPath(), name), url);
        if (revision >= 0) {
            entry.setPropertyValue(SVNProperty.COMMITTED_REVISION, SVNProperty.toString(revision));
        }
        myChildren.put(name, entry);
        Map entryMap = new HashMap();
        entryMap.put(SVNProperty.NAME, name);
        entryMap.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
        myChildEntries.put(name, entryMap);

        return entry;
    }
    
    public ISVNFileEntry addFile(String name, long revision) throws SVNException {
        loadEntries();

        Map entryMap = new HashMap();
        entryMap.put(SVNProperty.NAME, name);
        entryMap.put(SVNProperty.KIND, SVNProperty.KIND_FILE);
        FSFileEntry entry = new FSFileEntry(getAdminArea(), getRootEntry(), PathUtil.append(getPath(), name), entryMap);
        if (revision >= 0) {
            entry.setPropertyValue(SVNProperty.COMMITTED_REVISION, SVNProperty.toString(revision));
        }        
        myChildren.put(name, entry);
        myChildEntries.put(name, entryMap);
        
        return entry;
    }

    public Iterator childEntries() throws SVNException {
        loadEntries();
        return myChildren.values().iterator();
    }
    
    public Iterator deletedEntries() throws SVNException {
        loadEntries();
        Collection result = new LinkedList();
        if (myDeletedEntries != null) {
            result.addAll(myDeletedEntries.values());
        } 
        if (myChildEntries != null) {
            for(Iterator entries = myChildEntries.values().iterator(); entries.hasNext();) {
                Map entry = (Map) entries.next();
                if (entry.get(SVNProperty.DELETED) != null) {
                    result.add(entry);
                }
            }
        }
        return result.iterator();
    }
    
    public void dispose() throws SVNException {
        if (myChildren != null) {
            for(Iterator children = childEntries(); children.hasNext();) {
                ((ISVNEntry) children.next()).dispose();
            }
        }
        
        myChildEntries = null;
        myDeletedEntries = null;
        myChildren = null;
        myDirEntry = null;
        myUnmanagedChildren = null;
        myAllUnmanagedChildren = null;
        
        super.dispose();
    }
    
    public void revert(String childName) throws SVNException {
        ISVNEntry child = getChild(childName);
        if (child == null) {
            return;
        }
        myUnmanagedChildren = null;
        myAllUnmanagedChildren = null;
        
        if (child.isScheduledForAddition()) {
            myChildEntries.remove(childName);
            myChildren.remove(childName);
            if (child.isDirectory()) {
                File adminArea = getAdminArea().getAdminArea(child);
                FSUtil.deleteAll(adminArea);
            }
        } else {
            unschedule(childName);
            ((FSEntry) child).revertProperties();
            if (!child.isDirectory()) {
                ((FSFileEntry) child).revertContents();
            } else if (child.getPropertyValue(SVNProperty.PROP_TIME) != null) {
                if (myChildEntries != null) {
                    Map entryInParent = (Map) myChildEntries.get(childName);
                    if (entryInParent != null) {
                        entryInParent.put(SVNProperty.PROP_TIME, child.getPropertyValue(SVNProperty.PROP_TIME));
                    }
                }
            }
        }
    }

    public void merge(boolean recursive) throws SVNException {
        String revision = getPropertyValue(SVNProperty.REVISION);
        Set obstructedChildren = new HashSet();
        if (myChildren != null) {
            Collection entries = new ArrayList(myChildren.values()); 
            for(Iterator children = entries.iterator(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                if (!recursive && child.isDirectory()) {
                    continue;
                }
                boolean isCorrupted = child.getPropertyValue(SVNProperty.CORRUPTED) != null;
                child.setPropertyValue(SVNProperty.CORRUPTED, null);
                if (child.getPropertyValue(SVNProperty.REVISION) == null) {
                    // missing child that was deleted.
                    DebugLog.log("MERGING: MISSING ENTRY DELETED: " + child.getPath());
                    deleteChild(child.getName(), true);
                    continue;
                }
                if (child.isScheduledForAddition() || child.isScheduledForDeletion() || isCorrupted) {
                    // obstructed!
                    obstructedChildren.add(child.getName());
                } else {
                    child.setPropertyValue(SVNProperty.REVISION, revision);
                }
                
                child.merge(recursive);
            }
        }
        super.merge();
        // update revision in all direct entries (but obstructed!)
        // if it is was not a single child file update!
        for(Iterator entries = myChildEntries.values().iterator(); entries.hasNext();) {
            Map entry = (Map) entries.next();
            if (!obstructedChildren.contains(entry.get(SVNProperty.NAME))) {
                entry.put(SVNProperty.REVISION, revision);
            }
        }
        myDeletedEntries = null;
        saveEntries();
    }
    
    public void commit() throws SVNException {
        if (!getRootEntry().getWorkingCopyFile(this).exists()) {
            return;
        }
        super.commit();
        saveEntries();
    }

    public void save(boolean recursive) throws SVNException {
        super.save(recursive);
        if (recursive && myChildren != null) {
            // only if children was loaded?
            for(Iterator children = childEntries(); children.hasNext();) {
                ISVNEntry entry = (ISVNEntry) children.next();
                entry.save();
            }
        }
        saveEntries();
    }
    
    
    private void saveEntries() throws SVNException {
        if (myDirEntry != null) {
            getAdminArea().saveEntries(this, myDirEntry, myChildEntries, myDeletedEntries);
        }
    }

    private void loadEntries() throws SVNException {
        if (myChildren != null) {
            return;
        }
        Map childEntriesMap = getAdminArea().loadEntries(this);
        myDirEntry = (Map) childEntriesMap.remove("");
        if (myDirEntry == null) {
            myDirEntry = new HashMap();
            myDirEntry.put(SVNProperty.NAME, "");
            myDirEntry.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
        }
        myChildren = new HashMap();
        myChildEntries = new HashMap();
        
        String baseURL = (String) myDirEntry.get(SVNProperty.URL);
        String revision = (String) myDirEntry.get(SVNProperty.REVISION);
        for(Iterator childEntries = childEntriesMap.values().iterator(); childEntries.hasNext();) {
            Map entry = (Map) childEntries.next();
            String name = (String) entry.get(SVNProperty.NAME);
            if (entry.containsKey(SVNProperty.DELETED)) {
                if (myDeletedEntries == null) {
                    myDeletedEntries = new HashMap();
                }
                // if not added, skip.
                if (!SVNProperty.SCHEDULE_ADD.equals(entry.get(SVNProperty.SCHEDULE))) {
                    myDeletedEntries.put(name, entry);
                    continue;
                }
                // add to normal entries, but still return in deleted...
            }
            ISVNEntry child = null;
            String url = null;
            if (!entry.containsKey(SVNProperty.URL)) {
                url = PathUtil.append(baseURL, PathUtil.encode(name));
                entry.put(SVNProperty.URL, url);
            }
            if (!entry.containsKey(SVNProperty.REVISION)) {
                entry.put(SVNProperty.REVISION, revision);
            }
            if (SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND))) {
                child = new FSDirEntry(getAdminArea(), getRootEntry(), PathUtil.append(getPath(), name), url);
            } else {
                child = new FSFileEntry(getAdminArea(), getRootEntry(), PathUtil.append(getPath(), name), entry);
            }
            myChildren.put(name, child);
            myChildEntries.put(name, entry);
        }
    }
    
    protected Map getEntry() throws SVNException {
        loadEntries();            
        return myDirEntry;
    }
    
    protected Map getChildEntry(String name) {
        if (myChildEntries != null && name != null) {
            return (Map) myChildEntries.get(name);
        }
        return null;
    }
    
    private Map myUnmanagedChildren;
    private Map myAllUnmanagedChildren;
    
    public Iterator unmanagedChildEntries(boolean includeIgnored) throws SVNException {
        loadUnmanagedChildren();
        return includeIgnored ? myAllUnmanagedChildren.values().iterator() : myUnmanagedChildren.values().iterator();
    }
    
    private void loadUnmanagedChildren() throws SVNException {
        if (myAllUnmanagedChildren != null) {
            return;
        }
        loadEntries();
        myAllUnmanagedChildren = new HashMap();
        myUnmanagedChildren = new HashMap();
        
        File dir = getRootEntry().getWorkingCopyFile(this);
        final File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                if (myChildren != null &&
                        (myChildren.containsKey(child.getName()) || ".svn".equals(child.getName()))) {
                    continue;
                }
                if (FSUtil.isWindows) {
                    // check for files with different case in name
                    boolean obsturcted = false;
                    for(Iterator names = myChildren.keySet().iterator(); names.hasNext();) {
                        String name = (String) names.next();
                        if (name.equalsIgnoreCase(child.getName())) {
                            obsturcted = true;
                            break;                            
                        }
                    }
                    if (obsturcted) {
                        continue;
                    }
                }
                String path = PathUtil.append(getPath(), child.getName());
                ISVNEntry childEntry;
                if (child.isDirectory()) {
                    childEntry = new FSDirEntry(getAdminArea(), getRootEntry(), path, null);
                    ((FSDirEntry) childEntry).getEntry().put(SVNProperty.KIND, SVNProperty.KIND_DIR);
                } else {
                    Map entryMap = new HashMap();
                    entryMap.put(SVNProperty.NAME, child.getName());
                    entryMap.put(SVNProperty.KIND, SVNProperty.KIND_FILE);
                    childEntry = new FSFileEntry(getAdminArea(), getRootEntry(), path, entryMap);                        
                }
                ((FSEntry) childEntry).setManaged(false);
                if (!isIgnored(child.getName())) {
                    myUnmanagedChildren.put(child.getName(), childEntry);
                }
                myAllUnmanagedChildren.put(child.getName(), childEntry);
            }
        }
    }

    public ISVNEntry scheduleForAddition(String name, boolean mkdir, boolean recurse) throws SVNException {
        loadEntries();
        ISVNEntry child = getChild(name);
	    File file = new File(getRootEntry().getWorkingCopyFile(this), name);
        if (child != null && !child.isScheduledForDeletion()) {
            throw new SVNException("working copy file " + name + " already exists in " + getPath());
        }
	    if (child != null && ((child.isDirectory() && file.isFile()) || (!child.isDirectory() && !file.isFile()))) {
		    throw new SVNException("Cannot change node kind of " + name + " within path " + getPath());
	    }
	    if (myDeletedEntries != null && myDeletedEntries.get(name) != null) {
		    final Map deletedProperties = (Map)myDeletedEntries.get(name);
		    final String kind = (String)deletedProperties.get(SVNProperty.KIND);
		    if (kind != null && ((file.isFile() && kind.equals(SVNProperty.KIND_DIR)) || (!file.isFile() && kind.equals(SVNProperty.KIND_FILE)))) {
			    throw new SVNException("Cannot change node kind of " + name + " within path " + getPath());
		    }
	    }

        if (mkdir && !file.exists()) {
            file.mkdirs();
        }
        if (!file.exists()) {
            throw new SVNException("file " + file.getPath() + " doesn't exists");
        }
        myUnmanagedChildren = null;
        myAllUnmanagedChildren = null;
        
        String path = PathUtil.append(getPath(), name);
        FSEntry entry = null;
        Map entryMap = null;
        if (child == null) {
            entryMap = new HashMap();
            entryMap.put(SVNProperty.NAME, name);
        } else {
            entryMap = (Map) myChildEntries.get(name);
        }
        if (child != null) {
            entryMap.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_REPLACE);
        } else {
            entryMap.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
            entryMap.put(SVNProperty.KIND, file.isDirectory() ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
        }
        String url = (String) getEntry().get(SVNProperty.URL);
        url = PathUtil.append(url, PathUtil.encode(name));
        
        if (myDeletedEntries != null && myDeletedEntries.containsKey(name)) {
            myDeletedEntries.remove(name);
            entryMap.put(SVNProperty.DELETED, Boolean.TRUE.toString());
        }
        
        if (file.isDirectory()) {
            if (child == null) {
                entry = new FSDirEntry(getAdminArea(), getRootEntry(), path, url);            
                myChildren.put(name, entry);
                myChildEntries.put(name, entryMap);
                entry.getEntry().put(SVNProperty.REVISION, SVNProperty.toString(0));
                entry.getEntry().put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
            } else {
                entry = (FSEntry) child;
                entry.getEntry().put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_REPLACE);
            }
            if (recurse) {
                for(Iterator children = ((ISVNDirectoryEntry) entry).childEntries(); children.hasNext();) {
                    ISVNEntry ch = (ISVNEntry) children.next();
                    if (ch.isScheduledForDeletion() && getRootEntry().getWorkingCopyFile(ch).exists()) {
                        ((FSDirEntry) entry).scheduleForAddition(ch.getName(), false, recurse);
                    }
                }
                for(Iterator children = ((ISVNDirectoryEntry) entry).unmanagedChildEntries(false); children.hasNext();) {
                    ISVNEntry ch = (ISVNEntry) children.next();
                    ((FSDirEntry) entry).scheduleForAddition(ch.getName(), false, recurse);
                }
            }
        } else if (child == null) {
            entryMap.put(SVNProperty.REVISION, SVNProperty.toString(0));
            entryMap.put(SVNProperty.URL, url);
            entry = new FSFileEntry(getAdminArea(), getRootEntry(), path, entryMap);
            myChildren.put(name, entry);
            myChildEntries.put(name, entryMap);
        } else if (child != null) {
            // delete props file to copy behaviour of command line client.
            File propsFile = getAdminArea().getPropertiesFile(child);
            FSUtil.setReadonly(propsFile, false);
            propsFile.delete();
            entry = (FSEntry) child;
        }
        try {
            if (!entry.isDirectory() && !FileTypeUtil.isTextFile(getRootEntry().getWorkingCopyFile(entry))) {
                entry.setPropertyValue(SVNProperty.MIME_TYPE, "application/octet-stream");
            }
        } catch (IOException e) {
            throw new SVNException(e);
        }
    
        return entry;
    }

    public ISVNEntry scheduleForDeletion(String name) throws SVNException {
        return scheduleForDeletion(name, false);
    }
    
    public ISVNEntry scheduleForDeletion(String name, boolean moved) throws SVNException {
        ISVNEntry entry = getChild(name);
        if (entry == null) {
            // force file deletion
            if (moved) {
                deleteChild(name, false);
            }
            return entry;
        } else if (entry.isScheduledForDeletion()) {
            return entry;
        }
        if (entry.isScheduledForAddition()) {
            if (moved) {
                // keep "deleted" state
                boolean storeInfo = entry.getPropertyValue(SVNProperty.DELETED) != null;
                if (!storeInfo && entry.isDirectory()) {
                    storeInfo = ((Map) myChildEntries.get(name)).get(SVNProperty.DELETED) != null;
                }
                deleteChild(name, storeInfo);
            } else {
                myChildEntries.remove(name);
                myChildren.remove(name);
            }
            return entry;
        }
        // to file's entry or dir entry
        ((FSEntry) entry).getEntry().put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
        if (entry.isDirectory()) {
            Map entryMap = (Map) myChildEntries.get(name);
            if (entryMap != null) {
                entryMap.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
            }
            for(Iterator children = ((ISVNDirectoryEntry) entry).childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                ((ISVNDirectoryEntry) entry).scheduleForDeletion(child.getName(), moved);
            }
        } else {
            File file = getRootEntry().getWorkingCopyFile(entry);
            file.delete();
        }
        return entry;
    }

    public void unschedule(String name) throws SVNException {
        ISVNEntry child = getChild(name);
        if (child == null) {
            return;
        }
        if (child.isDirectory()) {
            Map entryMap = (Map) myChildEntries.get(name);
            if (entryMap != null) {
                unschedule(entryMap);
            }
            if (child.getPropertyValue(SVNProperty.DELETED) == null &&
                    entryMap.get(SVNProperty.DELETED) != null) {
                entryMap.remove(SVNProperty.DELETED);
            }
        }
        unschedule(((FSEntry) child).getEntry()); 
    }
    
    private static void unschedule(Map entryMap) {
        entryMap.remove(SVNProperty.SCHEDULE);
        entryMap.remove(SVNProperty.COPIED);
        entryMap.remove(SVNProperty.COPYFROM_URL);
        entryMap.remove(SVNProperty.COPYFROM_REVISION);
    }
    
    public ISVNFileEntry asFile() { 
        return null;
    }
    
    public ISVNDirectoryEntry asDirectory() {
        return this;
    }
    
    public boolean isIgnored(String name) throws SVNException {
        String ignored = getRootEntry().getGlobalIgnore();
        if (getPropertyValue(SVNProperty.IGNORE) != null) {
            ignored += " " + getPropertyValue(SVNProperty.IGNORE);
        }
        for(StringTokenizer tokens = new StringTokenizer(ignored, " \t\n\r"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (token.length() == 0) {
                continue;
            }
            token = token.replaceAll("\\.", "\\\\.");
            token = token.replaceAll("\\*", ".*");
            token = token.replaceAll("\\?", ".");
            if (Pattern.matches(token, name)) {
                return true;
            }
        }        
        return false;        
    }
    
    private void doCopyFiles(ISVNEntry copy, File dst, String asName) throws SVNException {
        File src = getRootEntry().getWorkingCopyFile(copy);
        try {
            if (copy.isDirectory()) {
                File dir = new File(dst, asName);
                dir.mkdirs();
                for(Iterator children = copy.asDirectory().childEntries(); children.hasNext();) {
                    ISVNEntry child = (ISVNEntry) children.next();
                    doCopyFiles(child, dir, child.getName());
                }
                getAdminArea().copyArea(dir, copy, asName);
            } else {
                FSUtil.copyAll(src, dst, asName, null);
                getAdminArea().copyArea(dst, copy, asName);
            }
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }

    private void doDeleteFiles(ISVNEntry entry) throws SVNException {
        if (entry.isDirectory()) {
            for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                doDeleteFiles(child);
            }
        }
        boolean keepWC = !entry.isDirectory() && entry.asFile().isContentsModified();
        getAdminArea().deleteArea(entry);
        if (keepWC) {
            return;
        }
        File file = getRootEntry().getWorkingCopyFile(entry);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static void setPropertyValueRecursively(ISVNEntry root, String name, String value) throws SVNException {
        root.setPropertyValue(name, value);
        if (root.isDirectory()) {
            for(Iterator children = root.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                setPropertyValueRecursively(child, name, value);
            }
        }
    }
    
    private static void updateURL(ISVNEntry target, String parentURL) throws SVNException {
        parentURL = PathUtil.append(parentURL, PathUtil.encode(target.getName()));
        target.setPropertyValue(SVNProperty.URL, parentURL);
        if (target.isDirectory()) {
            for(Iterator children = target.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                if (child.isDirectory()) {
                    updateURL(child, parentURL);
                }
            }
        }
    }

	public ISVNEntryContent getContent() throws SVNException {
		return new FSDirEntryContent(this);
	}
}