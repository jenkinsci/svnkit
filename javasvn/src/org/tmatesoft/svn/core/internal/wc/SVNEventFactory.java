/*
 * Created on 25.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SVNEventFactory {

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

    public static SVNEvent createUpdateExternalEvent(SVNWCAccess source, String path) {
        SVNEvent event = new SVNEvent(source, null, null, 
                SVNEventAction.UPDATE_EXTERNAL, null, 
                -1, null, null, null, null, null, null);
        event.setPath(path);
        return event;
    }

    public static SVNEvent createUpdateCompletedEvent(SVNWCAccess source, long revision) {
        return new SVNEvent(source, source.getTarget(), "", 
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

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.UPDATE_DELETE, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

}
