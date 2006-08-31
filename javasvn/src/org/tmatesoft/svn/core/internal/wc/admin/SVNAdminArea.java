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
import java.util.ArrayList;
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

        public SVNAdminArea createVersionedDirectory() throws SVNException {
            return this;
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

    public abstract String getThisDirName();

    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    public abstract boolean hasProperties(String entryName) throws SVNException;

    public abstract SVNAdminArea createVersionedDirectory() throws SVNException;
    
    public abstract boolean hasTextModifications(String name, boolean forceComparison) throws SVNException;

    public abstract SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException;
    
    public InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        File baseFile = getAdminFile(path);
        return SVNFileUtil.openFileForReading(baseFile);
    }

    public OutputStream getBaseFileForWriting(String name) throws SVNException {
        final String fileName = name;
        final File tmpFile = getBaseFile(name, true);
        try {
            final OutputStream os = SVNFileUtil.openFileForWriting(tmpFile);
            return new OutputStream() {
                private String myName = fileName;
                private File myTmpFile = tmpFile;
                
                public void write(int b) throws IOException {
                    os.write(b);
                }
                
                public void write(byte[] b) throws IOException {
                    os.write(b);
                }
                
                public void write(byte[] b, int off, int len) throws IOException {
                    os.write(b, off, len);
                }
                
                public void close() throws IOException {
                    os.close();
                    File baseFile = getBaseFile(myName, false);
                    try {
                        SVNFileUtil.rename(myTmpFile, baseFile);
                    } catch (SVNException e) {
                        throw new IOException(e.getMessage());
                    }
                    SVNFileUtil.setReadonly(baseFile, true);
                }
            }; 
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Your .svn/tmp directory may be missing or corrupt; run 'svn cleanup' and try again");
            SVNErrorManager.error(err);
        }
        return null;
    }

    public ISVNLog getLog() {
        int index = 0;
        File logFile = null;
        File tmpFile = null;
        while (true) {
            logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
            if (logFile.exists()) {
                index++;
                continue;
            }
            tmpFile = getAdminFile("tmp/log" + (index == 0 ? "" : "." + index));
            return new SVNLog2(logFile, tmpFile, this);
        }
    }

    public void runLogs() throws SVNException {
        SVNLogRunner2 runner = new SVNLogRunner2();
        int index = 0;
        Collection processedLogs = new ArrayList();
        // find first, not yet executed log file.
        ISVNLog log = null;
        try {
            File logFile = null;
            while (true) {
                getWCAccess().checkCancelled();
                logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
                log = new SVNLog2(logFile, null, this);
                if (log.exists()) {
                    log.run(runner);
                    processedLogs.add(log);
                    index++;
                    continue;
                }
                break;
            }
        } catch (SVNException e) {
            // to save modifications made to .svn/entries
            runner.logFailed(this);
            deleteLogs(processedLogs);
            int newIndex = 0;
            while (true && index != 0) {
                File logFile = getAdminFile("log." + index);
                if (logFile.exists()) {
                    File newFile = getAdminFile(newIndex == 0 ? "log" : "log." + newIndex);
                    SVNFileUtil.rename(logFile, newFile);
                    newIndex++;
                    index++;
                    continue;
                }
                break;
            }
            throw e;
        }
        runner.logCompleted(this);
        deleteLogs(processedLogs);
    }

    public void removeFromRevisionControl(String name, boolean deleteWorkingFiles, boolean reportInstantError) throws SVNException {
        getWCAccess().checkCancelled();
        boolean isFile = !getThisDirName().equals(name);
        if (!isFile) {
            removeThisDirectory(deleteWorkingFiles, reportInstantError);
        } else {
            removeFile(name, deleteWorkingFiles, reportInstantError);
        }
    }

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

    protected File getBaseFile(String name, boolean tmp) {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        return getAdminFile(path);
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

    private void removeThisDirectory(boolean deleteWorkingFiles, boolean reportInstantError) throws SVNException {
        SVNWCAccess2 access = getWCAccess(); 
        access.checkCancelled();
        boolean leftSomething = false;
        SVNEntry thisDirEntry = getEntry(getThisDirName(), true);
        thisDirEntry.setIncomplete(true);
        saveEntries(false);
        
        Map wcProps = getWCPropertiesStorage(true);
        if (wcProps.size() > 0) {
            wcProps.clear();
        }
        saveWCProperties(true);
        
        for (Iterator entries = entries(false); entries.hasNext();) {
            SVNEntry childEntry = (SVNEntry) entries.next();
            if (childEntry.isFile()) {
                try {
                    removeFile(childEntry.getName(), deleteWorkingFiles, reportInstantError);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                        if (reportInstantError) {
                            throw svne;
                        }
                        leftSomething = true;
                    } else {
                        throw svne;
                    }
                }
            } else if (childEntry.isDirectory() && !getThisDirName().equals(childEntry.getName())) {
                File childPath = getFile(childEntry.getName());
                if (access.isMissing(childPath)) {
                    deleteEntry(childEntry.getName());
                } else {
                    SVNAdminArea childArea = access.retrieve(childPath);
                    try {
                        childArea.removeFromRevisionControl(childEntry.getName(), deleteWorkingFiles, reportInstantError);
                    } catch (SVNException svne) {
                        if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                            if (reportInstantError) {
                                throw svne;
                            }
                            leftSomething = true;
                        } else {
                            throw svne;
                        }
                    }
                }
            }
        }
        
        if (!access.isWCRoot(getRoot())) {
            SVNAdminArea parentArea = access.retrieve(getRoot().getParentFile());
            parentArea.deleteEntry(getRoot().getName());
            parentArea.saveEntries(false);
        }
        
        destroyAdminArea();
        if (deleteWorkingFiles && !leftSomething) {
            getRoot().delete();
        }
        if (leftSomething) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED);
            SVNErrorManager.error(err);
        }
    }
    
    private void destroyAdminArea() throws SVNException {
        if (!isLocked()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Write-lock stolen in ''{0}''", getRoot());
            SVNErrorManager.error(err);
        }
        SVNFileUtil.deleteAll(getAdminDirectory(), getWCAccess());
    }
    
    private void removeFile(String name, boolean deleteWorkingFiles, boolean reportInstantError) throws SVNException {
        getWCAccess().checkCancelled();
        boolean hasLocalMods = hasTextModifications(name, false); 
        if (hasLocalMods && reportInstantError) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD, "File ''{0}'' has local modifications", getFile(name));
            SVNErrorManager.error(err);
        }

        ISVNProperties wcProps = getWCProperties(name);
        if (wcProps != null && !wcProps.isEmpty()) {
            wcProps.removeAll();
            saveWCProperties(false);
        }
        
        deleteEntry(name);
        saveEntries(false);
        
        File baseFile = getBaseFile(name, false);
        baseFile.delete();

        File basePropsFile = getAdminFile("prop-base/" + name + ".svn-base");
        basePropsFile.delete();
        
        File propertiesFile = getAdminFile("props/" + name + ".svn-work");
        propertiesFile.delete();
        
        if (deleteWorkingFiles) {
            if (hasLocalMods) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD);
                SVNErrorManager.error(err);
            } else {
                File workingFile = getFile(name);
                workingFile.delete();
            }
        }
    }

    private static void deleteLogs(Collection logsList) {
        for (Iterator logs = logsList.iterator(); logs.hasNext();) {
            ISVNLog log = (ISVNLog) logs.next();
            log.delete();
        }
    }

}
