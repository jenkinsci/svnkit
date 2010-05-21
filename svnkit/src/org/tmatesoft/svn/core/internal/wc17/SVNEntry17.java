/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNEntry17 extends SVNEntry {

    private SVNEntryInfo myEntryInfo;

    public SVNEntry17(SVNEntryInfo entryInfo) {
        this.myEntryInfo = entryInfo;
    }

    public Map asMap() {
        return null;
    }

    public SVNAdminArea getAdminArea() {
        return null;
    }

    public String getAuthor() {
        return myEntryInfo.getCommittedAuthor();
    }

    public String[] getCachableProperties() {
        return null;
    }

    public String getChangelistName() {
        return null;
    }

    public String getChecksum() {
        return null;
    }

    public String getCommittedDate() {
        return null;
    }

    public long getCommittedRevision() {
        return 0;
    }

    public String getConflictNew() {
        return null;
    }

    public String getConflictOld() {
        return null;
    }

    public String getConflictWorking() {
        return null;
    }

    public long getCopyFromRevision() {
        return 0;
    }

    public SVNURL getCopyFromSVNURL() throws SVNException {
        return null;
    }

    public String getCopyFromURL() {
        return null;
    }

    public SVNDepth getDepth() {
        return null;
    }

    public String getExternalFilePath() {
        return null;
    }

    public SVNRevision getExternalFilePegRevision() {
        return null;
    }

    public SVNRevision getExternalFileRevision() {
        return null;
    }

    public SVNNodeKind getKind() {
        return null;
    }

    public String getLockComment() {
        return null;
    }

    public String getLockCreationDate() {
        return null;
    }

    public String getLockOwner() {
        return null;
    }

    public String getLockToken() {
        return null;
    }

    public String getName() {
        return null;
    }

    public String[] getPresentProperties() {
        return null;
    }

    public String getPropRejectFile() {
        return null;
    }

    public String getPropTime() {
        return null;
    }

    public String getRepositoryRoot() {
        return null;
    }

    public SVNURL getRepositoryRootURL() throws SVNException {
        return null;
    }

    public long getRevision() {
        return 0;
    }

    public SVNURL getSVNURL() throws SVNException {
        return null;
    }

    public String getSchedule() {
        return null;
    }

    public String getTextTime() {
        return null;
    }

    public String getTreeConflictData() {
        return null;
    }

    public Map getTreeConflicts() throws SVNException {
        return null;
    }

    public String getURL() {
        return null;
    }

    public String getUUID() {
        return null;
    }

    public long getWorkingSize() {
        return 0;
    }

    public boolean isAbsent() {
        return false;
    }

    public boolean isCopied() {
        return false;
    }

    public boolean isDeleted() {
        return false;
    }

    public boolean isDirectory() {
        return false;
    }

    public boolean isFile() {
        return false;
    }

    public boolean isHidden() {
        return false;
    }

    public boolean isIncomplete() {
        return false;
    }

    public boolean isKeepLocal() {
        return false;
    }

    public boolean isScheduledForAddition() {
        return false;
    }

    public boolean isScheduledForDeletion() {
        return false;
    }

    public boolean isScheduledForReplacement() {
        return false;
    }

    public boolean isThisDir() {
        return false;
    }

    public void loadProperties(Map entryProps) {
    }

    public void scheduleForAddition() {
    }

    public void scheduleForDeletion() {
    }

    public void scheduleForReplacement() {
    }

    public void setAbsent(boolean absent) {
    }

    public boolean setAuthor(String cmtAuthor) {
        return false;
    }

    public void setCachableProperties(String[] cachableProps) {
    }

    public boolean setChangelistName(String changelistName) {
        return false;
    }

    public void setChecksum(String checksum) {
    }

    public void setCommittedDate(String date) {
    }

    public boolean setCommittedRevision(long cmtRevision) {
        return false;
    }

    public void setConflictNew(String name) {
    }

    public void setConflictOld(String name) {
    }

    public void setConflictWorking(String name) {
    }

    public void setCopied(boolean copied) {
    }

    public void setCopyFromRevision(long revision) {
    }

    public boolean setCopyFromURL(String url) {
        return false;
    }

    public void setDeleted(boolean deleted) {
    }

    public void setDepth(SVNDepth depth) {
    }

    public void setIncomplete(boolean incomplete) {
    }

    public void setKeepLocal(boolean keepLocal) {
    }

    public void setKind(SVNNodeKind kind) {
    }

    public void setLockComment(String comment) {
    }

    public void setLockCreationDate(String date) {
    }

    public void setLockOwner(String owner) {
    }

    public void setLockToken(String token) {
    }

    public void setPropRejectFile(String name) {
    }

    public void setPropTime(String time) {
    }

    public boolean setRepositoryRoot(String url) {
        return false;
    }

    public boolean setRepositoryRootURL(SVNURL url) {
        return false;
    }

    public boolean setRevision(long revision) {
        return false;
    }

    public void setSchedule(String schedule) {
    }

    public void setTextTime(String time) {
    }

    public void setTreeConflictData(String conflictData) {
    }

    public void setTreeConflicts(Map treeConflicts) throws SVNException {
    }

    public boolean setURL(String url) {
        return false;
    }

    public void setUUID(String uuid) {
    }

    public boolean setWorkingSize(long size) {
        return false;
    }

    public void unschedule() {
    }

}
