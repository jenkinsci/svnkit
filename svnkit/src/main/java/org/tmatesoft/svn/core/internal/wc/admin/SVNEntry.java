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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNEntry {

    public abstract boolean isThisDir();

    public abstract String getURL();

    public abstract SVNURL getSVNURL() throws SVNException;

    public abstract String getName();

    public abstract boolean isDirectory();

    public abstract long getRevision();

    public abstract boolean isScheduledForAddition();

    public abstract boolean isScheduledForDeletion();

    public abstract boolean isScheduledForReplacement();

    public abstract boolean isHidden();

    public abstract boolean isFile();

    public abstract String getLockToken();

    public abstract boolean isDeleted();

    public abstract boolean isAbsent();

    public abstract boolean setRevision(long revision);

    public abstract boolean setCommittedRevision(long cmtRevision);

    public abstract boolean setAuthor(String cmtAuthor);

    public abstract boolean setChangelistName(String changelistName);

    public abstract String getChangelistName();

    public abstract boolean setWorkingSize(long size);

    public abstract long getWorkingSize();

    public abstract SVNDepth getDepth();

    public abstract void setDepth(SVNDepth depth);

    public abstract boolean setURL(String url);

    public abstract void setIncomplete(boolean incomplete);

    public abstract boolean isIncomplete();

    public abstract String getConflictOld();

    public abstract void setConflictOld(String name);

    public abstract String getConflictNew();

    public abstract void setConflictNew(String name);

    public abstract String getConflictWorking();

    public abstract void setConflictWorking(String name);

    public abstract String getPropRejectFile();

    public abstract void setPropRejectFile(String name);

    public abstract String getAuthor();

    public abstract void setCommittedDate(String date);

    public abstract String getCommittedDate();

    public abstract long getCommittedRevision();

    public abstract void setTextTime(String time);

    public abstract void setKind(SVNNodeKind kind);

    public abstract void setAbsent(boolean absent);

    public abstract void setDeleted(boolean deleted);

    public abstract SVNNodeKind getKind();

    public abstract String getTextTime();

    public abstract String getChecksum();

    public abstract void setChecksum(String checksum);

    public abstract void setLockComment(String comment);

    public abstract void setLockOwner(String owner);

    public abstract void setLockCreationDate(String date);

    public abstract void setLockToken(String token);

    public abstract void setUUID(String uuid);

    public abstract void unschedule();

    public abstract void scheduleForAddition();

    public abstract void scheduleForDeletion();

    public abstract void scheduleForReplacement();

    public abstract void setSchedule(String schedule);

    public abstract void setCopyFromRevision(long revision);

    public abstract boolean setCopyFromURL(String url);

    public abstract void setCopied(boolean copied);

    public abstract String getCopyFromURL();

    public abstract SVNURL getCopyFromSVNURL() throws SVNException;

    public abstract long getCopyFromRevision();

    public abstract String getPropTime();

    public abstract void setPropTime(String time);

    public abstract boolean isCopied();

    public abstract String getUUID();

    public abstract String getRepositoryRoot();

    public abstract SVNURL getRepositoryRootURL() throws SVNException;

    public abstract boolean setRepositoryRoot(String url);

    public abstract boolean setRepositoryRootURL(SVNURL url);

    public abstract String getLockOwner();

    public abstract String getLockComment();

    public abstract String getLockCreationDate();

    public abstract String getSchedule();

    public abstract void setCachableProperties(String[] cachableProps);

    public abstract void setKeepLocal(boolean keepLocal);

    public abstract boolean isKeepLocal();

    public abstract String[] getCachableProperties();

    public abstract String[] getPresentProperties();

    public abstract String getExternalFilePath();

    public abstract SVNRevision getExternalFileRevision();

    public abstract SVNRevision getExternalFilePegRevision();

    public abstract String getTreeConflictData();

    public abstract Map getTreeConflicts() throws SVNException;

    public abstract void setTreeConflictData(String conflictData);

    public abstract void setTreeConflicts(Map treeConflicts) throws SVNException;
    
    public abstract void setExternalFilePath(String path);

    public abstract void setExternalFileRevision(SVNRevision rev);

    public abstract void setExternalFilePegRevision(SVNRevision pegRev);

    public abstract Map asMap();

    public abstract SVNAdminArea getAdminArea();

    public abstract void setParentURL(String url);

    public abstract boolean hasPropertiesModifications();

    public abstract boolean hasProperties();

    public abstract void applyChanges(Map attributes);

}