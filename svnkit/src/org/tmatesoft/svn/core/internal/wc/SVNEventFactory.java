/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNEventFactory {

    public static SVNEvent createMergeEvent(SVNAdminArea adminArea, String path,
            SVNEventAction action, SVNEventAction expectedAction, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(adminArea.getRoot(), adminArea.getFile(path),
                                      action, expectedAction, kind, -1, null, cType, 
                                      pType, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createMergeEvent(SVNAdminArea adminArea, File file, 
                                            SVNEventAction action, SVNEventAction expectedAction, 
                                            SVNStatusType cType, SVNStatusType pType, 
                                            SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(adminArea.getRoot(), file, action, expectedAction, kind, -1, 
                                      null, cType, pType, null, null, null);
        return event;
    }

    public static SVNEvent createCommitEvent(File rootFile, File file,
            SVNEventAction action, SVNNodeKind kind, String mimeType) {
        return new SVNEvent(rootFile, file, action, kind, -1, mimeType,
                SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE,
                SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createCommitEvent(File rootFile, File file,
                                             SVNEventAction action, SVNNodeKind kind) {
        return new SVNEvent(rootFile, file, action, kind, -1, null,
                SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE,
                SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createSkipEvent(File rootFile, File file,
            SVNEventAction action, SVNEventAction expectedAction, SVNNodeKind kind) {
        return new SVNEvent(rootFile, file, action, expectedAction, kind, -1, null,
                SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE,
                SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createSkipEvent(SVNAdminArea dir, String name, SVNEventAction action, 
                                           SVNEventAction expectedAction, SVNNodeKind kind, 
                                           long revision, SVNStatusType cstatus, 
                                           SVNStatusType pstatus) {
        return new SVNEvent(dir, name, action, expectedAction, kind, revision, null,
                            cstatus, pstatus, SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createLockEvent(SVNAdminArea dir, String path, SVNEventAction action, 
                                           SVNLock lock, SVNErrorMessage message) {
        SVNEvent event = new SVNEvent(dir, SVNPathUtil.tail(path), action, SVNNodeKind.FILE, -1, 
                                      null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }
    
    public static SVNEvent createAnnotateEvent(String path, long revision) {
        SVNEvent event = new SVNEvent(null, SVNPathUtil.tail(path), SVNEventAction.ANNOTATE, 
                                      SVNNodeKind.NONE, revision, null, null, null, null, null, 
                                      null);
        event.setPath(path);
        return event;
    }
    
    public static SVNEvent createAddedEvent(SVNAdminArea dir, SVNEntry entry) {
        String mimeType = null;
        try {
            mimeType = dir.getProperties(entry.getName()).getPropertyValue(SVNProperty.MIME_TYPE);
        } catch (SVNException e) {
            //
        }
        return new SVNEvent(dir, entry.getName(), SVNEventAction.ADD,
                entry.getKind(), 0, mimeType, null, null, null, null, null);

    }

    public static SVNEvent createAddedEvent(SVNAdminArea dir, String name, SVNNodeKind kind, 
                                            String mimeType) {
        return new SVNEvent(dir, name, SVNEventAction.ADD, kind, 0, mimeType, null, null, null, 
                            null, null);
    }

    public static SVNEvent createDeletedEvent(SVNAdminArea dir, String name) {
        return new SVNEvent(dir, name, SVNEventAction.DELETE, null, 0,
                            null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateExternalEvent(SVNAdminArea adminArea, String path) {
        SVNEvent event = new SVNEvent(adminArea, null, SVNEventAction.UPDATE_EXTERNAL, 
                                      SVNNodeKind.DIR, -1, null,
                                      null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createStatusExternalEvent(SVNAdminArea adminArea, String path) {
        SVNEvent event = new SVNEvent(adminArea, null, SVNEventAction.STATUS_EXTERNAL, 
                                      SVNNodeKind.DIR, -1, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createUpdateCompletedEvent(SVNAdminArea adminArea, long revision) {
        return new SVNEvent(adminArea, "", SVNEventAction.UPDATE_COMPLETED, SVNNodeKind.NONE,
                            revision, null, null, null, null, null, null);
    }

    public static SVNEvent createCommitCompletedEvent(SVNAdminArea adminArea, long revision) {
        return new SVNEvent(adminArea, "", SVNEventAction.COMMIT_COMPLETED, SVNNodeKind.NONE,
                            revision, null, null, null, null, null, null);
    }

    public static SVNEvent createStatusCompletedEvent(SVNAdminArea adminArea, String name, 
                                                      long revision) {
        return new SVNEvent(adminArea, name, SVNEventAction.STATUS_COMPLETED, SVNNodeKind.NONE, 
                            revision, null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateModifiedEvent(SVNAdminArea adminArea, String name, 
                                                     SVNNodeKind kind, SVNEventAction action, 
                                                     String mimeType, SVNStatusType contents,
                                                     SVNStatusType props, SVNStatusType lock) {
        return new SVNEvent(adminArea, name, action, kind, -1, mimeType,
                            contents, props, lock, null, null);
    }

    public static SVNEvent createMergeBeginEvent(File path, SVNMergeRange range) {
        return new SVNEvent(path.getParentFile(), path, SVNEventAction.MERGE_BEGIN, 
                            SVNNodeKind.NONE, SVNRepository.INVALID_REVISION, null, 
                            null, null, null, null, range, null);
    }
    
    public static SVNEvent createUpdateAddEvent(SVNAdminArea adminArea, SVNNodeKind kind, 
                                                SVNEntry entry) {
        return new SVNEvent(adminArea, entry.getName(), SVNEventAction.UPDATE_ADD, kind, 
                            entry.getRevision(), null, null, null, null, null, null);
    }
    
    public static SVNEvent createExportAddedEvent(File root, File file, SVNNodeKind kind) {
        return new SVNEvent(root, file, SVNEventAction.UPDATE_ADD, kind, -1,
                null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNAdminArea adminArea, SVNNodeKind kind, 
                                                   String name) {
        return new SVNEvent(adminArea, name, SVNEventAction.UPDATE_DELETE, kind, -1, null, null, 
                            null, null, null, null);
    }

    public static SVNEvent createRestoredEvent(SVNAdminArea adminArea, SVNEntry entry) {
        return new SVNEvent(adminArea, entry.getName(), SVNEventAction.RESTORE, entry.getKind(), 
                            entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createResolvedEvent(SVNAdminArea adminArea, SVNEntry entry) {
        return new SVNEvent(adminArea, entry.getName(), SVNEventAction.RESOLVED, entry.getKind(), 
                            entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createRevertedEvent(SVNAdminArea dir, SVNEntry entry) {
        return new SVNEvent(dir, entry.getName(), SVNEventAction.REVERT, entry.getKind(), 
                            entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createNotRevertedEvent(SVNAdminArea dir, SVNEntry entry) {
        return new SVNEvent(dir, entry.getName(), SVNEventAction.FAILED_REVERT, entry.getKind(), 
                            entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNAdminArea adminArea, SVNEntry entry) {
        return new SVNEvent(adminArea, entry.getName(), SVNEventAction.UPDATE_DELETE, 
                            entry.getKind(), entry.getRevision(), null, null, null, null, 
                            null, null);
    }

    public static SVNEvent createUpgradeEvent(SVNAdminArea adminArea) {
        return new SVNEvent(null, adminArea.getRoot(), SVNEventAction.UPGRADE, SVNNodeKind.DIR, -1, null, null, null, null, null, null);
    }

    public static SVNEvent createChangelistEvent(File path, SVNAdminArea adminArea, 
                                                 String changelistName, SVNEventAction action, 
                                                 SVNErrorMessage message) {
        SVNEvent event = new SVNEvent(adminArea, path.getName(), action, null, -1, null, null, 
                                      null, null, null, message);
        event.setPath(path.getAbsolutePath().replace(File.separatorChar, '/'));
        event.setChangelistName(changelistName);
        return event;
    }
    
}
