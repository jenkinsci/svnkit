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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCManager {
    
    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, SVNRevision copyFromRev) throws SVNException {
        add(path, parentDir, copyFromURL, copyFromRev.getNumber());
    }

    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, long copyFromRev) throws SVNException {
        SVNWCAccess wcAccess = parentDir.getWCAccess();
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' not found", path);
            SVNErrorManager.error(err);
        } else if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Unsupported node kind for path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea dir = wcAccess.probeTry(path, true, copyFromURL != null ? SVNWCAccess.INFINITE_DEPTH : 0);
        SVNEntry entry = null;
        if (dir != null) {
            entry = wcAccess.getEntry(path, true);
        }
        boolean replace = false;
        SVNNodeKind kind = SVNFileType.getNodeKind(fileType);
        if (entry != null) {
            if (copyFromURL == null && !entry.isScheduledForDeletion() && !entry.isDeleted()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control", path);
                SVNErrorManager.error(err);
            } else if (entry.getKind() != kind) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, 
                        "Can''t replace ''{0}'' with a node of a different type; the deletion must be committed and the parent updated before adding ''{0}''", path);
                SVNErrorManager.error(err);
            }
            replace = entry.isScheduledForDeletion();
        }
        SVNEntry parentEntry = wcAccess.getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "Can''t find parent directory''s entry while trying to add ''{0}''", path);
            SVNErrorManager.error(err);
        }
        if (parentEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, 
                    "Can''t add ''{0}'' to a parent directory scheduled for deletion", path);
            SVNErrorManager.error(err);
        }
        Map command = new HashMap();
        String name = path.getName();
        if (copyFromURL != null) {
            if (parentEntry.getRepositoryRoot() != null && !SVNPathUtil.isAncestor(parentEntry.getRepositoryRoot(), copyFromURL.toString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "The URL ''{0}'' has a different repository root than its parent", copyFromURL);
                SVNErrorManager.error(err);
            }
            command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL.toString());
            command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), SVNProperty.toString(copyFromRev));
            command.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), Boolean.TRUE.toString());
        }
        if (replace) {
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), null);
        }
        command.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        command.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNFileType.getNodeKind(fileType).toString());
        if (!(replace || copyFromURL != null)) {
            command.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), "0");
        }
        parentDir.modifyEntry(name, command, true, false);
        
        if (entry != null && copyFromURL == null) {
            String propPath = SVNAdminUtil.getPropPath(name, entry.getKind(), false);
            File propFile = dir.getFile(propPath);
            SVNFileUtil.deleteFile(propFile);
        }
        if (kind == SVNNodeKind.DIR) {
            if (copyFromURL == null) {
                SVNEntry pEntry = wcAccess.getEntry(path.getParentFile(), false);
                SVNURL newURL = pEntry.getSVNURL().appendPath(name, false);
                SVNURL rootURL = pEntry.getRepositoryRootURL();
                ensureAdmiAreaExists(path, newURL.toString(), rootURL != null ? rootURL.toString() : null, pEntry.getUUID(), 0);
            } else {
                SVNURL rootURL = parentEntry.getRepositoryRootURL();
                ensureAdmiAreaExists(path, copyFromURL.toString(), rootURL != null ? rootURL.toString() : null, parentEntry.getUUID(), copyFromRev);
            }
            if (entry == null || entry.isDeleted()) {
                dir = wcAccess.open(path, true, copyFromURL != null ? SVNWCAccess.INFINITE_DEPTH : 0);
            }
            command.put(SVNProperty.shortPropertyName(SVNProperty.INCOMPLETE), null);
            command.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), replace ? SVNProperty.SCHEDULE_REPLACE : SVNProperty.SCHEDULE_ADD);
            dir.modifyEntry(dir.getThisDirName(), command, true, true);
            if (copyFromURL != null) {
                SVNURL newURL = parentEntry.getSVNURL().appendPath(name, false);
                updateCleanup(path, wcAccess, true, newURL.toString(), parentEntry.getRepositoryRoot(), -1, false);
                markTree(dir, null, true, COPIED);
                SVNPropertiesManager.deleteWCProperties(dir, null, true);
            }
        }
        SVNEvent event = SVNEventFactory.createAddedEvent(parentDir, name, kind, null);
        parentDir.getWCAccess().handleEvent(event);
    }
    
    public static final int SCHEDULE = 1; 
    public static final int COPIED = 2; 
    
    public static void markTree(SVNAdminArea dir, String schedule, boolean copied, int flags) throws SVNException {
        Map attributes = new HashMap();
        for(Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            File path = dir.getFile(entry.getName());
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
                markTree(childDir, schedule, copied, flags);
            }
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), copied ? Boolean.TRUE.toString() : null);
            }
            dir.modifyEntry(entry.getName(), attributes, true, false);
            attributes.clear();
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                SVNEvent event = SVNEventFactory.createDeletedEvent(dir, entry.getName());
                dir.getWCAccess().handleEvent(event);
            }
        }
        SVNEntry dirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (!(dirEntry.isScheduledForAddition() && SVNProperty.SCHEDULE_DELETE.equals(schedule))) {
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), copied ? Boolean.TRUE.toString() : null);
            }
            dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            attributes.clear();
        }
        dir.saveEntries(false);
    }
    
    public static void updateCleanup(File path, SVNWCAccess wcAccess, boolean recursive, String baseURL, String rootURL,
            long newRevision, boolean removeMissingDirs) throws SVNException {
        SVNEntry entry = wcAccess.getEntry(path, true);
        if (entry == null) {
            return;
        }
        if (entry.isFile() || (entry.isDirectory() && (entry.isAbsent() || entry.isDeleted()))) {
            SVNAdminArea dir = wcAccess.retrieve(path.getParentFile());
            if (dir.tweakEntry(path.getName(), baseURL, rootURL, newRevision, false)) {
                dir.saveEntries(false);
            }
        } else if (entry.isDirectory()) {
            SVNAdminArea dir = wcAccess.retrieve(path);
            tweakEntries(dir, baseURL, rootURL, newRevision, removeMissingDirs, recursive);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unrecognized node kind: ''{0}''", path);
            SVNErrorManager.error(err);
            
        }
    }
    
    private static void tweakEntries(SVNAdminArea dir, String baseURL, String rootURL, long newRevision, boolean removeMissingDirs, boolean recursive) throws SVNException {
        boolean write = dir.tweakEntry(dir.getThisDirName(), baseURL, rootURL, newRevision, false);
        for(Iterator entries = dir.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            String childURL = null;
            if (baseURL != null) {
                childURL = SVNPathUtil.append(baseURL, SVNEncodingUtil.uriEncode(entry.getName()));
            }
            if (entry.isFile() || (entry.isDirectory() && (entry.isAbsent() || entry.isDeleted()))) {
                write |= dir.tweakEntry(entry.getName(), childURL, rootURL, newRevision, true);
            } else if (entry.isDirectory() && recursive) {
                File path = dir.getFile(entry.getName());
                if (removeMissingDirs && dir.getWCAccess().isMissing(path)) {
                    if (!entry.isScheduledForAddition()) {
                        dir.deleteEntry(entry.getName());
                        dir.getWCAccess().handleEvent(SVNEventFactory.createUpdateDeleteEvent(null, dir, entry));
                    }
                } else {
                    SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
                    tweakEntries(childDir, childURL, rootURL, newRevision, removeMissingDirs, recursive);
                }
            } 
        }
        if (write) {
            dir.saveEntries(false);
        }
    }
    
    public static boolean ensureAdmiAreaExists(File path, String url, String rootURL, String uuid, long revision) throws SVNException{
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' is not a directory", path);
            SVNErrorManager.error(err);            
        }
        if (fileType == SVNFileType.NONE) {
            SVNAdminAreaFactory.createVersionedDirectory(path, url, rootURL, uuid, revision);
            return true;
        }
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            wcAccess.open(path, false, 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "No entry for ''{0}''", path);
                SVNErrorManager.error(err);            
            }
            if (!entry.isScheduledForDeletion()) {
                if (entry.getRevision() != revision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Revision {0} doesn''t match existing revision {1} in ''{2}''", 
                            new Object[] {new Long(revision), new Long(entry.getRevision()), path});
                    SVNErrorManager.error(err);            
                }
                if (!entry.getURL().equals(url)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "URL {0} doesn''t match existing URL {1} in ''{2}''", 
                            new Object[] {url, entry.getURL(), path});
                    SVNErrorManager.error(err);            
                }
            }
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNAdminAreaFactory.createVersionedDirectory(path, url, rootURL, uuid, revision);
                return true;
            }
            throw e;
        } finally {
            wcAccess.close();
        }
        return false;
    }

    public static void canDelete(File path, boolean skipIgnored, ISVNOptions options) throws SVNException {
        SVNStatusClient statusClient = new SVNStatusClient((ISVNAuthenticationManager) null, options);
        statusClient.doStatus(path, SVNRevision.UNDEFINED, true, false, false, !skipIgnored, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) throws SVNException {
                if (status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is in the way of the resource actually under version control", status.getFile());
                    SVNErrorManager.error(err);
                } else if (status.getEntry() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", status.getFile());
                    SVNErrorManager.error(err);
                } else if ((status.getContentsStatus() != SVNStatusType.STATUS_NORMAL &&
                        status.getContentsStatus() != SVNStatusType.STATUS_DELETED &&
                        status.getContentsStatus() != SVNStatusType.STATUS_MISSING) ||
                        (status.getPropertiesStatus() != SVNStatusType.STATUS_NONE &&
                         status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MODIFIED, "''{0}'' has local modifications", status.getFile());
                    SVNErrorManager.error(err);
                }
            }
        });
    }

    public static void delete(SVNWCAccess wcAccess, SVNAdminArea root, File path, boolean deleteFiles) throws SVNException {
        SVNAdminArea dir = wcAccess.probeTry(path, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = null;
        if (dir != null) {
            entry = wcAccess.getEntry(path, false);
        } else {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
            return;
        }
        if (entry == null) {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
            return;
        }
        String schedule = entry.getSchedule();
        SVNNodeKind kind = entry.getKind();
        boolean copied = entry.isCopied();
        boolean deleted = false;
        String name = path.getName();
        
        if (kind == SVNNodeKind.DIR) {
            SVNAdminArea parent = wcAccess.retrieve(path.getParentFile());
            SVNEntry entryInParent = parent.getEntry(name, true);
            deleted = entryInParent != null ? entryInParent.isDeleted() : false;
            if (!deleted && SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                if (dir != root) {
                    dir.removeFromRevisionControl("", false, false);
                } else {
                    parent.deleteEntry(name);
                    parent.saveEntries(false);
                }
            } else {
                if (dir != root) {
                    markTree(dir, SVNProperty.SCHEDULE_DELETE, false, SCHEDULE);
                }
            }
        }
        if (!(kind == SVNNodeKind.DIR && SVNProperty.SCHEDULE_ADD.equals(schedule) && !deleted)) {
            SVNLog log = root.getLog();
            
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_DELETE);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
            if (SVNProperty.SCHEDULE_REPLACE.equals(schedule) && copied) {
                if (kind != SVNNodeKind.DIR) {
                    command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextRevertPath(name, false));
                    command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                    log.addCommand(SVNLog.MOVE, command, false);
                    command.clear();
                }
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropRevertPath(name, kind, false));
                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getPropBasePath(name, kind, false));
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropPath(name, kind, false));
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            }
            log.save();
            root.runLogs();
        }
        SVNEvent event = SVNEventFactory.createDeletedEvent(root, name);
        wcAccess.handleEvent(event);
        if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
        } else {
            SVNWCManager.doEraseFromWC(path, root, kind, deleteFiles);
        }
    }

    public static void doDeleteUnversionedFiles(SVNWCAccess wcAccess, File path, boolean deleteFiles) throws SVNException {
        wcAccess.checkCancelled();
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "''{0}'' does not exist", path);
            SVNErrorManager.error(err);
        }
        if (deleteFiles) {
            SVNFileUtil.deleteAll(path, true, wcAccess.getEventHandler());
        }
    }

    public static void doEraseFromWC(File path, SVNAdminArea dir, SVNNodeKind kind, boolean deleteFiles) throws SVNException {
        SVNFileType type = SVNFileType.getType(path);
        if (type == SVNFileType.NONE) {
            return;
        }
        dir.getWCAccess().checkCancelled();
        if (kind == SVNNodeKind.FILE) {
            if (deleteFiles) {
                SVNFileUtil.deleteFile(path);
            }
        } else if (kind == SVNNodeKind.DIR) {
            SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
            Collection versioned = new HashSet();
            for(Iterator entries = childDir.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                versioned.add(entry.getName());
                if (childDir.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                File childPath = childDir.getFile(entry.getName());
                doEraseFromWC(childPath, childDir, entry.getKind(), deleteFiles);
                
            }
            File[] children = path.listFiles();
            for(int i = 0; children != null && i < children.length; i++) {
                if (SVNFileUtil.getAdminDirectoryName().equals(children[i].getName())) {
                    continue;
                }
                if (versioned.contains(children[i].getName())) {
                    continue;
                }
                doDeleteUnversionedFiles(dir.getWCAccess(), children[i], deleteFiles);
            }
        }
    }

    public static void addRepositoryFile(SVNAdminArea dir, String fileName, File text, File textBase, Map baseProperties, Map properties, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntry parentEntry = dir.getEntry(dir.getThisDirName(), false);
        if (parentEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", dir.getRoot());
            SVNErrorManager.error(err);
        }
        String newURL = SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(fileName));
        if (copyFromURL != null && parentEntry.getRepositoryRoot() != null && !SVNPathUtil.isAncestor(parentEntry.getRepositoryRoot(), copyFromURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Copyfrom-url ''{0}'' has different repository root than ''{1}''", new Object[]{copyFromURL, parentEntry.getRepositoryRoot()});
            SVNErrorManager.error(err);
        }
    
        SVNEntry dstEntry = dir.getEntry(fileName, false);
        SVNLog log = dir.getLog();
        Map command = new HashMap();
        if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
            String revertTextPath = SVNAdminUtil.getTextRevertPath(fileName, false);
            String baseTextPath = SVNAdminUtil.getTextBasePath(fileName, false);
            String revertPropsPath = SVNAdminUtil.getPropRevertPath(fileName, SVNNodeKind.FILE, false);
            String basePropsPath = SVNAdminUtil.getPropBasePath(fileName, SVNNodeKind.FILE, false);
    
            command.put(SVNLog.NAME_ATTR, baseTextPath);
            command.put(SVNLog.DEST_ATTR, revertTextPath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            
            if (dir.getFile(basePropsPath).isFile()) {
                command.put(SVNLog.NAME_ATTR, basePropsPath);
                command.put(SVNLog.DEST_ATTR, revertPropsPath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
        }
        
        Map entryAttrs = new HashMap();
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), SVNProperty.toString(true));
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), SVNProperty.toString(copyFromRev));
        }
        log.logChangedEntryProperties(fileName, entryAttrs);
        entryAttrs.clear();
        
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), SVNProperty.toString(dstEntry != null ? dstEntry.getRevision() : parentEntry.getRevision()));
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.URL), newURL);
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.ABSENT), null);
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), null);
        log.logChangedEntryProperties(fileName, entryAttrs);
        entryAttrs.clear();
    
        SVNWCManager.addProperties(dir, fileName, baseProperties, true, log);
        SVNWCManager.addProperties(dir, fileName, properties, false, log);
        
        File tmpTextBase = dir.getBaseFile(fileName, true);
        if (!tmpTextBase.equals(textBase) && textBase != null) {
            SVNFileUtil.rename(textBase, tmpTextBase);
        }
        if (text != null) {
            File tmpFile = SVNFileUtil.createUniqueFile(dir.getRoot(), fileName, ".tmp");
            SVNFileUtil.rename(text, tmpFile);
            if (baseProperties != null && baseProperties.containsKey(SVNProperty.SPECIAL)) {
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                command.put(SVNLog.DEST_ATTR, fileName);
                command.put(SVNLog.ATTR1, "true");
                log.addCommand(SVNLog.COPY, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            } else {
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                command.put(SVNLog.DEST_ATTR, fileName);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
        } else {
            command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, true));
            command.put(SVNLog.DEST_ATTR, fileName);
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
            command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
            log.logChangedEntryProperties(fileName, command);
            command.clear();
        }
        
    
    
        command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, true));
        command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextBasePath(fileName, false));
        log.addCommand(SVNLog.MOVE, command, false);
        command.clear();
        
        command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, false));
        log.addCommand(SVNLog.READONLY, command, false);
        command.clear();
    
        String checksum = SVNFileUtil.computeChecksum(dir.getBaseFile(fileName, true));
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
        log.logChangedEntryProperties(fileName, entryAttrs);
        entryAttrs.clear();
        
        log.save();
        dir.runLogs();
    }

    public static void addProperties(SVNAdminArea dir, String fileName, Map properties, boolean base, SVNLog log) throws SVNException {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        Map regularProps = new HashMap();
        Map entryProps = new HashMap();
        Map wcProps = new HashMap();
    
        for (Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) properties.get(propName);
            if (SVNProperty.isEntryProperty(propName)) {
                entryProps.put(SVNProperty.shortPropertyName(propName), propValue);
            } else if (SVNProperty.isWorkingCopyProperty(propName)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        SVNVersionedProperties props = base ? dir.getBaseProperties(fileName) : dir.getProperties(fileName);
        props.removeAll();
        for (Iterator propNames = regularProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propValue = (String) regularProps.get(propName);
            props.setPropertyValue(propName, propValue);
        }
        dir.saveVersionedProperties(log, false);
        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
    }

}
