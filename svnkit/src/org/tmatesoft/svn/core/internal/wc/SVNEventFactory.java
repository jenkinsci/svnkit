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

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNEventFactory {

    public static SVNEvent createMergeEvent(SVNAdminAreaInfo info, String path,
            SVNEventAction action, SVNEventAction expectedAction, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(info.getTarget().getRoot(), info.getTarget().getFile(path),
                action, expectedAction, kind, -1, null, cType, pType, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createMergeEvent(SVNAdminAreaInfo info, File file,
            SVNEventAction action, SVNEventAction expectedAction, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(info.getTarget().getRoot(), file,
                action, expectedAction, kind, -1, null, cType, pType, null, null, null);
        return event;
    }

    public static SVNEvent createMergeEvent(SVNAdminAreaInfo info, SVNAdminArea dir, String path,
            SVNEventAction action, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(info, dir, SVNPathUtil.tail(path),
                action, kind, -1, null, cType, pType, null, null, null);
        event.setPath(path);
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

    public static SVNEvent createLockEvent(SVNAdminArea dir, String path,
            SVNEventAction action, SVNLock lock, SVNErrorMessage message) {
        SVNEvent event = new SVNEvent(null, dir, SVNPathUtil.tail(path), action, SVNNodeKind.FILE, -1, null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }
    
    public static SVNEvent createAnnotateEvent(String path, long revision) {
        SVNEvent event = new SVNEvent(null, null, SVNPathUtil.tail(path), 
                SVNEventAction.ANNOTATE, SVNNodeKind.NONE, revision, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }
    public static SVNEvent createAddedEvent(SVNAdminAreaInfo info, SVNAdminArea dir, SVNEntry entry) {
        String mimeType = null;
        try {
            mimeType = dir.getProperties(entry.getName()).getPropertyValue(SVNProperty.MIME_TYPE);
        } catch (SVNException e) {
            //
        }
        return new SVNEvent(info, dir, entry.getName(), SVNEventAction.ADD,
                entry.getKind(), 0, mimeType, null, null, null, null, null);

    }

    public static SVNEvent createAddedEvent(SVNAdminArea dir, String name, SVNNodeKind kind, String mimeType) {
        return new SVNEvent(null, dir, name, SVNEventAction.ADD,
                kind, 0, mimeType, null, null, null, null, null);
    }

    public static SVNEvent createDeletedEvent(SVNAdminArea dir, String name) {
        return new SVNEvent(null, dir, name, SVNEventAction.DELETE, null, 0,
                null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateExternalEvent(SVNAdminAreaInfo info, String path) {
        SVNEvent event = new SVNEvent(info, null, null,
                SVNEventAction.UPDATE_EXTERNAL, SVNNodeKind.DIR, -1, null,
                null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createStatusExternalEvent(SVNAdminAreaInfo info, String path) {
        SVNEvent event = new SVNEvent(info, null, null,
                SVNEventAction.STATUS_EXTERNAL, SVNNodeKind.DIR, -1, null, null, null,
                null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createUpdateCompletedEvent(SVNAdminAreaInfo info,
            long revision) {
        return new SVNEvent(info, info != null ? info.getTarget() : null,
                "", SVNEventAction.UPDATE_COMPLETED, SVNNodeKind.NONE,
                revision, null, null, null, null, null, null);
    }

    public static SVNEvent createCommitCompletedEvent(SVNAdminAreaInfo info, long revision) {
        return new SVNEvent(info, null,
                "", SVNEventAction.COMMIT_COMPLETED, SVNNodeKind.NONE,
                revision, null, null, null, null, null, null);
    }

    public static SVNEvent createStatusCompletedEvent(SVNAdminAreaInfo info, long revision) {
        return new SVNEvent(info, info.getAnchor(), info.getTargetName(), SVNEventAction.STATUS_COMPLETED, SVNNodeKind.NONE, revision, null,
                null, null, null, null, null);
    }

    public static SVNEvent createUpdateModifiedEvent(SVNAdminAreaInfo info,
            SVNAdminArea adminArea, String name, SVNNodeKind kind,
            SVNEventAction action, String mimeType, SVNStatusType contents,
            SVNStatusType props, SVNStatusType lock) {
        return new SVNEvent(info, adminArea, name, action, kind, -1, mimeType,
                contents, props, lock, null, null);
    }

    public static SVNEvent createUpdateAddEvent(SVNAdminAreaInfo info,
            SVNAdminArea adminArea, SVNNodeKind kind, SVNEntry entry) {
        return new SVNEvent(info, adminArea, entry.getName(),
                SVNEventAction.UPDATE_ADD, kind, entry.getRevision(), null,
                null, null, null, null, null);
    }
    
    public static SVNEvent createExportAddedEvent(File root, File file, SVNNodeKind kind) {
        return new SVNEvent(root, file, SVNEventAction.UPDATE_ADD, kind, -1,
                null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNAdminAreaInfo info, 
            SVNAdminArea adminArea, SVNNodeKind kind, String name) {
        return new SVNEvent(info, adminArea, name, SVNEventAction.UPDATE_DELETE,
                kind, -1, null, null, null, null, null, null);
    }

    public static SVNEvent createRestoredEvent(SVNAdminAreaInfo info, SVNAdminArea adminArea, SVNEntry entry) {
        return new SVNEvent(info, adminArea, entry.getName(),
                SVNEventAction.RESTORE, entry.getKind(), entry.getRevision(),
                null, null, null, null, null, null);
    }

    public static SVNEvent createRevertedEvent(SVNAdminArea dir, SVNEntry entry) {
        return new SVNEvent(null, dir, entry.getName(),
                SVNEventAction.REVERT, entry.getKind(), entry.getRevision(),
                null, null, null, null, null, null);
    }

    public static SVNEvent createNotRevertedEvent(SVNAdminArea dir, SVNEntry entry) {
        return new SVNEvent(null, dir, entry.getName(),
                SVNEventAction.FAILED_REVERT, entry.getKind(), entry
                        .getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNAdminAreaInfo info,
            SVNAdminArea adminArea, SVNEntry entry) {
        return new SVNEvent(info, adminArea, entry.getName(),
                SVNEventAction.UPDATE_DELETE, entry.getKind(), entry
                        .getRevision(), null, null, null, null, null, null);
    }

}
