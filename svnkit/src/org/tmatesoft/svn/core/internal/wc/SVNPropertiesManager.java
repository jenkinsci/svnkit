/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNPropertiesManager {
    
    private static final Collection NOT_ALLOWED_FOR_FILE = new HashSet();
    private static final Collection NOT_ALLOWED_FOR_DIR = new HashSet();
    
    static {
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.IGNORE);
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.EXTERNALS);
        
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EXECUTABLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.KEYWORDS);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EOL_STYLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.NEEDS_LOCK);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.MIME_TYPE);
    }

    public static void setWCProperty(SVNWCAccess access, File path, String propName, String propValue, boolean write) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        dir.getWCProperties(entry.getName()).setPropertyValue(propName, propValue);
        if (write) {
            dir.saveWCProperties(false);
        }
    }

    public static String getWCProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getWCProperties(entry.getName()).getPropertyValue(propName);
    }
    
    public static void deleteWCProperties(SVNAdminArea dir, String name, boolean recursive) throws SVNException {
        if (name != null) {
            SVNVersionedProperties props = dir.getWCProperties(name);
            if (props != null) {
                props.removeAll();
            }
        } 
        if (recursive || name == null) {
            for(Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                if (name != null) {
                    SVNVersionedProperties props = dir.getWCProperties(entry.getName());
                    if (props != null) {
                        props.removeAll();
                    }
                }
                if (dir.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                if (entry.isFile()) {
                    continue;                    
                }
                if (recursive) {
                    SVNAdminArea childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
                    deleteWCProperties(childDir, null, true);
                }
            }
        }
        dir.saveWCProperties(false);
    }

    public static String getProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        String[] cachableProperties = entry.getCachableProperties();
        if (cachableProperties != null && contains(cachableProperties, propName)) {
            String[] presentProperties = entry.getPresentProperties();
            if (presentProperties == null || !contains(presentProperties, propName)) {
                return null;
            }
            if (SVNProperty.isBooleanProperty(propName)) {
                return SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            return getWCProperty(access, path, propName);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err);
        } 
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getProperties(entry.getName()).getPropertyValue(propName);
    }

    public static void setProperty(SVNWCAccess access, File path, String propName, String propValue, boolean skipChecks) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            setWCProperty(access, path, propName, propValue, true);
            return;
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err);
        }
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        boolean updateTimeStamp = SVNProperty.EOL_STYLE.equals(propName);
        if (propValue != null) {
            validatePropertyName(path, propName, entry.getKind());
            if (!skipChecks && SVNProperty.EOL_STYLE.equals(propName)) {
                propValue = propValue.trim();
                validateEOLProperty(path, access);
            } else if (!skipChecks && SVNProperty.MIME_TYPE.equals(propName)) {
                propValue = propValue.trim();
                validateMimeType(propValue);
            } else if (SVNProperty.EXTERNALS.equals(propName) || SVNProperty.IGNORE.equals(propName)) {
                if (!propValue.endsWith("\n")) {
                    propValue += "\n";
                }
                if (SVNProperty.EXTERNALS.equals(propName)) {
                    // TODO validate
                }
            } else if (SVNProperty.KEYWORDS.equals(propName)) {
                propValue = propValue.trim();
            }
        }
        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.EXECUTABLE.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setExecutable(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
                SVNFileUtil.setExecutable(path, true);
            }
        }
        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.NEEDS_LOCK.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setReadonly(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        SVNVersionedProperties properties = dir.getProperties(entry.getName());
        if (!updateTimeStamp && (entry.getKind() == SVNNodeKind.FILE && SVNProperty.KEYWORDS.equals(propName))) {
            String oldValue = properties.getPropertyValue(SVNProperty.KEYWORDS);
            Collection oldKeywords = getKeywords(oldValue);
            Collection newKeywords = getKeywords(propValue);
            updateTimeStamp = !oldKeywords.equals(newKeywords); 
        }
        SVNLog log = dir.getLog();
        if (updateTimeStamp) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, entry.getName());
            command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), null);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        }
        properties.setPropertyValue(propName, propValue);
        dir.saveVersionedProperties(log, false);
        log.save();
        dir.runLogs();
    }
    
    public static SVNStatusType mergeProperties(SVNWCAccess wcAccess, File path, Map baseProperties, Map diff, boolean baseMerge, boolean dryRun) throws SVNException {
        SVNEntry entry = wcAccess.getEntry(path, false);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
            SVNErrorManager.error(err);
        }
        File parent = null;
        String name = null;
        if (entry.isDirectory()) {
            parent = path;
            name = "";
        } else if (entry.isFile()) {
            parent = path.getParentFile();
            name = entry.getName();
        } 
        
        SVNLog log = null;
        SVNAdminArea dir = wcAccess.retrieve(parent);
        if (!dryRun) {
            log = dir.getLog();            
        }
        SVNStatusType result = dir.mergeProperties(name, baseProperties, diff, baseMerge, dryRun, log);
        if (!dryRun) {
            log.save();
            dir.runLogs();
        }
        return result; 
    }
    
    public static Map computeAutoProperties(ISVNOptions options, File file) {
        Map properties = options.applyAutoProperties(file, null);
        if (!properties.containsKey(SVNProperty.MIME_TYPE)) {
            String mimeType = SVNFileUtil.detectMimeType(file);
            if (mimeType != null) {
                properties.put(SVNProperty.MIME_TYPE, mimeType);
            }
        }
        if (SVNProperty.isBinaryMimeType((String) properties.get(SVNProperty.MIME_TYPE))) {
            properties.remove(SVNProperty.EOL_STYLE);
        }
        if (!properties.containsKey(SVNProperty.EXECUTABLE)) { 
            if (SVNFileUtil.isExecutable(file)) {
                properties.put(SVNProperty.EXECUTABLE, SVNProperty.getValueOfBooleanProperty(SVNProperty.EXECUTABLE));
            }
        }
        return properties;
    }
    
    private static void validatePropertyName(File path, String name, SVNNodeKind kind) throws SVNException {
        SVNErrorMessage err = null;
        if (kind == SVNNodeKind.DIR) {
            if (NOT_ALLOWED_FOR_DIR.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a directory (''{1}'')", new Object[] {name, path});
            }
        } else if (kind == SVNNodeKind.FILE) {
            if (NOT_ALLOWED_FOR_FILE.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a file (''{1}'')", new Object[] {name, path});
            }
        } else {
            err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is not a file or directory", path);
        }
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }

    private static void validateEOLProperty(File path, SVNWCAccess access) throws SVNException {
        String mimeType = getProperty(access, path, SVNProperty.MIME_TYPE);
        if (mimeType != null && SVNProperty.isBinaryMimeType(mimeType)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", path);
            SVNErrorManager.error(err);
        }
        boolean consistent = SVNTranslator.checkNewLines(path);
        if (!consistent) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has inconsistent newlines", path);
            SVNErrorManager.error(err);
        }
    }

    private static void validateMimeType(String value) throws SVNException {
        String type = value.indexOf(';') >= 0 ? value.substring(0, value.indexOf(';')) : value;
        SVNErrorMessage err = null;
        if (type.length() == 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' has empty media type", value);
        } else if (type.indexOf('/') < 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' does not contain ''/''", value);
        } else if (!Character.isLetterOrDigit(type.charAt(type.length() - 1))) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' ends with non-alphanumeric character", value);
        }
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }
    
    private static Collection getKeywords(String value) {
        Collection keywords = new HashSet();
        if (value == null || "".equals(value.trim())) {
            return keywords;
        }
        for(StringTokenizer tokens = new StringTokenizer(value, " \t\n\r"); tokens.hasMoreTokens();) {
            keywords.add(tokens.nextToken().toLowerCase());
        }
        return keywords;
    }
    
    private static boolean contains(String[] values, String value) {
        for (int i = 0; value != null && i < values.length; i++) {
            if (values[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

}
