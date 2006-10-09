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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.HashMap;
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
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry2;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess2;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCManager {
    
    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, SVNRevision copyFromRev) throws SVNException {
        add(path, parentDir, copyFromURL, copyFromRev.getNumber());
    }

    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, long copyFromRev) throws SVNException {
        SVNWCAccess2 wcAccess = parentDir.getWCAccess();
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' not found", path);
            SVNErrorManager.error(err);
        } else if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Unsupported node kind for path ''{0}''", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea dir = wcAccess.probeTry(path, true, copyFromURL != null ? SVNWCAccess2.INFINITE_DEPTH : 0);
        SVNEntry2 entry = null;
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
        SVNEntry2 parentEntry = wcAccess.getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "Can''t find parent directory's entry while trying to add ''{0}''", path);
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
                SVNEntry2 pEntry = wcAccess.getEntry(path.getParentFile(), false);
                SVNURL newURL = pEntry.getSVNURL().appendPath(name, false);
                ensureAdmiAreaExists(path, newURL.toString(), pEntry.getRepositoryRootURL().toString(), pEntry.getUUID(), 0);
            } else {
                ensureAdmiAreaExists(path, copyFromURL.toString(), parentEntry.getRepositoryRootURL().toString(), parentEntry.getUUID(), copyFromRev);
            }
            if (entry == null || entry.isDeleted()) {
                dir = wcAccess.open(path, true, copyFromURL != null ? SVNWCAccess2.INFINITE_DEPTH : 0);
            }
            dir.getEntry(dir.getThisDirName(), false).setSchedule(replace ? SVNProperty.SCHEDULE_REPLACE : SVNProperty.SCHEDULE_ADD);
            dir.getEntry(dir.getThisDirName(), false).setIncomplete(false);
            dir.saveEntries(false);
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
            SVNEntry2 entry = (SVNEntry2) entries.next();
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
        SVNEntry2 dirEntry = dir.getEntry(dir.getThisDirName(), false);
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
    
    public static void updateCleanup(File path, SVNWCAccess2 wcAccess, boolean recursive, String baseURL, String rootURL,
            long newRevision, boolean removeMissingDirs) throws SVNException {
        SVNEntry2 entry = wcAccess.getEntry(path, true);
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
            SVNEntry2 entry = (SVNEntry2) entries.next();
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
    
    private static void ensureAdmiAreaExists(File path, String url, String rootURL, String uuid, long revision) throws SVNException{
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' is not a directory", path);
            SVNErrorManager.error(err);            
        }
        if (fileType == SVNFileType.NONE) {
            SVNAdminAreaFactory.createVersionedDirectory(path, url, rootURL, uuid, revision);
            return;
        }
        SVNWCAccess2 wcAccess = SVNWCAccess2.newInstance(null);
        try {
            wcAccess.open(path, false, 0);
            SVNEntry2 entry = wcAccess.getEntry(path, false);
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
                return;
            }
            throw e;
        } finally {
            wcAccess.close();
        }
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

}
