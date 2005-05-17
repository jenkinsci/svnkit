/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;

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
    private SVNDirectory myDirectory;
    private String myName;
    private String myPath;
    
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

    public static SVNEvent createUpdateAddEvent(SVNWCAccess source, SVNDirectory dir, SVNEntry entry) {
        return new SVNEvent(source, dir, entry.getName(), 
                SVNEventAction.UPDATE_ADD, entry.getKind(), 
                entry.getRevision(), null, null, null, null, null, null);
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
        myDirectory = dir;
        myName = name;        
    }
    
    public SVNWCAccess getSource() {
        return mySVNWCAccess;
    }
    
    public String getPath() {
        if (myPath != null) {
            return myPath;
        }
        if (mySVNWCAccess == null || myDirectory == null) {
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
        if (myDirectory != null) {
            return ("".equals(myName) || ".".equals(myName)) ? myDirectory.getRoot() : 
                new File(myDirectory.getRoot(), myName);   
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
}
