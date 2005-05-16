package org.tmatesoft.svn.core.internal.ws.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNProperty;

public class SVNEntries {
    
    private File myFile;
    private Map myData;
    private Set myEntries;

    public SVNEntries(File entriesFile) {
        myFile = entriesFile;
    }
    
    public void open() throws IOException {        
        if (myData != null) {
            return;
        }
        myData = new TreeMap();
        myEntries = new TreeSet();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(myFile));
            String line;
            Map entry = null;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<entry")) {
                    entry = new HashMap();
                    continue;
                } 
                if (entry != null) {
                    String name = line.substring(0, line.indexOf('='));
                    String value = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
                    value = SVNTranslator.xmlDecode(value);
                    entry.put(SVNProperty.SVN_ENTRY_PREFIX + name, value);
                    if (line.endsWith("/>")) {
                        String entryName = (String) entry.get(SVNProperty.NAME); 
                        myData.put(entryName, entry);
                        myEntries.add(new SVNEntry(this, entryName));
                        entry = null;
                    }
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {}
            }
        }
    }
    
    public void save() throws IOException {
        if (myData == null) {
            return;
        }
        Writer os = null;
        try {
            os = new FileWriter(myFile);
            os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            os.write("<wc-entries\n");
            os.write("   xmlns=\"svn:\">\n");
            for (Iterator entries = myData.keySet().iterator(); entries.hasNext();) {
                String name = (String) entries.next();
                Map entry = (Map) myData.get(name);
                os.write("<entry\n");
                for (Iterator names = entry.keySet().iterator(); names.hasNext();) {
                    String propName = (String) names.next();
                    String propValue = (String) entry.get(propName);
                    propName = propName.substring(SVNProperty.SVN_ENTRY_PREFIX.length());
                    propValue = SVNTranslator.xmlEncode(propValue);  
                    os.write("   ");
                    os.write(propName);
                    os.write("=\"");
                    os.write(propValue);
                    os.write("\"");
                    if (!names.hasNext()) {
                        os.write("/>");
                    }
                    os.write("\n");
                }
            }
            os.write("</wc-entries>\n");
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
            close();
        }
    }

    public void close() {
        myData = null;
        myEntries = null;        
    }
    
    public String getPropertyValue(String name, String propertyName) {
        if (myData == null) {
            return null;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            return (String) entry.get(propertyName);
        }
        return null;
    }
    
    public void setPropertyValue(String name, String propertyName, String propertyValue) {        
        if (myData == null) {
            return;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            if (propertyValue == null) {
                entry.remove(propertyName);
            } else {
                entry.put(propertyName, propertyValue);
            }
        }
    }
    
    public Iterator entries() {
        if (myEntries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        return myEntries.iterator();
    }
    
    public SVNEntry getEntry(String name) {
        if (myData.containsKey(name)) {
            return new SVNEntry(this, name);
        }
        return null;
    }
    
    public SVNEntry addEntry(String name) {
        if (myData != null && !myData.containsKey(name)) {
            myData.put(name, new HashMap());
            SVNEntry entry = new SVNEntry(this, name);
            myEntries.add(entry);
            return entry;
        }
        return null;
    }

    public void deleteEntry(String name) {
        if (myData != null) {
            myData.remove(name);
        }
        myEntries.remove(new SVNEntry(this, name));
    }
}
