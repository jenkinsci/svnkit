package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNNodeKind;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 21:41:31
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitItem {

    private SVNRevision myRevision;
    private File myFile;
    private String myURL;
    private String myCopyFromURL;
    private SVNNodeKind myKind;

    private boolean myIsAdded;
    private boolean myIsDeleted;
    private boolean myIsPropertiesModified;
    private boolean myIsContentsModified;
    private boolean myIsCopied;
    private boolean myIsLocked;
    private String myPath;

    public SVNCommitItem(File file, String URL, String copyFromURL, SVNNodeKind kind, SVNRevision revision,
                         boolean isAdded, boolean isDeleted, boolean isPropertiesModified, boolean isContentsModified, boolean isCopied,
                         boolean locked) {
        myRevision = revision;
        myFile = file;
        myURL = URL;
        myCopyFromURL = copyFromURL;
        myKind = kind;
        myIsAdded = isAdded;
        myIsDeleted = isDeleted;
        myIsPropertiesModified = isPropertiesModified;
        myIsContentsModified = isContentsModified;
        myIsCopied = isCopied;
        myIsLocked = locked;
    }

    public SVNRevision getRevision() {
        return myRevision;
    }

    public File getFile() {
        return myFile;
    }

    public String getURL() {
        return myURL;
    }

    public String getCopyFromURL() {
        return myCopyFromURL;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public boolean isAdded() {
        return myIsAdded;
    }

    public boolean isDeleted() {
        return myIsDeleted;
    }

    public boolean isPropertiesModified() {
        return myIsPropertiesModified;
    }

    public boolean isContentsModified() {
        return myIsContentsModified;
    }

    public boolean isCopied() {
        return myIsCopied;
    }

    public boolean isLocked() {
        return myIsLocked;
    }

    public String getPath() {
        return myPath;
    }

    public void setPath(String path) {
        myPath = path;
    }
}
