package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLock;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 07.06.2005
 * Time: 19:35:58
 * To change this template use File | Settings | File Templates.
 */
public class SVNStatus {
    private String myURL;
    private File myFile;
    private SVNNodeKind myKind;
    private SVNRevision myRevision;
    private SVNRevision myCommittedRevision;
    private Date myCommittedDate;
    private String myAuthor;

    private SVNStatusType myContentsStatus;
    private SVNStatusType myPropertiesStatus;
    private SVNStatusType myRemoteContentsStatus;
    private SVNStatusType myRemotePropertiesStatus;

    private boolean myIsLocked;
    private boolean myIsCopied;
    private boolean myIsSwitched;

    private File myConflictNewFile;
    private File myConflictOldFile;
    private File myConflictWrkFile;
    private File myPropRejectFile;

    private String myCopyFromURL;
    private SVNRevision myCopyFromRevision;

    private SVNLock myRemoteLock;
    private SVNLock myLocalLock;

    private Map myEntryProperties;

    public SVNStatus(String URL, File file, SVNNodeKind kind, SVNRevision revision,
                     SVNRevision committedRevision, Date committedDate, String author,
                     SVNStatusType contentsStatus, SVNStatusType propertiesStatus, SVNStatusType remoteContentsStatus, SVNStatusType remotePropertiesStatus,
                     boolean isLocked, boolean isCopied, boolean isSwitched,
                     File conflictNewFile, File conflictOldFile, File conflictWrkFile, File projRejectFile,
                     String copyFromURL, SVNRevision copyFromRevision,
                     SVNLock remoteLock, SVNLock localLock,
                     Map entryProperties) {
        myURL = URL;
        myFile = file;
        myKind = kind == null ? SVNNodeKind.NONE : kind;
        myRevision = revision == null ? SVNRevision.UNDEFINED : revision;
        myCommittedRevision = committedRevision == null ? SVNRevision.UNDEFINED : committedRevision;
        myCommittedDate = committedDate;
        myAuthor = author;
        myContentsStatus = contentsStatus == null ? SVNStatusType.STATUS_NONE : contentsStatus;
        myPropertiesStatus = propertiesStatus == null ? SVNStatusType.STATUS_NONE : propertiesStatus;
        myRemoteContentsStatus = remoteContentsStatus == null ? SVNStatusType.STATUS_NONE : remoteContentsStatus;
        myRemotePropertiesStatus = remotePropertiesStatus == null ? SVNStatusType.STATUS_NONE : remotePropertiesStatus;
        myIsLocked = isLocked;
        myIsCopied = isCopied;
        myIsSwitched = isSwitched;
        myConflictNewFile = conflictNewFile;
        myConflictOldFile = conflictOldFile;
        myConflictWrkFile = conflictWrkFile;
        myCopyFromURL = copyFromURL;
        myCopyFromRevision = copyFromRevision == null ? SVNRevision.UNDEFINED : copyFromRevision;
        myRemoteLock = remoteLock;
        myLocalLock = localLock;
        myPropRejectFile = projRejectFile;
        myEntryProperties = entryProperties;
    }

    public String getURL() {
        return myURL;
    }

    public File getFile() {
        return myFile;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public SVNRevision getRevision() {
        return myRevision;
    }

    public SVNRevision getCommittedRevision() {
        return myCommittedRevision;
    }

    public Date getCommittedDate() {
        return myCommittedDate;
    }

    public String getAuthor() {
        return myAuthor;
    }

    public SVNStatusType getContentsStatus() {
        return myContentsStatus;
    }

    public SVNStatusType getPropertiesStatus() {
        return myPropertiesStatus;
    }

    public SVNStatusType getRemoteContentsStatus() {
        return myRemoteContentsStatus;
    }

    public SVNStatusType getRemotePropertiesStatus() {
        return myRemotePropertiesStatus;
    }

    public boolean isLocked() {
        return myIsLocked;
    }

    public boolean isCopied() {
        return myIsCopied;
    }

    public boolean isSwitched() {
        return myIsSwitched;
    }

    public File getConflictNewFile() {
        return myConflictNewFile;
    }

    public File getConflictOldFile() {
        return myConflictOldFile;
    }

    public File getConflictWrkFile() {
        return myConflictWrkFile;
    }

    public File getPropRejectFile() {
        return myPropRejectFile;
    }

    public String getCopyFromURL() {
        return myCopyFromURL;
    }

    public SVNRevision getCopyFromRevision() {
        return myCopyFromRevision;
    }

    public SVNLock getRemoteLock() {
        return myRemoteLock;
    }

    public SVNLock getLocalLock() {
        return myLocalLock;
    }

    public Map getEntryProperties() {
        return myEntryProperties;
    }

    public void markExternal() {
        myContentsStatus = SVNStatusType.STATUS_EXTERNAL;
    }

    public void setRemoteStatus(SVNStatusType contents, SVNStatusType props, SVNLock lock) {
        if (contents == SVNStatusType.STATUS_ADDED && myRemoteContentsStatus == SVNStatusType.STATUS_DELETED) {
            contents = SVNStatusType.STATUS_REPLACED;
        }

        myRemoteContentsStatus = contents != null ? contents : myRemoteContentsStatus;
        myRemotePropertiesStatus = props != null ? props : myRemotePropertiesStatus;
        if (lock != null) {
            myRemoteLock = lock;
        }
    }

    public void setContentsStatus(SVNStatusType statusType) {
        if (statusType != null) {
            myContentsStatus = statusType;
        }
    }
}
