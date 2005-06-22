/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNNodeKind;

public class SVNEvent {
    
    private String myMimeType;
    private String myErrorMessage;
    
    private SVNEventAction myAction;
    private SVNNodeKind myNodeKind;
    private long myRevision;
    
    private SVNStatusType myContentsStatus;
    private SVNStatusType myPropertiesStatus;
    private SVNStatusType myLockStatus;
    
    private SVNLock myLock;
    
    private SVNWCAccess mySVNWCAccess;
    private String myName;
    private String myPath;
    private File myRoot;
    private File myRootFile;
    
    public SVNEvent(String errorMessage) {
        myErrorMessage = errorMessage;
    }
    
    public SVNEvent(SVNWCAccess source, SVNDirectory dir, String name, 
            SVNEventAction action, 
            SVNNodeKind kind, 
            long revision, 
            String mimetype, 
            SVNStatusType cstatus, SVNStatusType pstatus, SVNStatusType lstatus, 
            SVNLock lock,
            String error) {
        myMimeType = mimetype;
        myErrorMessage = error;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE : pstatus;
        myLockStatus = lstatus == null ? SVNStatusType.INAPPLICABLE : lstatus;
        myLock = lock;
        
        mySVNWCAccess = source;
        myRoot = dir != null ? dir.getRoot() : null;
        myName = name;        
    }

    public SVNEvent(File rootFile, File file, 
            SVNEventAction action, 
            SVNNodeKind kind, 
            long revision, 
            String mimetype, 
            SVNStatusType cstatus, SVNStatusType pstatus, SVNStatusType lstatus, 
            SVNLock lock,
            String error) {
        myMimeType = mimetype;
        myErrorMessage = error;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE : pstatus;
        myLockStatus = lstatus == null ? SVNStatusType.INAPPLICABLE : lstatus;
        myLock = lock;
        
        myRoot = file != null ? file.getParentFile() : null;
        myRootFile = rootFile;
        myName = file != null ? file.getName() : "";
    }

    public SVNEvent(String path, File rootFile, File file, 
            SVNEventAction action, 
            SVNNodeKind kind, 
            long revision, 
            String mimetype, 
            SVNStatusType cstatus, SVNStatusType pstatus) {
        myMimeType = mimetype;
        myErrorMessage = null;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE : pstatus;
        myLockStatus = SVNStatusType.INAPPLICABLE;
        myLock = null;
        myPath = path;
        
        myRoot = file != null ? file.getParentFile() : null;
        myRootFile = rootFile;
        myName = file != null ? file.getName() : "";
    }
    
    public SVNWCAccess getSource() {
        return mySVNWCAccess;
    }
    
    public String getPath() {
        if (myPath != null) {
            return myPath;
        }
        if (mySVNWCAccess == null && myRootFile == null) {
            return myName;
        } 
        File file = getFile();
        File root = mySVNWCAccess != null ? mySVNWCAccess.getAnchor().getRoot() : myRootFile;
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
        } else if (mySVNWCAccess != null && getPath() != null) {
            return new File(mySVNWCAccess.getAnchor().getRoot(), getPath());
        }
        return null;
    }     

    public SVNEventAction getAction() {
        return myAction;
    }

    public SVNStatusType getContentsStatus() {
        return myContentsStatus;
    }

    public String getErrorMessage() {
        return myErrorMessage;
    }

    public SVNLock getLock() {
        return myLock;
    }

    public SVNStatusType getLockStatus() {
        return myLockStatus;
    }

    public String getMimeType() {
        return myMimeType;
    }

    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }

    public SVNStatusType getPropertiesStatus() {
        return myPropertiesStatus;
    }

    public long getRevision() {
        return myRevision;
    }

    public void setPath(String path) {
        myPath = path;
    }
}
