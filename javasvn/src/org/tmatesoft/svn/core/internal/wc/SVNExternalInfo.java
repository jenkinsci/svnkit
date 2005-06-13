/*
 * Created on 20.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;

public class SVNExternalInfo {
    
    private String myPath;
    private File myFile;
    
    private String myOldExternalURL;
    private long myOldExternalRevision;
    
    private String myNewExternalURL;
    private long myNewExternalRevision;
    private String myOwnerPath;
    
    public SVNExternalInfo(String dirPath, File file, String path, String oldURL, long oldRev) {
        myFile = file;
        myPath = path;
        myOwnerPath = dirPath;
        myOldExternalURL = oldURL;
        myOldExternalRevision = oldRev;
    }

    public void setPath(String path) {
        myPath = path;
    }
    
    public String getOwnerPath() {
        return myOwnerPath;
    }
    
    public void setNewExternal(String newURL, long newRev) {
        myNewExternalRevision = newRev;
        myNewExternalURL = newURL;
    }

    public void setOldExternal(String oldURL, long oldRev) {
        myOldExternalRevision = oldRev;
        myOldExternalURL = oldURL;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public File getFile() {
        return myFile;
    }
    
    public boolean isModified() {
        return !isEquals();
    }
    
    private boolean isEquals() {
        if (myOldExternalURL == myNewExternalURL) {
            return myOldExternalRevision == myNewExternalRevision;
        }
        if (myOldExternalURL == null || myNewExternalURL == null) {
            return true;
        }
        if (myOldExternalURL.equals(myNewExternalURL)) {
            return myOldExternalRevision == myNewExternalRevision;
        }
        
        return false;
    }

    public long getNewRevision() {
        return myNewExternalRevision;
    }

    public String getNewURL() {
        return myNewExternalURL;
    }
    
    public String getOldURL() {
        return myOldExternalURL;
    }

    public long getOldRevision() {
        return myOldExternalRevision;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(myPath + " : ");
        sb.append(myNewExternalURL);
        if (myNewExternalRevision >= 0) {
            sb.append("(" + myNewExternalRevision + ")");
        }
        if (isModified()) {
            sb.append(" [ ");
            sb.append(myOldExternalURL);
            if (myOldExternalRevision >= 0) {
                sb.append("(" + myOldExternalRevision + ")");
            }
            sb.append(" ]");
        }
        return sb.toString();
    }

}
