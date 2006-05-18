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

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEventFactory {

    public static SVNEvent createMergeEvent(SVNWCAccess source, String path,
            SVNEventAction action, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(source, null, SVNPathUtil.tail(path),
                action, kind, -1, null, cType, pType, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createMergeEvent(SVNWCAccess source, String path,
            SVNEventAction action, SVNEventAction expectedAction, SVNStatusType cType, SVNStatusType pType, SVNNodeKind kind) {
        SVNEvent event = new SVNEvent(source, null, SVNPathUtil.tail(path),
                action, expectedAction, kind, -1, null, cType, pType, null, null, null);
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

    public static SVNEvent createLockEvent(SVNWCAccess source, String path,
            SVNEventAction action, SVNLock lock, SVNErrorMessage message) {
        SVNEvent event = new SVNEvent(source, null, SVNPathUtil.tail(path), action, SVNNodeKind.FILE, -1, null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createLockEvent(String path, SVNEventAction action, SVNLock lock, SVNErrorMessage message) {
        SVNEvent event = new SVNEvent(null, null, SVNPathUtil.tail(path), action, SVNNodeKind.FILE, -1, null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }
    
    public static SVNEvent createAnnotateEvent(String path, long revision) {
        SVNEvent event = new SVNEvent(null, null, SVNPathUtil.tail(path), 
                SVNEventAction.ANNOTATE, SVNNodeKind.NONE, revision, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createAddedEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        String mimeType = null;
        try {
            mimeType = dir.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.MIME_TYPE);
        } catch (SVNException e) {
            //
        }
        return new SVNEvent(source, dir, entry.getName(), SVNEventAction.ADD,
                entry.getKind(), 0, mimeType, null, null, null, null, null);
    }

    public static SVNEvent createDeletedEvent(SVNWCAccess source,
            SVNDirectory dir, String name) {
        return new SVNEvent(source, dir, name, SVNEventAction.DELETE, null, 0,
                null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateExternalEvent(SVNWCAccess source,
            String path) {
        SVNEvent event = new SVNEvent(source, null, null,
                SVNEventAction.UPDATE_EXTERNAL, SVNNodeKind.DIR, -1, null,
                null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createStatusExternalEvent(SVNWCAccess source,
            String path) {
        SVNEvent event = new SVNEvent(source, null, null,
                SVNEventAction.STATUS_EXTERNAL, SVNNodeKind.DIR, -1, null, null, null,
                null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createUpdateCompletedEvent(SVNWCAccess source,
            long revision) {
        return new SVNEvent(source, source != null ? source.getTarget() : null,
                "", SVNEventAction.UPDATE_COMPLETED, SVNNodeKind.NONE,
                revision, null, null, null, null, null, null);
    }

    public static SVNEvent createCommitCompletedEvent(SVNWCAccess source, long revision) {
        return new SVNEvent(source, source != null ? source.getTarget() : null,
                "", SVNEventAction.COMMIT_COMPLETED, SVNNodeKind.NONE,
                revision, null, null, null, null, null, null);
    }

    public static SVNEvent createStatusCompletedEvent(SVNWCAccess source,
            long revision) {
        return new SVNEvent(source, source != null ? source.getTarget() : null,
                "", SVNEventAction.STATUS_COMPLETED, SVNNodeKind.NONE, revision, null,
                null, null, null, null, null);
    }

    public static SVNEvent createUpdateModifiedEvent(SVNWCAccess source,
            SVNDirectory dir, String name, SVNNodeKind kind,
            SVNEventAction action, String mimeType, SVNStatusType contents,
            SVNStatusType props, SVNStatusType lock) {
        return new SVNEvent(source, dir, name, action, kind, -1, mimeType,
                contents, props, lock, null, null);
    }

    public static SVNEvent createUpdateAddEvent(SVNWCAccess source,
            SVNDirectory dir, SVNNodeKind kind, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.UPDATE_ADD, kind, entry.getRevision(), null,
                null, null, null, null, null);
    }

    public static SVNEvent createExportAddedEvent(File root, File file, SVNNodeKind kind) {
        return new SVNEvent(root, file, SVNEventAction.UPDATE_ADD, kind, -1,
                null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source, 
            SVNDirectory dir, SVNNodeKind kind, String name) {
        return new SVNEvent(source, dir, name, SVNEventAction.UPDATE_DELETE,
                kind, -1, null, null, null, null, null, null);
    }

    public static SVNEvent createRestoredEvent(SVNWCAccess source,
            SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.RESTORE, entry.getKind(), entry.getRevision(),
                null, null, null, null, null, null);
    }

    public static SVNEvent createRevertedEvent(SVNWCAccess source,
            SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.REVERT, entry.getKind(), entry.getRevision(),
                null, null, null, null, null, null);
    }

    public static SVNEvent createResolvedEvent(SVNWCAccess source,
            SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.RESOLVED, entry.getKind(), entry.getRevision(),
                null, null, null, null, null, null);
    }

    public static SVNEvent createNotRevertedEvent(SVNWCAccess source,
            SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.FAILED_REVERT, entry.getKind(), entry
                        .getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source,
            SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.UPDATE_DELETE, entry.getKind(), entry
                        .getRevision(), null, null, null, null, null, null);
    }

}
