package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNDirectory {
    
    private File myDirectory;
    private SVNEntries myEntries;
    
    private Map myProperties;
    private Map myBaseProperties;
    private Map myWCProperties;
    private SVNWCAccess myWCAccess;
    private String myPath;

    public SVNDirectory(SVNWCAccess wcAccess, String path, File dir) {
        myDirectory = dir;
    }

    public SVNDirectory[] getChildDirectories() {
        return myWCAccess.getChildDirectories(myPath);
    }

    public SVNDirectory getChildDirectory(String name) {
        return myWCAccess.getDirectory("".equals(myPath) ? name : PathUtil.append(myPath, name));
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
        }
        myEntries.open();
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
    
    public boolean hasTextModifications(String name, boolean force) throws SVNException {
        if (!getFile(name).isFile()) {
            return false;
        }
        SVNEntries entries = getEntries();
        if (entries == null || entries.getEntry(name) == null) {
            return false;
        }
        SVNEntry entry = entries.getEntry(name);
        if (entry.isDirectory()) {
            return false;
        }
        if (!force) {
            String textTime = entry.getTextTime();
            long tstamp = TimeUtil.parseDate(textTime).getTime();
            if (tstamp == getFile(name).lastModified()) {
                return false;
            }
        } 
        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }
        // translate versioned file.
        File baseTmpFile = getBaseFile(name, true);
        File versionedFile = getFile(name); 
        byte[] eol = getProperties(name).getPropertyValue(SVNProperty.EOL_STYLE) == null ? 
                null : SVNTranslator.LF;
        String keywords = getProperties(name).getPropertyValue(SVNProperty.KEYWORDS);
        boolean special = getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
        SVNTranslator.translate(versionedFile, baseTmpFile, eol, 
                SVNTranslator.computeKeywords(keywords, null, null, null, -1), special, false);
        
        // now compare file and get base file checksum (when forced)
        MessageDigest digest;
        boolean equals = true;
        try {
            digest = force ? MessageDigest.getInstance("MD5") : null;
            equals = SVNFileUtil.compareFiles(baseFile, baseTmpFile, digest);
            if (force) {
                // if checksum differs from expected - throw exception
                String checksum = SVNFileUtil.toHexDigest(digest);
                if (!checksum.equals(entry.getChecksum())) {
                    SVNErrorManager.error(10, null);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            SVNErrorManager.error(0, e);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
        
        if (equals && isLocked()) {
            entry.setTextTime(TimeUtil.formatDate(new Date(versionedFile.lastModified())));
            entries.save(true);
        }        
        return !equals;
    }
    
    private static String compareFiles(File baseFile, File versionedFile) {
        // 1. translate versioned file 
        return null;
    }
    
    public void dispose() {
        if (myEntries != null) {
            myEntries.close();
        }
        myEntries = null;
        myProperties = null;
        myBaseProperties = null;
        myWCProperties = null;        
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
            index++;
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
        
        String childPath = PathUtil.append(myPath, name);
        childPath = PathUtil.removeLeadingSlash(childPath);

        SVNDirectory child = myWCAccess.addDirectory(childPath, dir);
        SVNEntry rootEntry = child.getEntries().addEntry("");
        rootEntry.setURL(url);
        rootEntry.setRevision(revision);
        rootEntry.setKind(SVNNodeKind.DIR);
        child.getEntries().save(true);
        return child;
    }
    
    public void destroy(String name, boolean deleteWorkingFiles) throws SVNException {
        if ("".equals(name)) {
            SVNDirectory parent = null;
            if ("".equals(myPath)) {
                SVNWCAccess parentWCAccess = null;
                try {
                    parentWCAccess = SVNWCAccess.create(getRoot().getParentFile());
                    parentWCAccess.open(true, false);
                    parent = parentWCAccess.getAnchor();
                    destroyDirectory(parent, this, deleteWorkingFiles);
                } catch (SVNException e) {
                    parent = null;
                } finally {
                    if (parentWCAccess != null) {
                        parentWCAccess.close(true, false);
                    }
                }
                if (parent != null) {
                    return;
                }
            } else {
                String parentPath = PathUtil.removeTail(myPath);
                parent = myWCAccess.getDirectory(parentPath);
                if (parent != null && !parent.isVersioned()) {
                    parent = null;
                }
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
    
    public void setWCAccess(SVNWCAccess wcAccess, String path) {
        myWCAccess = wcAccess;
        myPath = path;
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
        
        if (deleteWorkingFile && !hasTextModifications(name, false)) {
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
