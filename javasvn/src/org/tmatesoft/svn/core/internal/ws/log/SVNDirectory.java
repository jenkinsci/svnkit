package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;

public class SVNDirectory {
    
    private File myDirectory;
    private SVNEntries myEntries;
    
    private Map myProperties;
    private Map myBaseProperties;
    private Map myWCProperties;

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

    public SVNProperties getBaseProperties(String name) {
        if (myBaseProperties == null) {
            myBaseProperties = new HashMap();
        }
        if (!myBaseProperties.containsKey(name)) {
            File propertiesFile = "".equals(name) ? 
                    new File(getAdminDirectory(), "dir-prop-base") :
                    new File(getAdminDirectory(), "prop-base/" + name + ".svn-base");
            myBaseProperties.put(name, new SVNProperties(propertiesFile));
        }
        return (SVNProperties) myBaseProperties.get(name);
    }
    
    public SVNProperties getWCProperties(String name) {
        if (myWCProperties == null) {
            myWCProperties = new HashMap();
        }
        if (!myWCProperties.containsKey(name)) {
            File propertiesFile = "".equals(name) ? 
                    new File(getAdminDirectory(), "dir-wcprops") :
                    new File(getAdminDirectory(), "wcprops/" + name + ".svn-work");
            myWCProperties.put(name, new SVNProperties(propertiesFile));
        }
        return (SVNProperties) myWCProperties.get(name);
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
    
    public File getRoot() {
        return myDirectory;
    }
    
    public SVNLog getLog(int id) {
        return new SVNLog(this, id);
    }
    
    public void runLogs() throws SVNException {
        Map logFiles = new TreeMap();
        File dir = getAdminDirectory();
        File[] children = dir.listFiles();
        SVNLogRunner runner = new SVNLogRunner();
        int index = 0;
        while(true) {
            SVNLog log = new SVNLog(this, index);
            if (log.exists()) {
                log.run(runner);
                continue;
            }
            return;
        }
    }

    public SVNDirectory createChildDirectory(String name, String url, long revision) throws SVNException {
        File dir = new File(myDirectory, name);
        dir.mkdirs();
        File adminDir = new File(dir, ".svn");
        adminDir.mkdirs();
        SVNFileUtil.setHidden(adminDir, true);
        
        File format = new File(adminDir, "format");
        OutputStream os = null;
        if (!format.exists()) {
            try {
                os = new FileOutputStream(format);
                os.write(new byte[] {'4', '\n'});
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) { 
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        File readme = new File(adminDir, "README.txt");
        if (!readme.exists()) {
            try {
                os = new FileOutputStream(readme);
                String eol = System.getProperty("line.separator");
                eol = eol == null ? "\n" : eol;
                os.write(("This is a Subversion working copy administrative directory." + eol + 
                "Visit http://subversion.tigris.org/ for more information." + eol).getBytes());
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) { 
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        File empty = new File(adminDir, "empty-file");
        if (!empty.exists()) {
            try {
                empty.createNewFile();
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
        File[] tmp = {
                new File(adminDir, "tmp" + File.separatorChar + "props"),
                new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                new File(adminDir, "tmp" + File.separatorChar + "wcprops"),
                new File(adminDir, "props"),
                new File(adminDir, "prop-base"),
                new File(adminDir, "text-base"),
                new File(adminDir, "wcprops")};
        for(int i = 0; i < tmp.length; i++) {
            if (!tmp[i].exists()) {
                tmp[i].mkdirs();
            }
        }
        
        SVNDirectory child = getChildDirectory(name);
        SVNEntry rootEntry = child.getEntries().addEntry("");
        rootEntry.setURL(url);
        rootEntry.setRevision(revision);
        child.getEntries().save(true);
        return child;
    }
    
    public void destroy(String name, boolean deleteWorkingFiles) throws SVNException {
        if ("".equals(name)) {
            SVNDirectory parent = new SVNDirectory(myDirectory.getParentFile());
            if (!parent.isVersioned()) {
                parent = null;
            }
            destroyDirectory(parent, this, deleteWorkingFiles);
        } else {
            File file = getFile(name);
            if (file.isFile()) {
                destroyFile(name, deleteWorkingFiles);
            } else if (file.exists()) {
                SVNDirectory childDir = getChildDirectory(name);
                if (childDir != null && childDir.isVersioned()) {
                    destroyDirectory(this, childDir, deleteWorkingFiles);
                }
            }
        }
    }
    
    private void destroyFile(String name, boolean deleteWorkingFile) throws SVNException {
        SVNEntries entries = getEntries();
        entries.deleteEntry(name);
        entries.save(true);
        
        File baseFile = getBaseFile(name, false);
        baseFile.delete();
        getProperties(name).delete();
        getBaseProperties(name).delete();
        getWCProperties(name).delete();
        
        // check for local mods.
        if (deleteWorkingFile) {
            getFile(name).delete();
        }
    }
    
    private void destroyDirectory(SVNDirectory parent, SVNDirectory dir, boolean deleteWorkingFiles) throws SVNException {
        SVNEntries entries = dir.getEntries();
        entries.getEntry("").setIncomplete(true);
        entries.save(false);
        
        // iterate over dir's entries, delete files and recurse into dirs
        for (Iterator ents = entries.entries(); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.getKind() == SVNNodeKind.FILE) {
                dir.destroy(entry.getName(), deleteWorkingFiles);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (childDirectory == null) {
                    entries.deleteEntry(entry.getName());
                } else {
                    // recurse
                    destroyDirectory(dir, childDirectory, deleteWorkingFiles);
                }
            }
        }
        entries.save(false);

        // delete dir's entry in parent.
        if (parent != null) {
            SVNEntries parentEntries = parent.getEntries();
            parentEntries.deleteEntry(dir.getRoot().getName());
            parentEntries.save(true);
        }
        // delete all admin files
        SVNFileUtil.deleteAll(new File(dir.getRoot(), ".svn"));
        // attempt to delete dir - will not be deleted if there are wc files left.
        dir.getRoot().delete();
    }
}
