/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {
    public static SVNAdminArea MISSING = new SVNAdminArea(null) {
        public boolean unlock() throws SVNException {
            return false;
        }
        
        public void removeFromRevisionControl(String name, boolean deleteWorkingFiles, boolean reportError) throws SVNException {
        }

        public boolean hasTextModifications(String name, boolean forceComparison) throws SVNException {
            return false;
        }

        public void saveVersionedProperties(ISVNLog log, boolean close) throws SVNException {
        }

        public void saveWCProperties(boolean close) throws SVNException {
        }

        public SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException {
            return this;
        }

        public void saveEntries(boolean close) throws SVNException {
        }

        public void runLogs() throws SVNException {
        }

        public SVNAdminArea createVersionedDirectory() throws SVNException {
            return this;
        }

        public ISVNLog getLog() {
            return null;
        }

        public boolean isLocked() {
            return false;
        }

        public boolean isVersioned() {
            return false;
        }

        public ISVNProperties getBaseProperties(String name) throws SVNException {
            return null;
        }

        public ISVNProperties getWCProperties(String name) throws SVNException {
            return null;
        }

        public ISVNProperties getProperties(String name) throws SVNException {
            return null;
        }

        public void save(boolean close) throws SVNException {
        }

        public String getThisDirName() {
            return null;
        }

        public boolean hasPropModifications(String entryName) throws SVNException {
            return false;
        }

        public boolean hasProperties(String entryName) throws SVNException {
            return false;
        }

        public InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException {
            return SVNFileUtil.DUMMY_IN;
        }

        public OutputStream getBaseFileForWriting(String name) throws SVNException {
            return SVNFileUtil.DUMMY_OUT;
        }

        public boolean lock() throws SVNException {
            return false;
        }
        
        protected void writeEntries(Writer writer) throws IOException {
        }

        protected int getFormatVersion() {
            return -1; 
        }

        protected Map fetchEntries() throws SVNException {
            return null;
        }

    };

    private File myDirectory;
    private SVNWCAccess2 myWCAccess;
    private File myAdminRoot;
    protected Map myBaseProperties;
    protected Map myProperties;
    protected Map myWCProperties;
    protected Map myEntries;

    public abstract boolean isLocked() throws SVNException;

    public abstract boolean isVersioned();

    public abstract boolean lock() throws SVNException;

    public abstract boolean unlock() throws SVNException;

    public abstract ISVNProperties getBaseProperties(String name) throws SVNException;

    public abstract ISVNProperties getWCProperties(String name) throws SVNException;

    public abstract ISVNProperties getProperties(String name) throws SVNException;

    public abstract void saveVersionedProperties(ISVNLog log, boolean close) throws SVNException;

    public abstract void saveWCProperties(boolean close) throws SVNException;

    public abstract void saveEntries(boolean close) throws SVNException;

    public abstract void save(boolean close) throws SVNException;

    public abstract String getThisDirName();

    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    public abstract boolean hasProperties(String entryName) throws SVNException;

    public abstract InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException;

    public abstract OutputStream getBaseFileForWriting(String name) throws SVNException;

    public abstract ISVNLog getLog();
    
    public abstract SVNAdminArea createVersionedDirectory() throws SVNException;
    
    public abstract void removeFromRevisionControl(String name, boolean deleteWorkingFiles, boolean reportError) throws SVNException;

    public abstract boolean hasTextModifications(String name, boolean forceComparison) throws SVNException;

    public abstract SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException;
    
    public abstract void runLogs() throws SVNException;

    public void foldScheduling(String name, String schedule) throws SVNException {
        SVNEntry entry = getEntry(name, true);
        
        if (entry == null && schedule != SVNProperty.SCHEDULE_ADD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is not under version control", name); 
            SVNErrorManager.error(err);
        } else {
            entry = addEntry(name);
        }
        SVNEntry thisDirEntry = getEntry(getThisDirName(), true);
        String rootSchedule = thisDirEntry.getSchedule();
        if (!getThisDirName().equals(entry.getName()) && (SVNProperty.SCHEDULE_DELETE.equals(rootSchedule))) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t add ''{0}'' to deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err);
            } else if (SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t replace ''{0}'' in deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err);
            }
        }
           
        if (entry.isAbsent() && SVNProperty.SCHEDULE_ADD.equals(schedule)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is marked as absent, so it cannot be scheduled for addition", name);
            SVNErrorManager.error(err);
        }
            
        if (SVNProperty.SCHEDULE_ADD.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                if (!entry.isDeleted()) {
                    deleteEntry(name);
                    return;
                } 
                entry.unschedule();
            }
        } else if (SVNProperty.SCHEDULE_DELETE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                entry.scheduleForReplacement();
            } 
        } else if (SVNProperty.SCHEDULE_REPLACE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                entry.scheduleForDeletion();
            } 
        } else {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) && !entry.isDeleted()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Entry ''{0}'' is already under version control", name);
                SVNErrorManager.error(err);
            }
        }
    }
    
    public void deleteEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries != null) {
            entries.remove(name);
        }
    }

    public SVNEntry getEntry(String name, boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries != null && entries.containsKey(name)) {
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry addEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            myEntries = new TreeMap(); 
            entries = myEntries;
        }

        SVNEntry entry = entries.containsKey(name) ? (SVNEntry) entries.get(name) : new SVNEntry(new HashMap(), this, name);
        entries.put(name, entry);
        return entry;
    }

    public Iterator entries(boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        Collection copy = new LinkedList(entries.values());
        if (!hidden) {
            for (Iterator iterator = copy.iterator(); iterator.hasNext();) {
                SVNEntry entry = (SVNEntry) iterator.next();
                if (entry.isHidden()) {
                    iterator.remove();
                }
            }
        }
        return copy.iterator();
    }

    public File getRoot() {
        return myDirectory;
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public File getAdminFile(String name) {
        return new File(getAdminDirectory(), name);
    }

    public File getFile(String name) {
        return new File(getRoot(), name);
    }

    public SVNWCAccess2 getWCAccess() {
        return myWCAccess;
    }

    public void setWCAccess(SVNWCAccess2 wcAccess) {
        myWCAccess = wcAccess;
    }
    
    public void closeVersionedProperties() {
        myProperties = null;
        myBaseProperties = null;
    }
    
    public void closeWCProperties() {
        myWCProperties = null;
    }
    
    public void closeEntries() {
        myEntries = null;
    }

    protected abstract void writeEntries(Writer writer) throws IOException;
    
    protected abstract int getFormatVersion();

    protected abstract Map fetchEntries() throws SVNException;

    protected SVNAdminArea(File dir){
        myDirectory = dir;
        myAdminRoot = new File(dir, SVNFileUtil.getAdminDirectoryName());
    }

    protected Map loadEntries() throws SVNException {
        if (myEntries != null) {
            return myEntries;
        }
        myEntries = fetchEntries();
        return myEntries;
    }

    protected Map getBasePropertiesStorage(boolean create) {
        if (myBaseProperties == null && create) {
            myBaseProperties = new HashMap();
        }
        return myBaseProperties;
    }

    protected Map getPropertiesStorage(boolean create) {
        if (myProperties == null && create) {
            myProperties = new HashMap();
        }
        return myProperties;
    }
    
    protected Map getWCPropertiesStorage(boolean create) {
        if (myWCProperties == null && create) {
            myWCProperties = new HashMap();
        }
        return myWCProperties;
    }
    
    public static String asString(String[] array, String delimiter) {
        String str = null;
        if (array != null) {
            str = "";
            for (int i = 0; i < array.length; i++) {
                str += array[i];
                if (i < array.length - 1) {
                    str += delimiter;
                }
            }
        }
        return str;
    }
    
    public static String[] fromString(String str, String delimiter) {
        LinkedList list = new LinkedList(); 
        int startInd = 0;
        int ind = -1;
        while ((ind = str.indexOf(delimiter, startInd)) != -1) {
            list.add(str.substring(startInd, ind));
            startInd = ind;
            while (startInd < str.length() && str.charAt(startInd) == ' '){
                startInd++;
            }
        }
        if (startInd < str.length()) {
            list.add(str.substring(startInd));
        }
        return (String[])list.toArray(new String[list.size()]);
    }
}
