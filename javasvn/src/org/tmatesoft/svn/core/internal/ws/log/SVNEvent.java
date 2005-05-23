/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNNodeKind;

public class SVNEvent {
    
    private String myMimeType;
    private String myErrorMessage;
    
    private SVNEventAction myAction;
    private SVNNodeKind myNodeKind;
    private long myRevision;
    
    private SVNEventStatus myContentsStatus;
    private SVNEventStatus myPropertiesStatus;
    private SVNEventStatus myLockStatus;
    
    private SVNLock myLock;
    
    private SVNWCAccess mySVNWCAccess;
    private String myName;
    private String myPath;
    private File myRoot;
    
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

    public static SVNEvent createUpdateDeleteEvent(SVNWCAccess source, SVNDirectory dir, String name) {
        return new SVNEvent(source, dir, name, 
                SVNEventAction.UPDATE_DELETE, null, 
                -1, null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateAddEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.UPDATE_ADD, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
    }

    public static SVNEvent createUpdateModifiedEvent(SVNWCAccess source, SVNDirectory dir, String name,
            SVNEventAction action, String mimeType, SVNEventStatus contents, SVNEventStatus props, SVNEventStatus lock) {
        return new SVNEvent(source, dir, name, 
                action, null, 
                -1, mimeType, contents, props, lock, null, null);
    }

    public static SVNEvent createUpdateCompletedEvent(SVNWCAccess source, long revision) {
        return new SVNEvent(source, source.getTarget(), "", 
                SVNEventAction.UPDATE_COMPLETED, null, 
                revision, null, null, null, null, null, null);
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

    public SVNEvent(SVNWCAccess source, SVNDirectory dir, String name, 
            SVNEventAction action, 
            SVNNodeKind kind, 
            long revision, 
            String mimetype, 
            SVNEventStatus cstatus, SVNEventStatus pstatus, SVNEventStatus lstatus, 
            SVNLock lock,
            String error) {
        myMimeType = mimetype;
        myErrorMessage = error;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNEventStatus.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNEventStatus.INAPPLICABLE : pstatus;
        myLockStatus = lstatus == null ? SVNEventStatus.INAPPLICABLE : lstatus;
        myLock = lock;
        
        mySVNWCAccess = source;
        myRoot = dir != null ? dir.getRoot() : null;
        myName = name;        
    }
    
    public SVNWCAccess getSource() {
        return mySVNWCAccess;
    }
    
    public String getPath() {
        if (myPath != null) {
            return myPath;
        }
        if (mySVNWCAccess == null) {
            return myName;
        }
        File file = getFile();
        File root = mySVNWCAccess.getAnchor().getRoot();
        String rootPath = root.getAbsolutePath().replace(File.separatorChar, '/');
        String filePath = file.getAbsolutePath().replace(File.separatorChar, '/');
        myPath = filePath.substring(rootPath.length());
        if (myPath.startsWith("/")) {
            myPath = myPath.substring(1);
        }
        return myPath;
    }
    
    public File getFile() {
        
        if (myRoot != null) {
            return ("".equals(myName) || ".".equals(myName)) ? myRoot : 
                new File(myRoot, myName);   
        }
        return null;
    }     

    public SVNEventAction getAction() {
        return myAction;
    }

    public SVNEventStatus getContentsStatus() {
        return myContentsStatus;
    }

    public String getErrorMessage() {
        return myErrorMessage;
    }

    public SVNLock getLock() {
        return myLock;
    }

    public SVNEventStatus getLockStatus() {
        return myLockStatus;
    }

    public String getMimeType() {
        return myMimeType;
    }

    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }

    public SVNEventStatus getPropertiesStatus() {
        return myPropertiesStatus;
    }

    public long getRevision() {
        return myRevision;
    }

    void setPath(String path) {
        myPath = path;
    }
}
