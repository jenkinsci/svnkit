/*
 * Created on 25.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.PathUtil;

public class SVNEventFactory {
    
    public static SVNEvent createMergeEvent(SVNWCAccess source, String path, SVNEventAction action, 
            SVNStatusType cType, SVNStatusType pType) {
        SVNEvent event = new SVNEvent(source, null, PathUtil.tail(path), action, null,
                -1, null, cType, pType, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createCommitEvent(File rootFile, File file, SVNEventAction action, SVNNodeKind kind, String mimeType) {
        return new SVNEvent(rootFile, file, action, kind, -1, mimeType, SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createCommitEvent(File rootFile, File file, SVNEventAction action, SVNNodeKind kind, SVNStatusType textType, SVNStatusType propType) {
        return new SVNEvent(rootFile, file, action, kind, -1, null, SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createSkipEvent(File rootFile, File file, SVNEventAction action, SVNNodeKind kind) {
        return new SVNEvent(rootFile, file, action, kind, -1, null, SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, null, null);
    }

    public static SVNEvent createLockEvent(SVNWCAccess source, String path, SVNEventAction action, SVNLock lock,
            String message) {
        SVNEvent event = new SVNEvent(source, null, PathUtil.tail(path), action, null,
                -1, null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createLockEvent(String path, SVNEventAction action, SVNLock lock,
            String message) {
        SVNEvent event = new SVNEvent(null, null, PathUtil.tail(path), action, null,
                -1, null, null, null, null, lock, message);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createAddedEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        String mimeType = null;
        try {
            mimeType = dir.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.MIME_TYPE);
        } catch (SVNException e) {
        }
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.ADD, entry.getKind(), 
                0, mimeType, 
                null, null, null, null, null);
    }

    public static SVNEvent createDeletedEvent(SVNWCAccess source, SVNDirectory dir, String name) {
        return new SVNEvent(source, dir, name, 
                SVNEventAction.DELETE, null, 
                0, null, 
                null, null, null, null, null);
    }

    public static SVNEvent createUpdateExternalEvent(SVNWCAccess source, String path) {
        SVNEvent event = new SVNEvent(source, null, null,
                SVNEventAction.UPDATE_EXTERNAL, null,
                -1, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createStatusExternalEvent(SVNWCAccess source, String path) {
        SVNEvent event = new SVNEvent(source, null, null,
                SVNEventAction.STATUS_EXTERNAL, null, 
                -1, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createUpdateCompletedEvent(SVNWCAccess source, long revision) {
        return new SVNEvent(source, source != null ? source.getTarget() : null, "", 
                SVNEventAction.UPDATE_COMPLETED, null, 
                revision, null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateModifiedEvent(SVNWCAccess source, SVNDirectory dir, String name,
            SVNEventAction action, String mimeType, SVNStatusType contents, SVNStatusType props, SVNStatusType lock) {
        return new SVNEvent(source, dir, name, 
                action, null, 
                -1, mimeType, contents, props, lock, null, null);
    }

    public static SVNEvent createUpdateAddEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.UPDATE_ADD, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createExportAddedEvent(File root, File file) {
        return new SVNEvent(root, file, 
                SVNEventAction.UPDATE_ADD, null, 
                -1, null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source, SVNDirectory dir, String name) {
        return new SVNEvent(source, dir, name, 
                SVNEventAction.UPDATE_DELETE, null, 
                -1, null, null, null, null, null, null);
    }

    public static SVNEvent createRestoredEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.RESTORE, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }
    public static SVNEvent createRevertedEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.REVERT, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createResolvedEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(),
                SVNEventAction.RESOLVED, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createNotRevertedEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.FAILED_REVERT, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.UPDATE_DELETE, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

}
