/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNExternalInfo {

    private String myPath;
    private File myFile;
    private SVNURL myOldExternalURL;
    private long myOldExternalRevision;
    private SVNURL myNewExternalURL;
    private long myNewExternalRevision;
    private String myOwnerPath;

    public SVNExternalInfo(String dirPath, File file, String path, SVNURL oldURL, long oldRev) {
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

    public void setNewExternal(SVNURL newURL, long newRev) {
        myNewExternalRevision = newRev;
        myNewExternalURL = newURL;
    }

    public void setOldExternal(SVNURL oldURL, long oldRev) {
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

    public SVNURL getNewURL() {
        return myNewExternalURL;
    }

    public SVNURL getOldURL() {
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
