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
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry2;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess2;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCManager {
    
    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, SVNRevision copyFromRev) throws SVNException {

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
                        "Can't replace ''{0}'' with a node of a different type; the deletion must be committed and the parent updated before adding ''{0}''", path);
                SVNErrorManager.error(err);
            }
            replace = entry.isScheduledForDeletion();
        }
        SVNEntry2 parentEntry = wcAccess.getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "Can't find parent directory's entry while trying to add ''{0}''", path);
            SVNErrorManager.error(err);
        }
        if (parentEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, 
                    "Can't add ''{0}'' to a parent directory scheduled for deletion", path);
            SVNErrorManager.error(err);
        }
        Map command = new HashMap();
        String name = path.getName();
        command.put(ISVNLog.NAME_ATTR, name);
        if (copyFromURL != null) {
            if (parentEntry.getRepositoryRoot() != null && !SVNPathUtil.isAncestor(parentEntry.getRepositoryRoot(), copyFromURL.toString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "The URL ''{0}'' has a different repository root than its parent", copyFromURL);
                SVNErrorManager.error(err);
            }
            command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL.toString());
            command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), copyFromRev.toString());
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
        ISVNLog log = parentDir.getLog();
        log.addCommand(ISVNLog.MODIFY_ENTRY, command, false);
        log.save();
        parentDir.runLogs();
        
        if (entry != null && copyFromURL == null) {
            String propPath = SVNAdminUtil.getPropPath(name, entry.getKind(), false);
            File propFile = dir.getFile(propPath);
            SVNFileUtil.deleteFile(propFile);
        }
        if (kind == SVNNodeKind.DIR) {
            if (copyFromURL == null) {
                SVNEntry2 pEntry = wcAccess.getEntry(path.getParentFile(), false);
                SVNURL newURL = pEntry.getSVNURL().appendPath(name, false);
                // TODO create admin area in dir for newURL
            } else {
                // TODO create admin area in dir for copyFromURL
            }
            if (entry == null || entry.isDeleted()) {
                // reopen created child dir.
            }
            // modify entry in created child dir.
        }
        
    }
    
    public static final int SCHEDULE = 1; 
    public static final int COPIED = 2; 
    
    public static void markTree(SVNAdminArea dir, String schedule, boolean copied, int flags) throws SVNException {
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
                entry.setSchedule(schedule);
            }
            if ((flags & COPIED) != 0) {
                entry.setCopied(copied);
            }
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                SVNEvent event = SVNEventFactory.createDeletedEvent(dir, entry.getName());
                dir.getWCAccess().handleEvent(event);
            }
        }
        SVNEntry2 dirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (!(dirEntry.isScheduledForAddition() && SVNProperty.SCHEDULE_DELETE.equals(schedule))) {
            if ((flags & SCHEDULE) != 0) {
                dirEntry.setSchedule(schedule);
            }
            if ((flags & COPIED) != 0) {
                dirEntry.setCopied(copied);
            }
        }
        dir.saveEntries(false);
    }

}
