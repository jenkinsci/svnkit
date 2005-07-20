/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEntries {

    private File myFile;
    private Map myData;
    private Set myEntries;

    private static final Set BOOLEAN_PROPERTIES = new HashSet();

    static {
        BOOLEAN_PROPERTIES.add(SVNProperty.COPIED);
        BOOLEAN_PROPERTIES.add(SVNProperty.DELETED);
        BOOLEAN_PROPERTIES.add(SVNProperty.ABSENT);
        BOOLEAN_PROPERTIES.add(SVNProperty.INCOMPLETE);
    }

    public SVNEntries(File entriesFile) {
        myFile = entriesFile;
    }

    public void open() throws SVNException {
        if (myData != null) {
            return;
        }
        if (!myFile.exists()) {
            return;
        }
        myData = new TreeMap();
        myEntries = new TreeSet();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myFile), "UTF-8"));
            String line;
            Map entry = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals("<entry")) {
                    entry = new HashMap();
                    continue;
                }
                if (entry != null) {
                    String name = line.substring(0, line.indexOf('='));
                    String value = line.substring(line.indexOf('\"') + 1, 
                            line.lastIndexOf('\"'));
                    value = SVNTranslator.xmlDecode(value);
                    entry.put(SVNProperty.SVN_ENTRY_PREFIX + name, value);
                    if (line.charAt(line.length() - 1) == '>') {
                        String entryName = (String) entry.get(SVNProperty.NAME);
                        myData.put(entryName, entry);
                        myEntries.add(new SVNEntry(this, entryName));
                        if (!"".equals(entryName)) {
                            Map rootEntry = (Map) myData.get("");
                            if (rootEntry != null) {
                                if (entry.get(SVNProperty.REVISION) == null) {
                                    entry.put(SVNProperty.REVISION, rootEntry.get(SVNProperty.REVISION));
                                }
                                if (entry.get(SVNProperty.URL) == null) {
                                    String url = (String) rootEntry.get(SVNProperty.URL);
                                    if (url != null) {
                                        url = PathUtil.append(url, SVNEncodingUtil.uriEncode(entryName));
                                    }
                                    entry.put(SVNProperty.URL, url);
                                }
                                if (entry.get(SVNProperty.UUID) == null) {
                                    entry.put(SVNProperty.UUID, rootEntry.get(SVNProperty.UUID));
                                }
                            }
                        }
                        entry = null;
                    }
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error("svn: Cannot load entries file '" + myFile + "'");
        } finally {
            SVNFileUtil.closeFile(reader);
        }
    }

    public void save(boolean close) throws SVNException {
        if (myData == null) {
            return;
        }
        Writer os = null;
        File tmpFile = new File(myFile.getParentFile(), "tmp/entries");
        Map rootEntry = (Map) myData.get("");
        try {
            os = new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8");
            os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            os.write("<wc-entries\n");
            os.write("   xmlns=\"svn:\">\n");
            for (Iterator entries = myData.keySet().iterator(); entries
                    .hasNext();) {
                String name = (String) entries.next();
                Map entry = (Map) myData.get(name);
                os.write("<entry");
                for (Iterator names = entry.keySet().iterator(); names
                        .hasNext();) {
                    String propName = (String) names.next();
                    String propValue = (String) entry.get(propName);
                    if (propValue == null) {
                        continue;
                    }
                    if (BOOLEAN_PROPERTIES.contains(propName)
                            && !Boolean.TRUE.toString().equals(propValue)) {
                        continue;
                    }
                    if (!"".equals(name)) {
                        Object expectedValue = null;
                        if (SVNProperty.KIND_DIR.equals(entry
                                .get(SVNProperty.KIND))) {
                            if (SVNProperty.UUID.equals(propName)
                                    || SVNProperty.REVISION.equals(propName)
                                    || SVNProperty.URL.equals(propName)) {
                                continue;
                            }
                        } else {
                            if (SVNProperty.URL.equals(propName)) {
                                expectedValue = PathUtil.append(
                                        (String) rootEntry.get(propName),
                                        SVNEncodingUtil.uriEncode(name));
                            } else if (SVNProperty.UUID.equals(propName)
                                    || SVNProperty.REVISION.equals(propName)) {
                                expectedValue = rootEntry.get(propName);
                            } else {
                                expectedValue = null;
                            }
                            if (propValue.equals(expectedValue)) {
                                continue;
                            }
                        }
                    }
                    propName = propName.substring(SVNProperty.SVN_ENTRY_PREFIX
                            .length());
                    propValue = SVNTranslator.xmlEncode(propValue);
                    os.write("\n   ");
                    os.write(propName);
                    os.write("=\"");
                    os.write(propValue);
                    os.write("\"");
                }
                os.write("/>\n");
            }
            os.write("</wc-entries>\n");
        } catch (IOException e) {
            tmpFile.delete();
            SVNErrorManager.error("svn: Cannot save entries file '" + myFile + "'");
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        SVNFileUtil.rename(tmpFile, myFile);
        SVNFileUtil.setReadonly(myFile, true);
        if (close) {
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

    public boolean setPropertyValue(String name, String propertyName,
            String propertyValue) {
        if (myData == null) {
            return false;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            if (SVNProperty.SCHEDULE.equals(propertyName)) {
                if (SVNProperty.SCHEDULE_DELETE.equals(propertyValue)) {
                    if (SVNProperty.SCHEDULE_ADD.equals(entry
                            .get(SVNProperty.SCHEDULE))) {
                        if (entry.get(SVNProperty.DELETED) == null) {
                            deleteEntry(name);
                        } else {
                            entry.remove(SVNProperty.SCHEDULE);
                        }
                        return true;
                    }
                }
            }
            if (propertyValue == null) {
                return entry.remove(propertyName) != null;
            } else {
                return entry.put(propertyName, propertyValue) != null;
            }
        }
        return false;
    }

    public Iterator entries(boolean hidden) {
        if (myEntries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        Collection copy = new LinkedList(myEntries);
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

    public SVNEntry getEntry(String name, boolean hidden) {
        if (myData != null && myData.containsKey(name)) {
            SVNEntry entry = new SVNEntry(this, name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry addEntry(String name) {
        if (myData == null) {
            myData = new TreeMap();
            myEntries = new TreeSet();
        }
        if (myData != null) {
            Map map = myData.containsKey(name) ? (Map) myData.get(name)
                    : new HashMap();
            myData.put(name, map);
            SVNEntry entry = new SVNEntry(this, name);
            myEntries.add(entry);
            setPropertyValue(name, SVNProperty.NAME, name);
            return entry;
        }
        return null;
    }

    public void deleteEntry(String name) {
        if (myData != null) {
            myData.remove(name);
            myEntries.remove(new SVNEntry(this, name));
        }
    }

    Map getEntryMap(String name) {
        if (myData != null && name != null) {
            return (Map) myData.get(name);
        }
        return null;
    }
}
