/*
 * Created on Oct 1, 2004
 */
package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.xml.sax.InputSource;

/**
 * @author alex
 */
public class FSAdminArea {
    
    private File myRoot;

    public FSAdminArea(File root) {
        myRoot = root;
    }
    
    protected File getAdminArea(File dir) {
        return new File(dir, ".svn");
    }
    
    protected File getAdminArea(ISVNEntry entry) {
        File adminDir;
        if (entry.isDirectory()) {
            adminDir = new File(myRoot, entry.getPath() + "/.svn");
        } else {
            adminDir = new File(myRoot, PathUtil.removeTail(entry.getPath()) + "/.svn");
        }
        return adminDir;
    }

    protected File initAdminArea(ISVNEntry entry) {
        File adminDir = getAdminArea(entry);
        if (!adminDir.exists()) {
            adminDir.mkdirs();
            FSUtil.setHidden(adminDir, true);
        }
        return adminDir;
    }
    
    public Map loadEntries(ISVNDirectoryEntry entry) throws SVNException {
        File entriesFile = new File(getAdminArea(entry), "entries");
        if (!entriesFile.exists()) {
            return new HashMap();
        }
        Reader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(entriesFile), "UTF-8"));
            InputSource source = new InputSource(reader);
            FSEntryHandler handler = FSEntryHandler.parse(source);
            return handler.getChildEntries();
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void saveEntries(ISVNDirectoryEntry entry, Map dirEntry, Map entries, Map deletedEntries) throws SVNException {
        File adminArea = initAdminArea(entry);
        Writer writer = null;
        File entriesFile = new File(adminArea, "entries");
        FSUtil.setReadonly(entriesFile, false);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entriesFile), "UTF-8"));
            if (deletedEntries == null) {
                deletedEntries = Collections.EMPTY_MAP;
            }
            FSEntryHandler.save(writer, dirEntry, entries.values(), deletedEntries.values());
            writeDefaultFiles(adminArea);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e1) {
                }
            }
        }
    }

    private void writeDefaultFiles(File adminDir) throws IOException {
        File format = new File(adminDir, "format");
        OutputStream os = null;
        if (!format.exists()) {
            try {
                os = new FileOutputStream(format);
                os.write(new byte[] {'4', '\n'});
            } finally {
                os.close();
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
            } finally {
                os.close();
            }
        }
        File empty = new File(adminDir, "empty-file");
        if (!empty.exists()) {
            empty.createNewFile();
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
    }
    
    public void saveBaseProperties(ISVNEntry entry, Map properties) throws SVNException {
        initAdminArea(entry);
        if (properties == null || entry == null || (entry.isDirectory() && properties.isEmpty())) {
            return;
        }
        saveProperties(getBasePropertiesFile(entry), properties);
    }
    
    public void saveWCProperties(ISVNEntry entry, Map properties) throws SVNException {
        initAdminArea(entry);
        if (properties == null || entry == null || (entry.isDirectory() && properties.isEmpty())) {
            return;
        }
        saveProperties(getWCPropertiesFile(entry), properties);
    }
    
    public void saveProperties(ISVNEntry entry, Map properties) throws SVNException {
        initAdminArea(entry);
        if (properties == null || entry == null || (entry.isDirectory() && properties.isEmpty())) {
            return;
        }
        
        saveProperties(getPropertiesFile(entry), properties);
    }

    public void saveTemporaryProperties(ISVNEntry entry, Map properties) throws SVNException {
        initAdminArea(entry);
        if (properties == null || entry == null) {
            return;
        }
        saveProperties(getTemporaryPropertiesFile(entry), properties, true);
    }
    
    public Map loadProperties(ISVNEntry entry) throws SVNException {
        if (entry == null) {
            return null;
        }
        return loadProperties(getPropertiesFile(entry));
    }
    
    public Map loadBaseProperties(ISVNEntry entry) throws SVNException {
        if (entry == null) {
            return null;
        }
        return loadProperties(getBasePropertiesFile(entry));
    }
    
    public Map loadWCProperties(ISVNEntry entry) throws SVNException {
        if (entry == null) {
            return null;
        }
        return loadProperties(getWCPropertiesFile(entry));
    }

    public Map loadTemporaryProperties(ISVNEntry entry) throws SVNException {
        if (entry == null) {
            return null;
        }
        return loadProperties(getTemporaryPropertiesFile(entry));
    }

    public void deleteTemporaryProperties(ISVNEntry entry) {
        if (entry == null) {
            return;
        }
        getTemporaryPropertiesFile(entry).delete();
    }
    
    public long propertiesLastModified(ISVNEntry entry) {
        return getPropertiesFile(entry).lastModified();
    }
    
    protected File getBasePropertiesFile(ISVNEntry entry) { 
        if (entry.isDirectory()) {
            return new File(getAdminArea(entry), "dir-prop-base");
        } 
        return new File(getAdminArea(entry), "prop-base/" + entry.getName() + ".svn-base");
    }

    protected File getWCPropertiesFile(ISVNEntry entry) { 
        if (entry.isDirectory()) {
            return new File(getAdminArea(entry), "dir-wcprops");
        } 
        return new File(getAdminArea(entry), "wcprops/" + entry.getName() + ".svn-work");
    }
  
    protected File getPropertiesFile(ISVNEntry entry) { 
        if (entry.isDirectory()) {
            return new File(getAdminArea(entry), "dir-props");
        } 
        return new File(getAdminArea(entry), "props/" + entry.getName() + ".svn-work");
    }

    protected File getTemporaryPropertiesFile(ISVNEntry entry) { 
        if (entry.isDirectory()) {
            return new File(getAdminArea(entry), "tmp/dir-props");
        } 
        return new File(getAdminArea(entry), "tmp/props/" + entry.getName() + ".svn-work");
    }
    
    protected void saveProperties(File file, Map properties) throws SVNException {
        saveProperties(file, properties, false);
    }
    
    protected void saveProperties(File file, Map properties, boolean saveNull) throws SVNException {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        BufferedWriter writer = null;
        StringBuffer buffer = new StringBuffer();
        FSUtil.setReadonly(file, false);

        try {
            writer = new BufferedWriter(new FileWriter(file));
            if (properties == null) {
                properties = Collections.EMPTY_MAP;
            }
            for(Iterator values = properties.entrySet().iterator(); values.hasNext();) {
                Map.Entry entry = (Map.Entry) values.next();
                if (!saveNull && entry.getValue() == null) {
                    continue;
                }
                buffer.append("K ");
                buffer.append(entry.getKey().toString().length());
                buffer.append("\n");
                buffer.append(entry.getKey().toString());
                if (entry.getValue() != null) {
                    buffer.append("\nV ");
                    buffer.append(entry.getValue().toString().length());
                    buffer.append("\n");
                    buffer.append(entry.getValue().toString());
                }
                buffer.append("\n");
                writer.write(buffer.toString());
                buffer.delete(0, buffer.length());
            }
            writer.write("END\n");
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    protected Map loadProperties(File file) throws SVNException {
        if (file == null || !file.exists()) {
            return new HashMap();
        }
        // load props.
        BufferedReader reader = null;
        Map result = new HashMap();
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = null;
            String name = null;
            String value = null;
            while((line = reader.readLine()) != null) {
                if (line.startsWith("K")) {
                    if (name != null) {
                        result.put(name, value);
                        name = null;
                        value = null;
                    }
                    name = reader.readLine();
                } else if (line.startsWith("V")) {
                    int count = Integer.parseInt(line.substring(1).trim());
                    char[] chars= new char[count];
                    reader.read(chars);
                    value = new String(chars);
                } else if ("END".equals(line)) {
                    if (name != null) {
                        result.put(name, value);
                        name = null;
                        value = null;
                    }
                    break;
                } 
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return result;
    }
    
    public void deleteArea(ISVNEntry entry) {
        File adminDir = getAdminArea(entry);
        if (entry.isDirectory()) {
            FSUtil.deleteAll(adminDir);
        } else {
            File[] adminFiles = new File[] {
                    new File(adminDir, "prop-base/" + entry.getName() + ".svn-base"),
                    new File(adminDir, "text-base/" + entry.getName() + ".svn-base"),
                    new File(adminDir, "props/" + entry.getName() + ".svn-work"),
                    new File(adminDir, "wcprops/" + entry.getName() + ".svn-work"),
            };
            for(int i = 0; i < adminFiles.length; i++) {
                if (!adminFiles[i].delete()) {
                    DebugLog.log("can't delete file " + adminFiles[i].getAbsolutePath());
                }
            }            
        }
    }
    
    public void copyArea(File dst, ISVNEntry src, String asName) throws SVNException {
        File dstAdminArea = getAdminArea(dst);
        File srcAdminArea = getAdminArea(src); 
        try {
            if (!src.isDirectory()) {
                // copy number of admin files for file.
                File baseFile = new File(srcAdminArea, "text-base/" + src.getName() + ".svn-base");
                File propsBaseFile = new File(srcAdminArea, "prop-base/" + src.getName() + ".svn-base");
                File propsFile = new File(srcAdminArea, "props/" + src.getName() + ".svn-work");
                
                FSUtil.copyAll(baseFile,  new File(dstAdminArea, "text-base"), asName + ".svn-base", null);
                FSUtil.copyAll(propsBaseFile,  new File(dstAdminArea, "prop-base"), asName + ".svn-base", null);
                FSUtil.copyAll(propsFile,  new File(dstAdminArea, "props"), asName + ".svn-work", null);
            } else {
                // we got area, copy _some_ files into it.
                dstAdminArea.mkdirs();
                FSUtil.setHidden(dstAdminArea, true);
                
                File propsFile = new File(srcAdminArea, "dir-props");
                File propsBaseFile = new File(srcAdminArea, "dir-prop-base");
                File entriesFile = new File(srcAdminArea, "entries");
                if (propsFile.exists()) {
                    FSUtil.copyAll(propsFile,  dstAdminArea, "dir-props", null);
                }
                if (propsBaseFile.exists()) {
                    FSUtil.copyAll(propsBaseFile,  dstAdminArea, "dir-prop-base", null);
                }
                FSUtil.copyAll(entriesFile,  dstAdminArea, "entries", null);
                writeDefaultFiles(dstAdminArea);
            }
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }
    
    public void deleteTemporaryBaseFile(ISVNEntry entry) {
        File adminArea = initAdminArea(entry);
        File file = new File(adminArea, "tmp/text-base/" + entry.getName() + ".svn-base");
        file.delete();
    }
    
    public File getTemporaryBaseFile(ISVNEntry entry) {
        File adminArea = initAdminArea(entry);
        return new File(adminArea, "tmp/text-base/" + entry.getName() + ".svn-base");
    }
    
    public File getBaseFile(ISVNEntry entry) {
        File adminArea = initAdminArea(entry);
        return new File(adminArea, "text-base/" + entry.getName() + ".svn-base");
    }
    
    public boolean hasBaseFile(ISVNEntry entry) {
        return new File(getAdminArea(entry), "text-base/" + entry.getName() + ".svn-base").exists();
    }
}
