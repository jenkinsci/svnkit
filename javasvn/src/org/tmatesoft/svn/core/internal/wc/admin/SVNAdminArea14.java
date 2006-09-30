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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;

public class SVNAdminArea14 extends SVNAdminArea {
    public static final String[] ourCachableProperties = new String[] {
        SVNProperty.SPECIAL,
        SVNProperty.EXTERNALS, 
        SVNProperty.NEEDS_LOCK
    };

    public static final int WC_FORMAT = 8;
    
    private static final String ATTRIBUTE_COPIED = "copied";
    private static final String ATTRIBUTE_DELETED = "deleted";
    private static final String ATTRIBUTE_ABSENT = "absent";
    private static final String ATTRIBUTE_INCOMPLETE = "incomplete";
    private static final String THIS_DIR = "";

    private File myLockFile;
    private File myEntriesFile;
    
    public SVNAdminArea14(File dir) {
        super(dir);
        myLockFile = new File(getAdminDirectory(), "lock");
        myEntriesFile = new File(getAdminDirectory(), "entries");
    }

    public static String[] getCachableProperties() {
        return ourCachableProperties;
    }
    
    public void saveWCProperties(boolean close) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(false);
        if (wcPropsCache == null) {
            return;
        }
        
        boolean hasAnyProps = false;
        File dstFile = getAdminFile("all-wcprops");
        File tmpFile = getAdminFile("tmp/all-wcprops");

        for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(name);
            if (!props.isEmpty()) {
                hasAnyProps = true;
                break;
            }
        }

        if (hasAnyProps) {
            OutputStream target = null;
            try {
                target = SVNFileUtil.openFileForWriting(tmpFile);
                SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(getThisDirName());
                if (props != null && !props.isEmpty()) {
                    SVNProperties.setProperties(props.asMap(), target, SVNProperties.SVN_HASH_TERMINATOR);
                } else {
                    SVNProperties.setProperties(Collections.EMPTY_MAP, target, SVNProperties.SVN_HASH_TERMINATOR);
                }
    
                for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
                    String name = (String)entries.next();
                    if (getThisDirName().equals(name)) {
                        continue;
                    }
                    props = (SVNVersionedProperties)wcPropsCache.get(name);
                    if (!props.isEmpty()) {
                        target.write(name.getBytes("UTF-8"));
                        target.write('\n');
                        SVNProperties.setProperties(props.asMap(), target, SVNProperties.SVN_HASH_TERMINATOR);
                    }
                }
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            } finally {
                SVNFileUtil.closeFile(target);
            }
            SVNFileUtil.rename(tmpFile, dstFile);
            SVNFileUtil.setReadonly(dstFile, true);
        } else {
            SVNFileUtil.deleteFile(dstFile);
        }
        if (close) {
            closeWCProperties();
        }
    }
    
    public SVNVersionedProperties getBaseProperties(String name) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)basePropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map baseProps = null;
        try {
            baseProps = readBaseProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }

        props = new SVNProperties13(baseProps);
        basePropsCache.put(name, props);
        return props;
    }

    public SVNVersionedProperties getProperties(String name) throws SVNException {
        Map propsCache = getPropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)propsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        final String entryName = name;
        props =  new SVNProperties14(null, this, name){

            protected Map loadProperties() throws SVNException {
                Map props = getPropertiesMap();
                if (props == null) {
                    try {
                        props = readProperties(entryName);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
                        SVNErrorManager.error(err);
                    }
                    props = props != null ? props : new HashMap();
                    setPropertiesMap(props);
                }
                return props;
            }
        };

        propsCache.put(name, props);
        return props;
    }

    public SVNVersionedProperties getWCProperties(String entryName) throws SVNException {
        SVNEntry2 entry = getEntry(entryName, false);
        if (entry == null) {
            return null;
        } 
        
        Map wcPropsCache = getWCPropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(entryName); 
        if (props != null) {
            return props;
        }
        
        if (wcPropsCache.isEmpty()) {
            wcPropsCache = readAllWCProperties();
        }

        props = (SVNVersionedProperties)wcPropsCache.get(entryName); 
        if (props == null) {
            props = new SVNProperties13(new HashMap());
            wcPropsCache.put(entryName, props);
        }
        return props;
    }
    
    private Map readAllWCProperties() throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(true);
        wcPropsCache.clear();
        File propertiesFile = getAdminFile("all-wcprops");
        if (!propertiesFile.exists()) {
            return wcPropsCache;
        } 

        FSFile wcpropsFile = null;
        try {
            wcpropsFile = new FSFile(propertiesFile);
            Map wcProps = wcpropsFile.readProperties(false);
            SVNVersionedProperties entryWCProps = new SVNProperties13(wcProps); 
            wcPropsCache.put(getThisDirName(), entryWCProps);
            
            String name = null;
            StringBuffer buffer = new StringBuffer();
            while(true) {
                try {
                    name = wcpropsFile.readLine(buffer);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF && buffer.length() > 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing end of line in wcprops file for ''{0}''", getRoot());
                        SVNErrorManager.error(err, e);
                    }
                    break;
                }
                wcProps = wcpropsFile.readProperties(false);
                entryWCProps = new SVNProperties13(wcProps);
                wcPropsCache.put(name, entryWCProps);
                buffer.delete(0, buffer.length());
            }
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        } finally {
            wcpropsFile.close();
        }
        return wcPropsCache;
    }
    
    private Map readBaseProperties(String name) throws SVNException {
        File propertiesFile = getBasePropertiesFile(name, false);
        SVNProperties props = new SVNProperties(propertiesFile, null);
        return props.asMap();
    }
    
    private Map readProperties(String name) throws SVNException {
        if (hasPropModifications(name)) {
            File propertiesFile = getPropertiesFile(name, false);
            SVNProperties props = new SVNProperties(propertiesFile, null);
            return props.asMap();
        } 
            
        Map basePropsCache = getBasePropertiesStorage(false);
        if (basePropsCache != null ) {
            SVNVersionedProperties baseProps = (SVNVersionedProperties) basePropsCache.get(name);
            if (baseProps != null) {
                return baseProps.asMap();
            }
        } else if (hasProperties(name)) {
            return readBaseProperties(name);
        }        
        return new HashMap();
    }

    public void saveVersionedProperties(ISVNLog log, boolean close) throws SVNException {
        Map command = new HashMap();
        Map processedEntries = new TreeMap();
        
        Map propsCache = getPropertiesStorage(false);
        if (propsCache != null && !propsCache.isEmpty()) {
            for(Iterator entries = propsCache.keySet().iterator(); entries.hasNext();) {
                String name = (String)entries.next();
                SVNVersionedProperties props = (SVNVersionedProperties)propsCache.get(name);
                if (props.isModified()) {
                    SVNVersionedProperties baseProps = getBaseProperties(name);
                    SVNVersionedProperties propsDiff = baseProps.compareTo(props);
                    String[] cachableProps = SVNAdminArea14.getCachableProperties();
                    command.put(SVNProperty.CACHABLE_PROPS, asString(cachableProps, " "));
                    Map propsMap = props.loadProperties();
                    LinkedList presentProps = new LinkedList();
                    for (int i = 0; i < cachableProps.length; i++) {
                        if (propsMap.containsKey(cachableProps[i])) {
                            presentProps.addLast(cachableProps[i]);
                        }
                    }

                    if (presentProps.size() > 0) {
                        String presentPropsString = asString((String[])presentProps.toArray(new String[presentProps.size()]), " ");
                        command.put(SVNProperty.PRESENT_PROPS, presentPropsString);
                    } else {
                        command.put(SVNProperty.PRESENT_PROPS, "");
                    }
                        
                    command.put(SVNProperty.HAS_PROPS, SVNProperty.toString(!props.isEmpty()));
        
                    boolean hasPropModifications = !propsDiff.isEmpty();
                    command.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropModifications));
                    command.put(ISVNLog.NAME_ATTR, name);
                    log.addCommand(ISVNLog.MODIFY_ENTRY, command, false);
                    //don't care of the value, because we need only the presence of a mapping
                    processedEntries.put(name, "");
                    command.clear();
                        
                    String dstPath = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                    dstPath = getAdminDirectory().getName() + "/" + dstPath;
        
                    if (hasPropModifications) {
                        String tmpPath = "tmp/";
                        tmpPath += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                        File tmpFile = getAdminFile(tmpPath);
                        String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                        SVNProperties tmpProps = new SVNProperties(tmpFile, srcPath);
                        tmpProps.setProperties(props.asMap());
                        command.put(ISVNLog.NAME_ATTR, srcPath);
                        command.put(ISVNLog.DEST_ATTR, dstPath);
                        log.addCommand(SVNLog.MOVE, command, false);
                        command.clear();
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.READONLY, command, false);
                    } else {
                        command.put(ISVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.DELETE, command, false);
                    }
                    command.clear();
                    props.setModified(false);
                }
            }
        }
        
        Map basePropsCache = getBasePropertiesStorage(false);
        if (basePropsCache != null && !basePropsCache.isEmpty()) {
            for(Iterator entries = basePropsCache.keySet().iterator(); entries.hasNext();) {
                String name = (String)entries.next();
                SVNVersionedProperties baseProps = (SVNVersionedProperties)basePropsCache.get(name);
                if (baseProps.isModified()) {
                    String dstPath = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                    dstPath = getAdminDirectory().getName() + "/" + dstPath;
                    boolean isEntryProcessed = processedEntries.containsKey(name);
                    if (!isEntryProcessed) {
                        SVNVersionedProperties props = getProperties(name);
                        
                        String[] cachableProps = SVNAdminArea14.getCachableProperties();
                        command.put(SVNProperty.CACHABLE_PROPS, asString(cachableProps, " "));
                        
                        Map propsMap = props.loadProperties();
                        LinkedList presentProps = new LinkedList();
                        for (int i = 0; i < cachableProps.length; i++) {
                            if (propsMap.containsKey(cachableProps[i])) {
                                presentProps.addLast(cachableProps[i]);
                            }
                        }
                        
                        if (presentProps.size() > 0) {
                            String presentPropsString = asString((String[])presentProps.toArray(new String[presentProps.size()]), " ");
                            command.put(SVNProperty.PRESENT_PROPS, presentPropsString);
                        } else {
                            command.put(SVNProperty.PRESENT_PROPS, "");
                        }
                        
                        command.put(SVNProperty.HAS_PROPS, SVNProperty.toString(props.isEmpty()));
                        SVNVersionedProperties propsDiff = baseProps.compareTo(props);
                        boolean hasPropModifications = !propsDiff.isEmpty();
                        command.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropModifications));
                        command.put(ISVNLog.NAME_ATTR, name);
                        log.addCommand(ISVNLog.MODIFY_ENTRY, command, false);
                        command.clear();
                    }
                
                    if (baseProps.isEmpty()) {
                        command.put(ISVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.DELETE, command, false);
                    } else {
                        String tmpPath = "tmp/";
                        tmpPath += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                        File tmpFile = getAdminFile(tmpPath);
                        String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                        SVNProperties tmpProps = new SVNProperties(tmpFile, srcPath);
                        tmpProps.setProperties(baseProps.asMap());

                        command.put(ISVNLog.NAME_ATTR, srcPath);
                        command.put(ISVNLog.DEST_ATTR, dstPath);
                        log.addCommand(SVNLog.MOVE, command, false);
                        command.clear();
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.READONLY, command, false);
                    }
                    baseProps.setModified(false);
                }
            }
        }
        
        if (close) {
            closeVersionedProperties();
        }
    }

    public void saveEntries(boolean close) throws SVNException {
        if (myEntries != null) {
            if (!isLocked()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "No write-lock in ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
            
            SVNEntry2 rootEntry = (SVNEntry2) myEntries.get(getThisDirName());
            if (rootEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
            
            String reposURL = rootEntry.getRepositoryRoot();
            String url = rootEntry.getURL();
            if (reposURL != null && !SVNPathUtil.isAncestor(reposURL, url)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry ''{0}'' has inconsistent repository root and url", getThisDirName());
                SVNErrorManager.error(err);
            }
    
            File tmpFile = new File(getAdminDirectory(), "tmp/entries");
            Writer os = null;
            try {
                os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(tmpFile), "UTF-8");
                writeEntries(os);
            } catch (IOException e) {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.deleteFile(tmpFile);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot wrtie entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            
            SVNFileUtil.rename(tmpFile, myEntriesFile);
            SVNFileUtil.setReadonly(myEntriesFile, true);
            if (close) {
                closeEntries();
            }
        }
    }

    protected Map fetchEntries() throws SVNException {
        if (!myEntriesFile.exists()) {
            return null;
        }
        
        Map entries = new TreeMap();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myEntriesFile), "UTF-8"));
            //skip format line
            reader.readLine();
            int entryNumber = 1;
            while(true){
                try {
                    SVNEntry2 entry = readEntry(reader, entryNumber); 
                    if (entry == null) {
                        break;
                    } 
                    entries.put(entry.getName(), entry);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Error at entry {0,number,integer} in entries file for ''{1}'':", new Object[]{new Integer(entryNumber), getRoot()});
                    SVNErrorManager.error(err);
                }
                ++entryNumber;
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        SVNEntry2 defaultEntry = (SVNEntry2)entries.get(getThisDirName());
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err);
        }
        
        Map defaultEntryAttrs = defaultEntry.asMap();
        if (defaultEntryAttrs.get(SVNProperty.REVISION) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err);
        }

        if (defaultEntryAttrs.get(SVNProperty.URL) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing URL");
            SVNErrorManager.error(err);
        }

        for (Iterator entriesIter = entries.keySet().iterator(); entriesIter.hasNext();) {
            String name = (String)entriesIter.next();
            SVNEntry2 entry = (SVNEntry2)entries.get(name);
            if (getThisDirName().equals(name)) {
                continue;
            }
            
            Map entryAttributes = entry.asMap();
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null || Long.parseLong((String) entryAttributes.get(SVNProperty.REVISION), 10) < 0) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntryAttrs.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntryAttrs.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntryAttrs.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntryAttrs.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntryAttrs.get(SVNProperty.CACHABLE_PROPS));
                }
            }
        }
        return entries;
    }

    private SVNEntry2 readEntry(BufferedReader reader, int entryNumber) throws IOException, SVNException {
        String line = reader.readLine();
        if (line == null && entryNumber > 1) {
            return null;
        }

        String name = parseString(line);
        name = name != null ? name : getThisDirName();

        Map entryAttrs = new HashMap();
        entryAttrs.put(SVNProperty.NAME, name);
        SVNEntry2 entry = new SVNEntry2(entryAttrs, this, name);
        
        line = reader.readLine();
        String kind = parseValue(line);
        if (kind != null) {
            SVNNodeKind parsedKind = SVNNodeKind.parseKind(kind); 
            if (parsedKind != SVNNodeKind.UNKNOWN && parsedKind != SVNNodeKind.NONE) {
                entryAttrs.put(SVNProperty.KIND, kind);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Entry ''{0}'' has invalid node kind", name);
                SVNErrorManager.error(err);
            }
        } else {
            entryAttrs.put(SVNProperty.KIND, SVNNodeKind.NONE.toString());
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String revision = parseValue(line);
        if (revision != null) {
            entryAttrs.put(SVNProperty.REVISION, revision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String url = parseString(line);
        if (url != null) {
            entryAttrs.put(SVNProperty.URL, url);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String reposRoot = parseString(line);
        if (reposRoot != null && url != null && !SVNPathUtil.isAncestor(reposRoot, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", name);
            SVNErrorManager.error(err);
        } else if (reposRoot != null) {
            entryAttrs.put(SVNProperty.REPOS, reposRoot);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String schedule = parseValue(line);
        if (schedule != null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_DELETE.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                entryAttrs.put(SVNProperty.SCHEDULE, schedule);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Entry ''{0}'' has invalid ''{1}'' value", new Object[]{name, SVNProperty.SCHEDULE});
                SVNErrorManager.error(err);
            }
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String timestamp = parseValue(line);
        if (timestamp != null) {
            entryAttrs.put(SVNProperty.TEXT_TIME, timestamp);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String checksum = parseString(line);
        if (checksum != null) {
            entryAttrs.put(SVNProperty.CHECKSUM, checksum);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedDate = parseValue(line);
        if (committedDate != null) {
            entryAttrs.put(SVNProperty.COMMITTED_DATE, committedDate);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedRevision = parseValue(line);
        if (committedRevision != null) {
            entryAttrs.put(SVNProperty.COMMITTED_REVISION, committedRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedAuthor = parseString(line);
        if (committedAuthor != null) {
            entryAttrs.put(SVNProperty.LAST_AUTHOR, committedAuthor);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasProps = parseBoolean(line, SVNProperty.HAS_PROPS);
        if (hasProps) {
            entryAttrs.put(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasPropMods = parseBoolean(line, SVNProperty.HAS_PROP_MODS);
        if (hasPropMods) {
            entryAttrs.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropMods));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String cachablePropsStr = parseValue(line);
        if (cachablePropsStr != null) {
            String[] cachableProps = fromString(cachablePropsStr, " ");
            entryAttrs.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String presentPropsStr = parseValue(line);
        if (presentPropsStr != null) {
            String[] presentProps = fromString(presentPropsStr, " ");
            entryAttrs.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String prejFile = parseString(line);
        if (prejFile != null) {
            entryAttrs.put(SVNProperty.PROP_REJECT_FILE, prejFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictOldFile = parseString(line);
        if (conflictOldFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_OLD, conflictOldFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictNewFile = parseString(line);
        if (conflictNewFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_NEW, conflictNewFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictWorkFile = parseString(line);
        if (conflictWorkFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_WRK, conflictWorkFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isCopied = parseBoolean(line, ATTRIBUTE_COPIED);
        if (isCopied) {
            entryAttrs.put(SVNProperty.COPIED, SVNProperty.toString(isCopied));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromURL = parseString(line);
        if (copyfromURL != null) {
            entryAttrs.put(SVNProperty.COPYFROM_URL, copyfromURL);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromRevision = parseValue(line);
        if (copyfromRevision != null) {
            entryAttrs.put(SVNProperty.COPYFROM_REVISION, copyfromRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isDeleted = parseBoolean(line, ATTRIBUTE_DELETED);
        if (isDeleted) {
            entryAttrs.put(SVNProperty.DELETED, SVNProperty.toString(isDeleted));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isAbsent = parseBoolean(line, ATTRIBUTE_ABSENT);
        if (isAbsent) {
            entryAttrs.put(SVNProperty.ABSENT, SVNProperty.toString(isAbsent));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isIncomplete = parseBoolean(line, ATTRIBUTE_INCOMPLETE);
        if (isIncomplete) {
            entryAttrs.put(SVNProperty.INCOMPLETE, SVNProperty.toString(isIncomplete));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String uuid = parseString(line);
        if (uuid != null) {
            entryAttrs.put(SVNProperty.UUID, uuid);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockToken = parseString(line);
        if (lockToken != null) {
            entryAttrs.put(SVNProperty.LOCK_TOKEN, lockToken);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockOwner = parseString(line);
        if (lockOwner != null) {
            entryAttrs.put(SVNProperty.LOCK_OWNER, lockOwner);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockComment = parseString(line);
        if (lockComment != null) {
            entryAttrs.put(SVNProperty.LOCK_COMMENT, lockComment);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockCreationDate = parseValue(line);
        if (lockCreationDate != null) {
            entryAttrs.put(SVNProperty.LOCK_CREATION_DATE, lockCreationDate);
        }

        line = reader.readLine();
        if (line == null || line.length() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing entry terminator");
            SVNErrorManager.error(err);
        } else if (line.length() == 1 && line.charAt(0) != '\f') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid entry terminator");
            SVNErrorManager.error(err);
        }
        return entry;
    }
    
    private boolean isEntryFinished(String line) {
        return line != null && line.length() > 0 && line.charAt(0) == '\f';
    }
    
    private boolean parseBoolean(String line, String field) throws SVNException {
        line = parseValue(line);
        if (line != null) {
            if (!line.equals(field)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid value for field ''{0}''", field);
                SVNErrorManager.error(err);
            }
            return true;
        }
        return false;
    }
    
    private String parseString(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        
        int fromIndex = 0;
        int ind = -1;
        StringBuffer buffer = null;
        String escapedString = null;
        while ((ind = line.indexOf('\\', fromIndex)) != -1) {
            if (line.length() < ind + 4 || line.charAt(ind + 1) != 'x' || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 2)) || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 3))) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid escape sequence");
                SVNErrorManager.error(err);
            }
            if (buffer == null) {
                buffer = new StringBuffer();
            }

            escapedString = line.substring(ind + 2, ind + 4);  
            int escapedByte = Integer.parseInt(escapedString, 16);
            
            if (ind > fromIndex) {
                buffer.append(line.substring(fromIndex, ind));
                buffer.append((char)(escapedByte & 0xFF));
            } else {
                buffer.append((char)(escapedByte & 0xFF));
            }
            fromIndex = ind + 4;
        }
        
        if (buffer != null) {
            if (fromIndex < line.length()) {
                buffer.append(line.substring(fromIndex));
            }
            return buffer.toString();
        }   
        return line;
    }
    
    private String parseValue(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        return line;
    }
    
    public String getThisDirName() {
        return THIS_DIR;
    }
    
    protected void writeEntries(Writer writer) throws IOException {
        SVNEntry2 rootEntry = (SVNEntry2)myEntries.get(getThisDirName());
        writer.write(getFormatVersion() + "\n");
        writeEntry(writer, getThisDirName(), rootEntry.asMap(), null);

        for (Iterator entries = myEntries.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            SVNEntry2 entry = (SVNEntry2)myEntries.get(name);
            if (getThisDirName().equals(name)) {
                continue;
            }

            Map entryAttributes = entry.asMap();
            Map defaultEntryAttrs = rootEntry.asMap();
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null || Long.parseLong((String) entryAttributes.get(SVNProperty.REVISION), 10) < 0) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntryAttrs.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntryAttrs.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntryAttrs.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntryAttrs.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntryAttrs.get(SVNProperty.CACHABLE_PROPS));
                }
            }

            writeEntry(writer, name, entryAttributes, rootEntry.asMap());
        }
    }

    private void writeEntry(Writer writer, String name, Map entry, Map rootEntry) throws IOException {
        boolean isThisDir = getThisDirName().equals(name);
        boolean isSubDir = !isThisDir && SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND)); 
        int emptyFields = 0;
        
        if (!writeString(writer, name, emptyFields)) {
            ++emptyFields;
        }
        
        String kind = (String)entry.get(SVNProperty.KIND);
        if (writeValue(writer, kind, emptyFields)){
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String revision = null;
        if (isThisDir){ 
            revision = (String)entry.get(SVNProperty.REVISION);
        } else if (!isSubDir){
            revision = (String)entry.get(SVNProperty.REVISION);
            if (revision != null && revision.equals(rootEntry.get(SVNProperty.REVISION))) {
                revision = null;
            }
        }
        if (writeRevision(writer, revision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String url = null;
        if (isThisDir) {
            url = (String)entry.get(SVNProperty.URL);
        } else if (!isSubDir) {
            url = (String)entry.get(SVNProperty.URL);
            String expectedURL = SVNPathUtil.append((String)rootEntry.get(SVNProperty.URL), name);
            if (url != null && url.equals(expectedURL)) {
                url = null;
            }
        }
        if (writeString(writer, url, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String root = null;
        if (isThisDir) {
            root = (String)entry.get(SVNProperty.REPOS);
        } else if (!isSubDir) {
            String thisDirRoot = (String)rootEntry.get(SVNProperty.REPOS);
            root = (String)entry.get(SVNProperty.REPOS);
            if (root != null && root.equals(thisDirRoot)) {
                root = null;
            }
        }
        if (writeString(writer, root, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String schedule = (String)entry.get(SVNProperty.SCHEDULE);
        if (schedule != null && (!SVNProperty.SCHEDULE_ADD.equals(schedule) && !SVNProperty.SCHEDULE_DELETE.equals(schedule) && !SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
            schedule = null;
        }
        if (writeValue(writer, schedule, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String textTime = (String)entry.get(SVNProperty.TEXT_TIME);
        if (writeValue(writer, textTime, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String checksum = (String)entry.get(SVNProperty.CHECKSUM);
        if (writeValue(writer, checksum, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedDate = (String)entry.get(SVNProperty.COMMITTED_DATE);
        if (writeValue(writer, committedDate, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String committedRevision = (String)entry.get(SVNProperty.COMMITTED_REVISION);
        if (writeRevision(writer, committedRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedAuthor = (String)entry.get(SVNProperty.LAST_AUTHOR);
        if (writeString(writer, committedAuthor, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String hasProps = (String)entry.get(SVNProperty.HAS_PROPS);
        if (SVNProperty.booleanValue(hasProps)) {
            writeValue(writer, SVNProperty.HAS_PROPS, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String hasPropMods = (String)entry.get(SVNProperty.HAS_PROP_MODS);
        if (SVNProperty.booleanValue(hasPropMods)) {
            writeValue(writer, SVNProperty.HAS_PROP_MODS, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String cachableProps = asString((String[])entry.get(SVNProperty.CACHABLE_PROPS), " ");
        if (!isThisDir) {             
            String thisDirCachableProps = asString((String[])rootEntry.get(SVNProperty.CACHABLE_PROPS), " ");
            if (thisDirCachableProps != null && cachableProps != null && thisDirCachableProps.equals(cachableProps)) {
                cachableProps = null;
            }
        }
        if (writeValue(writer, cachableProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String presentProps = asString((String[])entry.get(SVNProperty.PRESENT_PROPS), " ");
        if (writeValue(writer, presentProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String propRejectFile = (String)entry.get(SVNProperty.PROP_REJECT_FILE);
        if (writeString(writer, propRejectFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String conflictOldFile = (String)entry.get(SVNProperty.CONFLICT_OLD);
        if (writeString(writer, conflictOldFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String conflictNewFile = (String)entry.get(SVNProperty.CONFLICT_NEW);
        if (writeString(writer, conflictNewFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
    
        String conflictWrkFile = (String)entry.get(SVNProperty.CONFLICT_WRK);
        if (writeString(writer, conflictWrkFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String copiedAttr = (String)entry.get(SVNProperty.COPIED);
        if (SVNProperty.booleanValue(copiedAttr)) {
            writeValue(writer, ATTRIBUTE_COPIED, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromURL = (String)entry.get(SVNProperty.COPYFROM_URL);
        if (writeString(writer, copyfromURL, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromRevision = (String)entry.get(SVNProperty.COPYFROM_REVISION);
        if (writeRevision(writer, copyfromRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String deletedAttr = (String)entry.get(SVNProperty.DELETED);
        if (SVNProperty.booleanValue(deletedAttr)) {
            writeValue(writer, ATTRIBUTE_DELETED, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String absentAttr = (String)entry.get(SVNProperty.ABSENT);
        if (SVNProperty.booleanValue(absentAttr)) {
            writeValue(writer, ATTRIBUTE_ABSENT, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String incompleteAttr = (String)entry.get(SVNProperty.INCOMPLETE);
        if (SVNProperty.booleanValue(incompleteAttr)) {
            writeValue(writer, ATTRIBUTE_INCOMPLETE, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String uuid = (String)entry.get(SVNProperty.UUID);
        if (!isThisDir) {             
            String thisDirUUID = (String)rootEntry.get(SVNProperty.UUID);
            if (thisDirUUID != null && uuid != null && thisDirUUID.equals(uuid)) {
                uuid = null;
            }
        }
        if (writeValue(writer, uuid, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockToken = (String)entry.get(SVNProperty.LOCK_TOKEN);
        if (writeString(writer, lockToken, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockOwner = (String)entry.get(SVNProperty.LOCK_OWNER);
        if (writeString(writer, lockOwner, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String lockComment = (String)entry.get(SVNProperty.LOCK_COMMENT);
        if (writeString(writer, lockComment, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockCreationDate = (String)entry.get(SVNProperty.LOCK_CREATION_DATE);
        writeValue(writer, lockCreationDate, emptyFields);
        writer.write("\f\n");
        writer.flush();
    }
    
    private boolean writeString(Writer writer, String str, int emptyFields) throws IOException {
        if (str != null && str.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            for (int i = 0; i < str.length(); i++) {
                char ch = str.charAt(i);
                if (SVNEncodingUtil.isASCIIControlChar(ch) || ch == '\\') {
                    writer.write("\\x");
                    writer.write(SVNFormatUtil.getHexNumberFromByte((byte)ch));
                } else {
                    writer.write(ch);
                }
            }
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    private boolean writeValue(Writer writer, String val, int emptyFields) throws IOException {
        if (val != null && val.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            writer.write(val);
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    private boolean writeRevision(Writer writer, String rev, int emptyFields) throws IOException {
        if (rev != null && rev.length() > 0 && Long.parseLong(rev) >= 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            writer.write(rev);
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    public boolean hasPropModifications(String name) throws SVNException {
        SVNEntry2 entry = getEntry(name, true);
        if (entry != null) {
            Map entryAttrs = entry.asMap();
            return SVNProperty.booleanValue((String)entryAttrs.get(SVNProperty.HAS_PROP_MODS));
        }
        return false;
    }
    
    public boolean hasProperties(String name) throws SVNException {
        SVNEntry2 entry = getEntry(name, true);
        if (entry != null) {
            Map entryAttrs = entry.asMap();
            return SVNProperty.booleanValue((String)entryAttrs.get(SVNProperty.HAS_PROPS));
        }
        return false;
    }

    public boolean lock() throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        return innerLock(0);
    }

    private void createFormatFile(File formatFile, boolean createMyself) throws SVNException {
        OutputStream os = null;
        try {
            formatFile = createMyself ? getAdminFile("format") : formatFile;
            os = SVNFileUtil.openFileForWriting(formatFile);
            os.write(String.valueOf(WC_FORMAT).getBytes("UTF-8"));
            os.write('\n');            
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }
    
    public SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself) throws SVNException {
        dir = createMyself ? getRoot() : dir;
        dir.mkdirs();
        File adminDir = createMyself ? getAdminDirectory() : new File(dir, SVNFileUtil.getAdminDirectoryName());
        adminDir.mkdir();
        SVNFileUtil.setHidden(adminDir, true);
        // lock dir.
        File lockFile = createMyself ? myLockFile : new File(adminDir, "lock");
        SVNFileUtil.createEmptyFile(lockFile);

        File[] tmp = {
                createMyself ? getAdminFile("tmp") : new File(adminDir, "tmp"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "props") : new File(adminDir, "tmp" + File.separatorChar + "props"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "prop-base") : new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                createMyself ? getAdminFile("tmp" + File.separatorChar + "text-base") : new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                createMyself ? getAdminFile("props") : new File(adminDir, "props"), 
                createMyself ? getAdminFile("prop-base") : new File(adminDir, "prop-base"),
                createMyself ? getAdminFile("text-base") : new File(adminDir, "text-base")
                };

        for (int i = 0; i < tmp.length; i++) {
            tmp[i].mkdir();
        }
        // for backward compatibility 
        createFormatFile(createMyself ? null : new File(adminDir, "format"), createMyself);

        SVNAdminArea adminArea = createMyself ? this : new SVNAdminArea14(dir);
        SVNEntry2 rootEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        if (rootEntry == null) {
            rootEntry = adminArea.addEntry(adminArea.getThisDirName());
        }
        if (url != null) {
            rootEntry.setURL(url);
        }
        rootEntry.setRepositoryRoot(rootURL);
        rootEntry.setRevision(revNumber);
        rootEntry.setKind(SVNNodeKind.DIR);
        if (uuid != null) {
            rootEntry.setUUID(uuid);
        }
        if (revNumber > 0) {
            rootEntry.setIncomplete(true);
        }
        rootEntry.setCachableProperties(ourCachableProperties);
        try {
            adminArea.saveEntries(true);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Error writing entries file for ''{0}''", dir);
            SVNErrorManager.error(err, svne);
        }
        
        // unlock dir.
        SVNFileUtil.deleteFile(lockFile);
        return adminArea;
    }

    public SVNAdminArea upgradeFormat(SVNAdminArea adminArea) throws SVNException {
        File logFile = adminArea.getAdminFile("log");
        SVNFileType type = SVNFileType.getType(logFile);
        if (type == SVNFileType.FILE) {
            return adminArea;
        }

        ISVNLog log = getLog();
        Map command = new HashMap();
        command.put(ISVNLog.FORMAT_ATTR, String.valueOf(getFormatVersion()));
        log.addCommand(ISVNLog.UPGRADE_FORMAT, command, false);
        command.clear();
        
        setWCAccess(adminArea.getWCAccess());
        Iterator entries = adminArea.entries(true);
        myEntries = new HashMap();
        Map basePropsCache = getBasePropertiesStorage(true);
        Map propsCache = getPropertiesStorage(true);
        
        for (; entries.hasNext();) {
            SVNEntry2 entry = (SVNEntry2) entries.next();
            SVNEntry2 newEntry = new SVNEntry2(new HashMap(entry.asMap()), this, entry.getName());
            myEntries.put(entry.getName(), newEntry);

            if (entry.getKind() != SVNNodeKind.FILE && !adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }

            SVNVersionedProperties srcBaseProps = adminArea.getBaseProperties(entry.getName());
            Map basePropsHolder = srcBaseProps.asMap();
            SVNVersionedProperties dstBaseProps = new SVNProperties13(basePropsHolder);
            basePropsCache.put(entry.getName(), dstBaseProps);
            dstBaseProps.setModified(true);
            
            SVNVersionedProperties srcProps = adminArea.getProperties(entry.getName());
            SVNVersionedProperties dstProps = new SVNProperties14(srcProps.asMap(), this, entry.getName()){

                protected Map loadProperties() throws SVNException {
                    return getPropertiesMap();
                }
            };
            propsCache.put(entry.getName(), dstProps);
            dstProps.setModified(true);
            
            command.put(ISVNLog.NAME_ATTR, entry.getName());
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNTimeUtil.formatDate(new Date(0), true));
            log.addCommand(ISVNLog.MODIFY_ENTRY, command, false);
            command.clear();
            
            SVNVersionedProperties wcProps = adminArea.getWCProperties(entry.getName());
            log.logChangedWCProperties(entry.getName(), wcProps.asMap());
        }
        saveVersionedProperties(log, true);
        log.save();

        SVNFileUtil.deleteFile(getAdminFile("README.txt"));
        SVNFileUtil.deleteFile(getAdminFile("empty-file"));
        SVNFileUtil.deleteAll(getAdminFile("wcprops"), true);
        SVNFileUtil.deleteAll(getAdminFile("tmp/wcprops"), true);
        SVNFileUtil.deleteAll(getAdminFile("dir-wcprops"), true);

        runLogs();
        return this;
    }

    public void postUpgradeFormat(int format) throws SVNException {
        if (format == WC_FORMAT) {
            createFormatFile(null, true);
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unexpected format number:\n" + 
                                                                           "   expected: {0,number,integer}\n" + 
                                                                           "     actual: {1,number,integer}", new Object[]{new Integer(WC_FORMAT), new Integer(format)});
        SVNErrorManager.error(err);
    }

    public boolean hasTextModifications(String name, boolean forceComparison) throws SVNException {
        SVNEntry2 entry = getEntry(name, false);
        if (!forceComparison) {
            if (entry == null || entry.isDirectory()) {
                return false;
            }
            
            String textTime = entry.getTextTime();
            if (textTime != null) {
                long textTimeAsLong = SVNFileUtil.roundTimeStamp(SVNTimeUtil.parseDateAsLong(textTime));
                long tstamp = SVNFileUtil.roundTimeStamp(getFile(name).lastModified());
                if (textTimeAsLong == tstamp ) {
                    return false;
                }
            }
        }
        
        SVNFileType fType = SVNFileType.getType(getFile(name));
        if (fType != SVNFileType.FILE) {
            return false;
        }
        
        File baseFile = getBaseFile(name, false);
        SVNFileType baseFileType = SVNFileType.getType(baseFile);
        if (baseFileType != SVNFileType.FILE) {
            return true;
        }
        // translate versioned file.
        File baseTmpFile = SVNFileUtil.createUniqueFile(getRoot(), 
                SVNFileUtil.getBasePath(getBaseFile(name, true)), ".tmp");
        if (!baseTmpFile.getParentFile().exists()) {
            baseTmpFile.getParentFile().mkdirs();
        }
        File versionedFile = getFile(name);
        SVNTranslator2.translate(this, name, name, SVNFileUtil.getBasePath(baseTmpFile), false, false);

        // now compare file and get base file checksum (when forced)
        MessageDigest digest;
        boolean equals = true;
        try {
            digest = forceComparison ? MessageDigest.getInstance("MD5") : null;
            equals = SVNFileUtil.compareFiles(baseFile, baseTmpFile, digest);
            if (forceComparison) {
                // if checksum differs from expected - throw exception
                String checksum = SVNFileUtil.toHexDigest(digest);
                if (!checksum.equals(entry.getChecksum())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base: ''{0}''\n" +
                            "   expected: {1}\n" +
                            "     actual: {2}\n", new Object[] {baseFile, entry.getChecksum(), checksum});
                    SVNErrorManager.error(err);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {1}", e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            baseTmpFile.delete();
        }

        if (equals && isLocked()) {
            entry.setTextTime(SVNTimeUtil.formatDate(new Date(versionedFile.lastModified())));
            saveEntries(false);
        }
        return !equals;
    }

    private boolean innerLock(int secs) throws SVNException {
        boolean created = false;
        while(true){
            try {
                created = myLockFile.createNewFile();
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}'': {1}", 
                        new Object[] {getRoot(), e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            }
            
            if (created) {
                return created;
            }
            
            if (secs-- <= 0) {
                break;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (!created) {
            if (myLockFile.isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' is locked; try performing ''cleanup''", getRoot());
                SVNErrorManager.error(err);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
        }
        return created;
    }
    
    public boolean unlock() throws SVNException {
        if (!myLockFile.exists()) {
            return true;
        }
        
        // only if there are not locks or killme files.
        boolean killMe = getAdminFile("KILLME").exists();
        if (killMe) {
            return false;
        }
        File[] logs = getAdminDirectory().listFiles();
        for (int i = 0; logs != null && i < logs.length; i++) {
            File log = logs[i];
            if ("log".equals(log.getName()) || log.getName().startsWith("log.")) {
                return false;
            }
        }
        boolean deleted = myLockFile.delete();
        if (!deleted) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Failed to unlock working copy ''{0}''", getRoot());
            SVNErrorManager.error(err);
        }
        return deleted;
    }

    public boolean isVersioned() {
        if (getAdminDirectory().isDirectory() && myEntriesFile.canRead()) {
            try {
                if (getEntry("", false) != null) {
                    return true;
                }
            } catch (SVNException e) {
                //
            }
        }
        return false;
    }
    
    public boolean isLocked() throws SVNException {
        SVNFileType type = SVNFileType.getType(myLockFile);
        if (type == SVNFileType.FILE) {
            return true;
        } else if (type == SVNFileType.NONE) {
            return false;
        } 
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Lock file ''{0}'' is not a regular file", myLockFile);
        SVNErrorManager.error(err);
        return false;
    }

    protected int getFormatVersion() {
        return WC_FORMAT;
    }
    
}
