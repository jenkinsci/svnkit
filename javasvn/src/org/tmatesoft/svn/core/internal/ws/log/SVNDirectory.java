package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNException;

public class SVNDirectory {
    
    private File myDirectory;
    private SVNEntries myEntries;
    private Map myProperties;

    public SVNDirectory(File dir) {
        myDirectory = dir;
    }

    public SVNDirectory[] getChildDirectories() {
        File[] children = myDirectory.listFiles();
        Collection directories = new ArrayList();
        for (int i = 0; children != null && i < children.length; i++) {
            if (children[i].isDirectory() && !getAdminDirectory().equals(children[i])) {
                directories.add(new SVNDirectory(children[i]));
            }
        }
        return (SVNDirectory[]) directories.toArray(new SVNDirectory[directories.size()]);
    }

    public SVNDirectory getChildDirectory(String name) {
        File child = new File(myDirectory, name);
        if (child.isDirectory()) {
            return new SVNDirectory(child);
        }
        return null;
    }
    
    public boolean isVersioned() {
        return getAdminDirectory().isDirectory();
    }
    
    public boolean isLocked() {
        return getLockFile().isFile();
    }
    
    public boolean lock() throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        boolean created = false;
        try {
            created = getLockFile().createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
        if (!created) {
            SVNErrorManager.error(0, null);
        }
        return created;
    }

    public boolean unlock() throws SVNException {
        if (!getLockFile().exists()) {
            return true;
        }
        boolean deleted = getLockFile().delete();
        if (!deleted) {
            SVNErrorManager.error(1, null);
        }
        return deleted;
    }
    
    public SVNEntries getEntries() throws SVNException {
        if (myEntries == null) {
            myEntries = new SVNEntries(new File(getAdminDirectory(), "entries"));
            myEntries.open();
        }
        return myEntries;
    }

    public SVNProperties getProperties(String name) {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        if (!myProperties.containsKey(name)) {
            File propertiesFile = "".equals(name) ? 
                    new File(getAdminDirectory(), "dir-props") :
                    new File(getAdminDirectory(), "props/" + name + ".svn-work");
            myProperties.put(name, new SVNProperties(propertiesFile));
        }
        return (SVNProperties) myProperties.get(name);
    }
    
    public void markResolved(String name, boolean text, boolean props) throws SVNException {
        if (!text && !props) {
            return;
        }
        SVNEntry entry = getEntries().getEntry(name);
        if (entry == null) {
            return;
        }
        boolean modified = false;
        if (text && entry.getConflictOld() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictOld());
            file.delete();
            entry.setConflictOld(null);
        }
        if (text && entry.getConflictNew() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictNew());
            file.delete();
            entry.setConflictNew(null);
        }
        if (text && entry.getConflictWorking() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictWorking());
            file.delete();
            entry.setConflictWorking(null);
        }
        if (props && entry.getPropRejectFile() != null) {
            File file = new File(myDirectory, entry.getPropRejectFile());
            file.delete();
            modified = true;
            entry.setPropRejectFile(null);
        }
        if (modified) {
            getEntries().save(false);
        }
    }
    
    public void dispose() {
        if (myEntries != null) {
            myEntries.close();
            myEntries = null;
        }
        myProperties = null;
    }

    private File getLockFile() {
        return new File(getAdminDirectory(), "lock");
    }

    private File getAdminDirectory() {
        return new File(myDirectory, ".svn");
    }

    public File getFile(String name) {
        if ("".equals(name)) {
            return myDirectory;
        }
        return new File(myDirectory, name);
    }

    public File getBaseFile(String name, boolean tmp) {
        if ("".equals(name)) {
            return null;
        }
        File parent = tmp ? new File(getAdminDirectory(), "tmp") : getAdminDirectory();
        return new File(parent, "text-base/" + name + ".svn-base");
    }
}
