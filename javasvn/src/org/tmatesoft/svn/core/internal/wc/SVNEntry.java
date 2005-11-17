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

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEntry implements Comparable {

    private SVNEntries myEntries;

    private String myName;

    public SVNEntry(SVNEntries entries, String name) {
        myEntries = entries;
        myName = name;
    }

    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return false;
        }
        SVNEntry entry = (SVNEntry) obj;
        return entry.myEntries == myEntries && entry.myName.equals(myName);
    }

    public int hashCode() {
        return myEntries.hashCode() + 17 * myName.hashCode();
    }

    public int compareTo(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return 1;
        }
        return myName.compareTo(((SVNEntry) obj).myName);
    }

    public String getURL() {
        String url = myEntries.getPropertyValue(myName, SVNProperty.URL);
        if (url == null && !"".equals(myName)) {
            url = myEntries.getPropertyValue("", SVNProperty.URL);
            url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myName));
        }
        return url;
    }
    
    public SVNURL getSVNURL() throws SVNException {
        String url = getURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public String getName() {
        return myName;
    }

    public boolean isDirectory() {
        return SVNProperty.KIND_DIR.equals(myEntries.getPropertyValue(myName, SVNProperty.KIND));
    }

    public long getRevision() {
        String revStr = myEntries.getPropertyValue(myName, SVNProperty.REVISION);
        if (revStr == null && !"".equals(myName)) {
            revStr = myEntries.getPropertyValue("", SVNProperty.REVISION);
        }
        if (revStr == null) {
            return -1;
        }
        return Long.parseLong(revStr);
    }

    public boolean isScheduledForAddition() {
        return SVNProperty.SCHEDULE_ADD.equals(myEntries.getPropertyValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForDeletion() {
        return SVNProperty.SCHEDULE_DELETE.equals(myEntries.getPropertyValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForReplacement() {
        return SVNProperty.SCHEDULE_REPLACE.equals(myEntries.getPropertyValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isHidden() {
        return (isDeleted() || isAbsent()) && !isScheduledForAddition()
                && !isScheduledForReplacement();
    }

    public boolean isFile() {
        return SVNProperty.KIND_FILE.equals(myEntries.getPropertyValue(myName,
                SVNProperty.KIND));
    }

    public String getLockToken() {
        return myEntries.getPropertyValue(myName, SVNProperty.LOCK_TOKEN);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.toString().equals(myEntries.getPropertyValue(myName, SVNProperty.DELETED));
    }

    public boolean isAbsent() {
        return Boolean.TRUE.toString().equals(
                myEntries.getPropertyValue(myName, SVNProperty.ABSENT));
    }

    public String toString() {
        return myName;
    }

    public boolean setRevision(long revision) {
        return myEntries.setPropertyValue(myName, SVNProperty.REVISION, Long
                .toString(revision));
    }

    public boolean setURL(String url) {
        return myEntries.setPropertyValue(myName, SVNProperty.URL, url);
    }

    public void setIncomplete(boolean incomplete) {
        myEntries.setPropertyValue(myName, SVNProperty.INCOMPLETE,
                incomplete ? Boolean.TRUE.toString() : null);
    }

    public boolean isIncomplete() {
        return Boolean.TRUE.toString().equals(
                myEntries.getPropertyValue(myName, SVNProperty.INCOMPLETE));
    }

    public String getConflictOld() {
        return myEntries.getPropertyValue(myName, SVNProperty.CONFLICT_OLD);
    }

    public void setConflictOld(String name) {
        myEntries.setPropertyValue(myName, SVNProperty.CONFLICT_OLD, name);
    }

    public String getConflictNew() {
        return myEntries.getPropertyValue(myName, SVNProperty.CONFLICT_NEW);
    }

    public void setConflictNew(String name) {
        myEntries.setPropertyValue(myName, SVNProperty.CONFLICT_NEW, name);
    }

    public String getConflictWorking() {
        return myEntries.getPropertyValue(myName, SVNProperty.CONFLICT_WRK);
    }

    public void setConflictWorking(String name) {
        myEntries.setPropertyValue(myName, SVNProperty.CONFLICT_WRK, name);
    }

    public String getPropRejectFile() {
        return myEntries.getPropertyValue(myName, SVNProperty.PROP_REJECT_FILE);
    }

    public void setPropRejectFile(String name) {
        myEntries.setPropertyValue(myName, SVNProperty.PROP_REJECT_FILE, name);
    }

    public String getAuthor() {
        return myEntries.getPropertyValue(myName, SVNProperty.LAST_AUTHOR);
    }

    public String getCommittedDate() {
        return myEntries.getPropertyValue(myName, SVNProperty.COMMITTED_DATE);
    }

    public long getCommittedRevision() {
        String rev = myEntries.getPropertyValue(myName,
                SVNProperty.COMMITTED_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public void setTextTime(String time) {
        myEntries.setPropertyValue(myName, SVNProperty.TEXT_TIME, time);
    }

    public void setKind(SVNNodeKind kind) {
        String kindStr = kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : (kind == SVNNodeKind.FILE ? SVNProperty.KIND_FILE : null);
        myEntries.setPropertyValue(myName, SVNProperty.KIND, kindStr);
    }

    public void setAbsent(boolean absent) {
        myEntries.setPropertyValue(myName, SVNProperty.ABSENT,
                absent ? Boolean.TRUE.toString() : null);
    }

    public void setDeleted(boolean deleted) {
        myEntries.setPropertyValue(myName, SVNProperty.DELETED,
                deleted ? Boolean.TRUE.toString() : null);
    }

    public SVNNodeKind getKind() {
        String kind = myEntries.getPropertyValue(myName, SVNProperty.KIND);
        if (SVNProperty.KIND_DIR.equals(kind)) {
            return SVNNodeKind.DIR;
        } else if (SVNProperty.KIND_FILE.equals(kind)) {
            return SVNNodeKind.FILE;
        }
        return SVNNodeKind.UNKNOWN;
    }
    
    public String getTextTime() {
        return myEntries.getPropertyValue(myName, SVNProperty.TEXT_TIME);
    }

    public String getChecksum() {
        return myEntries.getPropertyValue(myName, SVNProperty.CHECKSUM);
    }

    public void setLockComment(String comment) {
        myEntries.setPropertyValue(myName, SVNProperty.LOCK_COMMENT, comment);
    }

    public void setLockOwner(String owner) {
        myEntries.setPropertyValue(myName, SVNProperty.LOCK_OWNER, owner);
    }

    public void setLockCreationDate(String date) {
        myEntries.setPropertyValue(myName, SVNProperty.LOCK_CREATION_DATE, date);
    }

    public void setLockToken(String token) {
        myEntries.setPropertyValue(myName, SVNProperty.LOCK_TOKEN, token);
    }

    public void setUUID(String uuid) {
        myEntries.setPropertyValue(myName, SVNProperty.UUID, uuid);
    }

    public void unschedule() {
        myEntries.setPropertyValue(myName, SVNProperty.SCHEDULE, null);
    }

    public void scheduleForAddition() {
        myEntries.setPropertyValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_ADD);
    }

    public void scheduleForDeletion() {
        myEntries.setPropertyValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_DELETE);
    }

    public void scheduleForReplacement() {
        myEntries.setPropertyValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_REPLACE);
    }

    public void setCopyFromRevision(long revision) {
        myEntries.setPropertyValue(myName, SVNProperty.COPYFROM_REVISION,
                revision >= 0 ? Long.toString(revision) : null);
    }

    public void setCopyFromURL(String url) {
        myEntries.setPropertyValue(myName, SVNProperty.COPYFROM_URL, url);
    }

    public void setCopied(boolean copied) {
        myEntries.setPropertyValue(myName, SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
    }

    public String getCopyFromURL() {
        return myEntries.getPropertyValue(myName, SVNProperty.COPYFROM_URL);
    }

    public SVNURL getCopyFromSVNURL() throws SVNException {
        String url = getCopyFromURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public long getCopyFromRevision() {
        String rev = myEntries.getPropertyValue(myName,
                SVNProperty.COPYFROM_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public String getPropTime() {
        return myEntries.getPropertyValue(myName, SVNProperty.PROP_TIME);
    }

    public void setPropTime(String time) {
        myEntries.setPropertyValue(myName, SVNProperty.PROP_TIME, time);
    }

    public boolean isCopied() {
        return Boolean.TRUE.toString().equals(
                myEntries.getPropertyValue(myName, SVNProperty.COPIED));
    }

    public String getUUID() {
        return myEntries.getPropertyValue(myName, SVNProperty.UUID);
    }

    public String getRepositoryRoot() {
        return myEntries.getPropertyValue(myName, SVNProperty.REPOS);
    }

    public SVNURL getRepositoryRootURL() throws SVNException {
        String url = getRepositoryRoot();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }
    
    public boolean setRepositoryRoot(String url) {
        return myEntries.setPropertyValue(myName, SVNProperty.REPOS, url);
    }

    public boolean setRepositoryRootURL(SVNURL url) {
        return setRepositoryRoot(url == null ? null : url.toString());
    }

    public void loadProperties(Map entryProps) {
        if (entryProps == null) {
            return;
        }
        for (Iterator propNames = entryProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            myEntries.setPropertyValue(myName, propName, (String) entryProps.get(propName));
        }
    }

    public String getLockOwner() {
        return myEntries.getPropertyValue(myName, SVNProperty.LOCK_OWNER);
    }

    public String getLockComment() {
        return myEntries.getPropertyValue(myName, SVNProperty.LOCK_COMMENT);
    }

    public String getLockCreationDate() {
        return myEntries.getPropertyValue(myName,
                SVNProperty.LOCK_CREATION_DATE);
    }

    public String getSchedule() {
        return myEntries.getPropertyValue(myName, SVNProperty.SCHEDULE);
    }

    public Map asMap() {
        return myEntries.getEntryMap(myName);
    }
}