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
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.TimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNInfo {

    private File myFile;

    private String myPath;

    private String myURL;

    private SVNRevision myRevision;

    private SVNNodeKind myKind;

    private String myRepositoryRootURL;

    private String myRepositoryUUID;

    private SVNRevision myCommittedRevision;

    private Date myCommittedDate;

    private String myAuthor;

    private SVNLock myLock;

    private boolean myIsRemote;

    private String mySchedule;

    private String myCopyFromURL;

    private SVNRevision myCopyFromRevision;

    private Date myTextTime;

    private Date myPropTime;

    private String myChecksum;

    private File myConflictOldFile;

    private File myConflictNewFile;

    private File myConflictWrkFile;

    private File myPropConflictFile;

    static SVNInfo createInfo(File file, SVNEntry entry) {
        if (entry == null) {
            return null;
        }
        SVNLock lock = null;
        if (entry.getLockToken() != null) {
            lock = new SVNLock(null, entry.getLockToken(),
                    entry.getLockOwner(), entry.getLockComment(), TimeUtil
                            .parseDate(entry.getLockCreationDate()), null);
        }
        return new SVNInfo(file, entry.getURL(), entry.getRevision(), entry
                .getKind(), entry.getUUID(), entry.getCommittedRevision(),
                entry.getCommittedDate(), entry.getAuthor(), entry
                        .getSchedule(), entry.getCopyFromURL(), entry
                        .getCopyFromRevision(), entry.getTextTime(), entry
                        .getPropTime(), entry.getChecksum(), entry
                        .getConflictOld(), entry.getConflictNew(), entry
                        .getConflictWorking(), entry.getPropRejectFile(), lock);
    }

    static SVNInfo createInfo(String path, String reposRootURL, String uuid,
            String url, SVNRevision revision, SVNDirEntry dirEntry, SVNLock lock) {
        if (dirEntry == null) {
            return null;
        }
        return new SVNInfo(path, url, revision, dirEntry.getKind(), uuid,
                reposRootURL, dirEntry.getRevision(), dirEntry.getDate(),
                dirEntry.getAuthor(), lock);
    }

    protected SVNInfo(File file, String url, long revision, SVNNodeKind kind,
            String uuid, long committedRevision, String committedDate,
            String author, String schedule, String copyFromURL,
            long copyFromRevision, String textTime, String propTime,
            String checksum, String conflictOld, String conflictNew,
            String conflictWorking, String propRejectFile, SVNLock lock) {
        myFile = file;
        myURL = url;
        myRevision = SVNRevision.create(revision);
        myKind = kind;
        myRepositoryUUID = uuid;

        myCommittedRevision = SVNRevision.create(committedRevision);
        myCommittedDate = committedDate != null ? TimeUtil
                .parseDate(committedDate) : null;
        myAuthor = author;

        mySchedule = schedule;
        myChecksum = checksum;
        myTextTime = textTime != null ? TimeUtil.parseDate(textTime) : null;
        myPropTime = propTime != null ? TimeUtil.parseDate(propTime) : null;

        myCopyFromURL = copyFromURL;
        myCopyFromRevision = SVNRevision.create(copyFromRevision);

        myLock = lock;

        if (file != null) {
            if (conflictOld != null) {
                myConflictOldFile = new File(file.getParentFile(), conflictOld);
            }
            if (conflictNew != null) {
                myConflictNewFile = new File(file.getParentFile(), conflictNew);
            }
            if (conflictWorking != null) {
                myConflictWrkFile = new File(file.getParentFile(),
                        conflictWorking);
            }
            if (propRejectFile != null) {
                myPropConflictFile = new File(file.getParentFile(),
                        propRejectFile);
            }
        }

        myIsRemote = false;
    }

    protected SVNInfo(String path, String url, SVNRevision revision,
            SVNNodeKind kind, String uuid, String reposRootURL,
            long comittedRevision, Date date, String author, SVNLock lock) {
        myIsRemote = true;
        myURL = url;
        myRevision = revision;
        myKind = kind;
        myRepositoryRootURL = reposRootURL;
        myRepositoryUUID = uuid;

        myCommittedDate = date;
        myCommittedRevision = SVNRevision.create(comittedRevision);
        myAuthor = author;

        myLock = lock;
        myPath = path;
    }

    public String getAuthor() {
        return myAuthor;
    }

    public String getChecksum() {
        return myChecksum;
    }

    public Date getCommittedDate() {
        return myCommittedDate;
    }

    public SVNRevision getCommittedRevision() {
        return myCommittedRevision;
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

    public SVNRevision getCopyFromRevision() {
        return myCopyFromRevision;
    }

    public String getCopyFromURL() {
        return myCopyFromURL;
    }

    public File getFile() {
        return myFile;
    }

    public boolean isRemote() {
        return myIsRemote;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public SVNLock getLock() {
        return myLock;
    }

    public String getPath() {
        return myPath;
    }

    public File getPropConflictFile() {
        return myPropConflictFile;
    }

    public Date getPropTime() {
        return myPropTime;
    }

    public String getRepositoryRootURL() {
        return myRepositoryRootURL;
    }

    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }

    public SVNRevision getRevision() {
        return myRevision;
    }

    public String getSchedule() {
        return mySchedule;
    }

    public Date getTextTime() {
        return myTextTime;
    }

    public String getURL() {
        return myURL;
    }

}
