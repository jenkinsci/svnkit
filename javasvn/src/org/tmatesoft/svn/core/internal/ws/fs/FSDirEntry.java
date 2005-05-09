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
import java.io.InputStream;
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
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
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
                if (!getAdminArea().getAdminArea(this).exists()) {
                    try {
                        setPropertyValue(SVNProperty.URL, location);
                    } catch (SVNException e) {
                        DebugLog.error(e);
                    }
                }
        }
    }
    
    public ISVNEntry getChild(String name) throws SVNException {
        loadEntries();
        return (ISVNEntry) myChildren.get(name);
    }
    
    public void rename(String oldName, String newName) throws SVNException {
        loadEntries();
        Object info = myChildEntries.remove(oldName);
        myChildEntries.put(newName, info);
        Object child = myChildren.remove(oldName);
        myChildren.put(newName, child);
        
        ((FSEntry) child).setName(newName);        
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
        } else {
            child = getUnmanagedChild(name);
            if (child != null) {
                doDeleteFiles(child);
            }
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
    
    public Map getChildEntryMap(String name) throws SVNException {
        loadEntries();
        return (Map) myChildEntries.get(name);
    }
    
    public void markAsCopied(String name, SVNRepositoryLocation source, long revision) throws SVNException {
        loadEntries();
        boolean wasDeleted = false; 
        if (myDeletedEntries != null && myDeletedEntries.containsKey(name)) {
            wasDeleted = true;
            myDeletedEntries.remove(name);
        }
        Map entry = new HashMap();
        entry.put(SVNProperty.COPYFROM_URL, source.toCanonicalForm());
        entry.put(SVNProperty.COPYFROM_REVISION, Long.toString(revision));
        entry.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
        entry.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        entry.put(SVNProperty.NAME, name);
        entry.put(SVNProperty.COPIED, Boolean.TRUE.toString());
        if (wasDeleted) {
            entry.put(SVNProperty.DELETED, Boolean.TRUE.toString());
        }
        myChildEntries.put(name, entry);
        save(false);
        myChildren = null;
        myChildEntries = null;
        ISVNEntry copiedEntry = getChild(name);
        copiedEntry.setPropertyValue(SVNProperty.COPIED, "true");
        copiedEntry.setPropertyValue(SVNProperty.COPYFROM_URL, source.toCanonicalForm());
        copiedEntry.setPropertyValue(SVNProperty.COPYFROM_REVISION, Long.toString(revision));
        copiedEntry.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        if (wasDeleted) {
            copiedEntry.setPropertyValue(SVNProperty.DELETED, Boolean.TRUE.toString());
        }
        updateURL(copiedEntry, getPropertyValue(SVNProperty.URL));
        setPropertyValueRecursively(copiedEntry, SVNProperty.COPIED, "true");
        copiedEntry.save();
    }

    public void markAsCopied(InputStream contents, long length, Map properties, String name, SVNRepositoryLocation source) throws SVNException {
        long revision = SVNProperty.longValue((String) properties.get(SVNProperty.REVISION));
        FSFileEntry file = (FSFileEntry) addFile(name, revision);
        file.initProperties();
        file.applyChangedProperties(properties);
        SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(length);
        file.applyDelta(window, contents, false);
        file.deltaApplied(false);
        file.merge(false);
        if (source != null) {
            file.setPropertyValue(SVNProperty.COPIED, "true");
            file.setPropertyValue(SVNProperty.COPYFROM_URL, source.toCanonicalForm());
            file.setPropertyValue(SVNProperty.COPYFROM_REVISION, Long.toString(revision));
        }
        file.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        file.setPropertyValue(SVNProperty.REVISION, "0");
        file.setPropertyValue(SVNProperty.KIND, SVNProperty.KIND_FILE);
        if (source == null) {
            getAdminArea().getBaseFile(file).delete();
            getAdminArea().getBasePropertiesFile(file).delete();
            getAdminArea().getWCPropertiesFile(file).delete();
            file.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
            file.setPropertyValue(SVNProperty.COMMITTED_DATE, null);
            file.setPropertyValue(SVNProperty.LAST_AUTHOR, null);
            file.setPropertyValue(SVNProperty.PROP_TIME, null);
            file.setPropertyValue(SVNProperty.TEXT_TIME, null);
            file.setPropertyValue(SVNProperty.CHECKSUM, null);
        } 
        if (myDeletedEntries != null && myDeletedEntries.containsKey(name)) {
            file.setPropertyValue(SVNProperty.DELETED, Boolean.TRUE.toString());
            myDeletedEntries.remove(name);
        }

        save(false);
    }
    
    public ISVNEntry copy(String asName, ISVNEntry toCopy) throws SVNException {
        File dst = getRootEntry().getWorkingCopyFile(this);
        doCopyFiles(toCopy, dst, asName);
        
        long revision = SVNProperty.longValue(toCopy.getPropertyValue(SVNProperty.REVISION));
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
            } else if (toCopy.getPropertyValue(SVNProperty.COPYFROM_URL) != null) {
                childEntry.put(SVNProperty.COPIED, SVNProperty.toString(true));
                childEntry.put(SVNProperty.COPYFROM_REVISION, toCopy.getPropertyValue(SVNProperty.COPYFROM_REVISION));
                childEntry.put(SVNProperty.COPYFROM_URL, toCopy.getPropertyValue(SVNProperty.COPYFROM_URL));
            }
            updateURL(added, getPropertyValue(SVNProperty.URL));
            updateDeletedEntries(added);
        } else {
            added = addFile(asName, revision);
            added.setPropertyValue(SVNProperty.COMMITTED_REVISION, null);
            // inherit copyfrom info
            added.setPropertyValue(SVNProperty.COPIED, toCopy.getPropertyValue(SVNProperty.COPIED));
        }
        // do not mark as copied if source was just locally scheduled for addition
        added.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        if (!toCopy.isScheduledForAddition()) {
            added.setPropertyValue(SVNProperty.COPYFROM_REVISION, SVNProperty.toString(revision));
            added.setPropertyValue(SVNProperty.COPYFROM_URL, url);
        } else if (toCopy.getPropertyValue(SVNProperty.COPYFROM_URL) != null) {
            added.setPropertyValue(SVNProperty.COPYFROM_REVISION, toCopy.getPropertyValue(SVNProperty.COPYFROM_REVISION));
            added.setPropertyValue(SVNProperty.COPYFROM_URL, toCopy.getPropertyValue(SVNProperty.COPYFROM_URL));
        }
        if (myDeletedEntries != null && myDeletedEntries.containsKey(asName)) {
            added.setPropertyValue(SVNProperty.DELETED, Boolean.TRUE.toString());
            myDeletedEntries.remove(asName);
        }
        if (!toCopy.isScheduledForAddition() || toCopy.getPropertyValue(SVNProperty.COPYFROM_URL) != null) {
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
        File dir = getRootEntry().getWorkingCopyFile(entry);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
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
        return new LinkedList(myChildren.values()).iterator();
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
    
    public boolean revert(String childName) throws SVNException {
        DebugLog.log("REVERT: " + childName + " in " + getPath());
        ISVNEntry child = getChild(childName);
        if (child == null) {
            return false;
        }
        myUnmanagedChildren = null;
        myAllUnmanagedChildren = null;
        if (child.isMissing() && child.isDirectory()) {
            Map childProperties = (Map) myChildEntries.get(child.getName());
            if (SVNProperty.SCHEDULE_ADD.equals(childProperties.get(SVNProperty.SCHEDULE))) {
                boolean keepInfo = childProperties.get(SVNProperty.DELETED) != null;
                deleteChild(child.getName(), keepInfo);
                return true;
            }
            return false;
        }
        
        if (child.isScheduledForAddition()) {
            myChildEntries.remove(childName);
            myChildren.remove(childName);
            if (child.isDirectory()) {
                File adminArea = getAdminArea().getAdminArea(child);
                FSUtil.deleteAll(adminArea);
            }
        } else {
            DebugLog.log("REVERT: reverting file, base file is: " + getAdminArea().getBaseFile(child));
            if (!child.isDirectory() && !getAdminArea().getBaseFile(child).exists()) {
                DebugLog.log("REVERT: no base file to revert contents from");
                return false;
            }
            unschedule(childName);
            ((FSEntry) child).revertProperties();
            if (!child.isDirectory()) {
                DebugLog.log("REVERT: reverting file contents");
                ((FSFileEntry) child).restoreContents();
            } else if (child.getPropertyValue(SVNProperty.PROP_TIME) != null) {
                if (myChildEntries != null) {
                    Map entryInParent = (Map) myChildEntries.get(childName);
                    if (entryInParent != null) {
                        entryInParent.put(SVNProperty.PROP_TIME, child.getPropertyValue(SVNProperty.PROP_TIME));
                    }
                }
            }
            DebugLog.log("REVERT: contents reverted");
        }
        return true;
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
                long rollbackRevision = -1; 
                if (child.isScheduledForAddition() || child.isScheduledForDeletion() || isCorrupted) {
                    // obstructed!
                    obstructedChildren.add(child.getName());
                    // if it is copied, update revision for children, but not for this entry!
                    if (child.getPropertyValue(SVNProperty.COPIED) != null && child.isDirectory()) {
                        rollbackRevision = SVNProperty.longValue(child.getPropertyValue(SVNProperty.REVISION));
                        child.setPropertyValue(SVNProperty.REVISION, revision);
                    }
                } else {
                    child.setPropertyValue(SVNProperty.REVISION, revision);
                }
                
                child.merge(recursive);
                if (rollbackRevision >= 0) {
                    child.setPropertyValue(SVNProperty.REVISION, SVNProperty.toString(rollbackRevision));
                    rollbackRevision = -1;
                    child.save();
                }
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
        if (isMissing()) {
            return;
        }
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
        DebugLog.log("SAVING ENTRIES FOR " + getPath());
        DebugLog.log("MISSING " + isMissing());
        if (myDirEntry != null && !isMissing()) {
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
        Collection unmanagedChildren = includeIgnored ? myAllUnmanagedChildren.values() : myUnmanagedChildren.values();
        return new ArrayList(unmanagedChildren).iterator();
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
                // if it is not external!
                ((FSEntry) childEntry).setManaged(childEntry.getPropertyValue(SVNProperty.URL) != null);
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
        DebugLog.log("DELETING: " + name + " from " + getPath());
        ISVNEntry entry = getChild(name);
        if (entry == null) {
            // force file deletion
            if (moved) {
                DebugLog.log("DELETING UNMANAGED CHILD: " + name + " from " + getPath());
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
            for(Iterator children = ((ISVNDirectoryEntry) entry).unmanagedChildEntries(true); children.hasNext();) {
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
    
    public boolean isManaged(String name) throws SVNException {
    	loadEntries();
    	return myChildEntries.containsKey(name);
    }
    
    public boolean isIgnored(String name) throws SVNException {
        String ignored = getRootEntry().getGlobalIgnore();
        for(StringTokenizer tokens = new StringTokenizer(ignored, " \t\n\r"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (token.length() == 0) {
                continue;
            }
            if (ignoreMatches(token, name)) {
                return true;
            }
        }        
        ignored = getPropertyValue(SVNProperty.IGNORE);
        if (ignored != null) {
            for(StringTokenizer tokens = new StringTokenizer(ignored, "\n\r"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                if (token.length() == 0) {
                    continue;
                }
                if (ignoreMatches(token, name)) {
                    return true;
                }
            }        
        }
        return false;        
    }
    
    private static boolean ignoreMatches(String token, String name) {
        token = token.replaceAll("\\.", "\\\\.");
        token = token.replaceAll("\\*", ".*");
        token = token.replaceAll("\\?", ".");
        return Pattern.matches(token, name);
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

    public static void setPropertyValueRecursively(ISVNEntry root, String name, String value) throws SVNException {
        root.setPropertyValue(name, value);
        if (root.isDirectory()) {
            for(Iterator children = root.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                if (SVNProperty.COPIED.equals(name) && child instanceof FSDirEntry) {
                    if (value != null) {
                        ((Map) ((FSDirEntry) root).myChildEntries.get(child.getName())).put(name, value);
                    } else {
                        ((Map) ((FSDirEntry) root).myChildEntries.get(child.getName())).remove(name);
                    }
                }                    
                setPropertyValueRecursively(child, name, value);
            }
        }
    }
    
    public static void updateURL(ISVNEntry target, String parentURL) throws SVNException {
        parentURL = PathUtil.append(parentURL, PathUtil.encode(target.getName()));
        target.setPropertyValue(SVNProperty.URL, parentURL);
        DebugLog.log("url set: " + parentURL);
        if (target.isDirectory()) {
            for(Iterator children = target.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                updateURL(child, parentURL);
            }
        }
    }

    private static void updateDeletedEntries(ISVNEntry target) throws SVNException {
        if (target.isDirectory()) {
            FSDirEntry dir = (FSDirEntry) target;
            for(Iterator deletedEntries = dir.deletedEntries(); deletedEntries.hasNext();) {
                Map deletedEntry = (Map) deletedEntries.next();
                // remove deleted entry in dir and replace it with deleted entry
                String name = (String) deletedEntry.get(SVNProperty.NAME);

                deletedEntry.remove(SVNProperty.DELETED);
                deletedEntry.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
                deletedEntry.put(SVNProperty.COPIED, "true");
                deletedEntry.put(SVNProperty.KIND, SVNProperty.KIND_FILE);
                
                dir.myDeletedEntries.remove(name);
                dir.myChildEntries.put(name, deletedEntry);
            }
            for(Iterator children = target.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                updateDeletedEntries(child);
            }
        }
    }

	public ISVNEntryContent getContent() throws SVNException {
		return new FSDirEntryContent(this);
	}
}
