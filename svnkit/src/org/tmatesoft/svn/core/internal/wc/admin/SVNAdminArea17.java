/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNRepositoryScanResult;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17.IsDirDeletedResult;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNPropertyConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTextConflictDescription;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea17 extends SVNAdminArea {
    private SVNWorkingCopyDB17 myWCDb;
    
    public SVNAdminArea17(File dir) {
        super(dir);
        myWCDb = new SVNWorkingCopyDB17();
    }

    public void addTreeConflict(SVNTreeConflictDescription conflict) throws SVNException {
    }

    public SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself, SVNDepth depth) throws SVNException {
        return null;
    }

    public SVNTreeConflictDescription deleteTreeConflict(String name) throws SVNException {
        return null;
    }

    protected Map fetchEntries() throws SVNException {
        File path = getRoot();
        
        SqlJetDb sdb = myWCDb.getDBTemp(path, false);
        List childNames = myWCDb.gatherChildren(path, false);
        childNames.add(getThisDirName());
        Map<String, SVNEntry> entries = new HashMap<String, SVNEntry>();
        for (ListIterator iterator = childNames.listIterator(childNames.size()); iterator.hasPrevious();) {
            String name = (String) iterator.previous();
            Map entryAttributes = new HashMap();
            SVNEntry entry = new SVNEntry(entryAttributes, this, name);
            entries.put(name, entry);
            SVNEntry parentEntry = null;
            if (entry.isThisDir()) {
                parentEntry = entry;
            }
            
            SVNEntryInfo info = myWCDb.readInfo(path, true, true, true, false, false, true, true, false, true, true, true, false);
            entry.setRevision(info.getRevision());
            entry.setRepositoryRoot(info.getReposRootURL());
            entry.setUUID(info.getUUID());
            entry.setCommittedRevision(info.getCommittedRevision());
            entry.setAuthor(info.getCommittedAuthor());
            entry.setTextTime(SVNDate.formatDate(info.getLastTextTime()));
            entry.setDepth(info.getDepth());
            entry.setChangelistName(info.getChangeList());
            entry.setCopyFromRevision(info.getCopyFromRevision());
            entry.setCommittedDate(SVNDate.formatDate(info.getCommittedDate()));
            String originalReposPath = info.getOriginalReposPath();
            String originalRootURL = info.getOriginalRootURL();
            SVNChecksum checksum = info.getChecksum();
            boolean conflicted = info.isConflicted();
            SVNWCDbLock lock = info.getWCDBLock();
            long translatedSize = info.getWorkingSize();
            
            if (getThisDirName().equals(name)) {
                Map treeConflicts = null;
                Collection conflictVictims = myWCDb.readConflictVictims(path);
                for (Iterator conflictVictimsIter = conflictVictims.iterator(); conflictVictimsIter.hasNext();) {
                    String childName = (String) conflictVictimsIter.next();
                    File childFile = new File(path, childName);
                    Collection childConflicts = myWCDb.readConflicts(childFile);
                    for (Iterator childConflictsIter = childConflicts.iterator(); childConflictsIter.hasNext();) {
                        SVNConflictDescription conflict = (SVNConflictDescription) childConflictsIter.next();
                        if (conflict instanceof SVNTreeConflictDescription) {
                            SVNTreeConflictDescription treeConflict = (SVNTreeConflictDescription) conflict;
                            if (treeConflicts == null) {
                                treeConflicts = new HashMap();
                            }
                            treeConflicts.put(childName, treeConflict);
                        }
                    }
                }
                
                if (treeConflicts != null) {
                    entry.setTreeConflicts(treeConflicts);
                }
            }
            
            SVNWCDbStatus status = info.getWCDBStatus();
            SVNWCDbKind kind = info.getWCDBKind();
            String reposPath = info.getReposPath();
            if (status == SVNWCDbStatus.NORMAL || status == SVNWCDbStatus.INCOMPLETE) {
                boolean notPresent = false;
                if (kind == SVNWCDbKind.DIR) {
                    notPresent = myWCDb.checkIfIsNotPresent(sdb, 1, name);
                }
                if (notPresent) {
                    entry.setSchedule(null);
                    entry.setDeleted(true);
                } else {
                    entry.setSchedule(null);
                    if (reposPath == null) {
                        SVNRepositoryScanResult reposScanResult = myWCDb.scanBaseRepos(path);
                        SVNRepositoryInfo reposInfo = reposScanResult.getReposInfo();
                        entry.setRepositoryRoot(reposInfo.getRootURL());
                        entry.setUUID(reposInfo.getUUID());
                    }
                    entry.setIncomplete(true);
                }
            } else if (status == SVNWCDbStatus.DELETED || status == SVNWCDbStatus.OBSTRUCTED_DELETE) {
                entry.scheduleForDeletion();
                if (entry.isThisDir()) {
                    entry.setKeepLocal(myWCDb.determineKeepLocal(path));
                }
            } else if (status == SVNWCDbStatus.ADDED || status == SVNWCDbStatus.OBSTRUCTED_ADD) {
                if (!entry.isThisDir()) {
                    SVNErrorManager.assertionFailure(parentEntry != null, null, SVNLogType.WC);
                    SVNErrorManager.assertionFailure(!SVNRevision.isValidRevisionNumber(entry.getRevision()), null, SVNLogType.WC);
                    entry.setRevision(parentEntry.getRevision());
                }
                
                if (info.isBaseShadowed()) {
                    SVNEntryInfo baseInfo = myWCDb.getBaseInfo(path, false);
                    entry.setRevision(baseInfo.getRevision());
                    if (baseInfo.getWCDBStatus() == SVNWCDbStatus.NOT_PRESENT) {
                        entry.setDeleted(true);
                        entry.scheduleForAddition();
                    } else {
                        entry.scheduleForReplacement();
                    }
                } else {
                    if (kind == SVNWCDbKind.DIR && !entry.isThisDir()) {
                        IsDirDeletedResult isDirDeletedInfo = myWCDb.isDirDeleted(path);
                        entry.setDeleted(isDirDeletedInfo.isDeleted());
                        entry.setRevision(isDirDeletedInfo.getRevision());
                    }
                   
                    if (entry.isDeleted()) {
                        entry.scheduleForAddition();
                    } else {
                        if (!SVNRevision.isValidRevisionNumber(entry.getCopyFromRevision()) && 
                                !SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                            entry.setRevision(0);
                        }
                        if (status == SVNWCDbStatus.OBSTRUCTED_ADD) {
                            entry.setRevision(SVNRepository.INVALID_REVISION);
                        }
                        if (entry.isThisDir() && status == SVNWCDbStatus.OBSTRUCTED_ADD) {
                            entry.unschedule();
                        } else {
                            entry.scheduleForAddition();
                        }
                    }
                }
                
                SVNEntryInfo additionInfo = myWCDb.scanAddition(path, true, true, true, false, false, false, false, true, true);
                SVNWCDbStatus workStatus = additionInfo.getWCDBStatus();
                reposPath = additionInfo.getReposPath();
                entry.setUUID(additionInfo.getUUID());
                entry.setRepositoryRoot(additionInfo.getReposRootURL());
                long originalRevision = additionInfo.getOriginalRevision();
                
                if (!SVNRevision.isValidRevisionNumber(entry.getCommittedRevision()) && originalReposPath == null) {
                    //
                } else if (workStatus == SVNWCDbStatus.COPIED) {
                    entry.setCopied(true);
                    if (originalReposPath == null) {
                        entry.unschedule();
                    }
                    if (!SVNRevision.isValidRevisionNumber(entry.getRevision())) {
                        entry.setRevision(originalRevision);
                    }
                }
                
                if (originalReposPath != null) {
                    SVNErrorManager.assertionFailure(workStatus == SVNWCDbStatus.COPIED, null, SVNLogType.WC);
                    boolean setCopyFrom = true;
                    File parentPath = path.getParentFile();
                    boolean wasError = false;
                    SVNEntryInfo parentAdditionInfo = null;
                    try {
                        parentAdditionInfo = myWCDb.scanAddition(parentPath, false, false, false, true, true, true, false, false, false);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage();
                        if (err.getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                            throw svne;
                        } 
                        wasError = true;
                    }
                    
                    if (!wasError) {
                        String parentRootURL = parentAdditionInfo.getOriginalRootURL();
                        String parentReposPath = parentAdditionInfo.getOriginalReposPath();
                        File operationRootPath = parentAdditionInfo.getOperationRootPath();
                        if (parentRootURL != null && parentRootURL.equals(originalRootURL)) {
                            String relPathToEntry = SVNPathUtil.getPathAsChild(operationRootPath.getAbsolutePath(), path.getAbsolutePath());
                            String entryReposPath = SVNPathUtil.append(parentReposPath, relPathToEntry);
                            if (originalReposPath.equals(entryReposPath)) {
                                setCopyFrom = false;
                                entry.setCopyFromRevision(SVNRepository.INVALID_REVISION);
                                entry.unschedule();
                                entry.setRevision(originalRevision);
                            }
                        }
                    }

                    if (setCopyFrom) {
                        entry.setCopyFromURL(SVNPathUtil.append(originalRootURL, SVNEncodingUtil.uriEncode(originalReposPath)));
                    }
                }
            } else if (status == SVNWCDbStatus.NOT_PRESENT) {
                entry.unschedule();
                entry.setDeleted(true);
            } else if (status == SVNWCDbStatus.OBSTRUCTED) {
                entry.setRevision(SVNRepository.INVALID_REVISION);
            } else if (status == SVNWCDbStatus.ABSENT) {
                entry.setAbsent(true);
            } else if (status == SVNWCDbStatus.EXCLUDED) {
                entry.unschedule();
                entry.setDepth(SVNDepth.EXCLUDE);
            } else {
                //TODO: may change later
                SVNErrorManager.assertionFailure(status == SVNWCDbStatus.EXCLUDED, null, SVNLogType.WC);
                continue;
            }
            
            if (entry.isScheduledForDeletion()) {
                SVNEntryInfo baseDeletionInfo = getBaseInfoForDeleted(path, entry, parentEntry);
                kind = baseDeletionInfo.getWCDBKind();
                reposPath = baseDeletionInfo.getReposPath();
                checksum = baseDeletionInfo.getChecksum();
            }
            
            if (entry.getDepth() == SVNDepth.UNKNOWN) {
                entry.setDepth(SVNDepth.INFINITY);
            }
            
            entry.setKind(SVNWCDbKind.convertWCDbKind(kind));
            
            SVNErrorManager.assertionFailure(reposPath != null || entry.isScheduledForDeletion() || status == SVNWCDbStatus.OBSTRUCTED || 
                    status == SVNWCDbStatus.OBSTRUCTED_DELETE, null, SVNLogType.WC);
            
            if (reposPath != null) {
                entry.setURL(SVNPathUtil.append(entry.getURL(), SVNEncodingUtil.uriEncode(reposPath)));
            }
            
            if (checksum != null) {
                entry.setChecksum(checksum.toString());
            }
            
            if (conflicted) {
                Collection childConflicts = myWCDb.readConflicts(path);
                for (Iterator childConflictsIter = childConflicts.iterator(); childConflictsIter.hasNext();) {
                    SVNConflictDescription conflict = (SVNConflictDescription) childConflictsIter.next();
                    if (conflict instanceof SVNTextConflictDescription) {
                        entry.setConflictOld(conflict.getMergeFiles().getBasePath());
                        entry.setConflictNew(conflict.getMergeFiles().getRepositoryPath());
                        entry.setConflictWorking(conflict.getMergeFiles().getLocalPath());
                        break;
                    } else if (conflict instanceof SVNPropertyConflictDescription) {
                        entry.setPropRejectFile(conflict.getMergeFiles().getRepositoryPath());
                    }
                }
            }
            
            if (lock != null) {
                entry.setLockToken(lock.getToken());
                entry.setLockOwner(lock.getOwner());
                entry.setLockComment(lock.getComment());
                entry.setLockCreationDate(SVNDate.formatDate(lock.getDate()));
            }
            
            if (entry.isFile()) {
                myWCDb.checkFileExternal(entry, sdb);
            }
            
            entry.setWorkingSize(translatedSize);
        }
        return entries;
    }
    
    
    protected SVNEntryInfo getBaseInfoForDeleted(File path, SVNEntry entry, SVNEntry parentEntry) throws SVNException {
        SVNEntryInfo info = null;
        SVNWCDbKind kind = null;
        SVNChecksum checksum = null;
        String reposPath = null;
        boolean wasError = false;
        try {
            info = myWCDb.getBaseInfo(path, false);
            kind = info.getWCDBKind();
            entry.setRevision(info.getRevision());
            entry.setCommittedRevision(info.getCommittedRevision());
            entry.setCommittedDate(SVNDate.formatDate(info.getCommittedDate()));
            entry.setAuthor(info.getCommittedAuthor());
            entry.setTextTime(SVNDate.formatDate(info.getLastTextTime()));
            entry.setDepth(info.getDepth());
            entry.setWorkingSize(info.getWorkingSize());
            checksum = info.getChecksum();
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage();
            if (err.getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw svne;
            }
            
            SVNEntryInfo deletionInfo = myWCDb.scanDeletion(path, false, false, false, true);
            File deletedWorkingPath = deletionInfo.getDeletedWorkingPath();
            SVNErrorManager.assertionFailure(deletedWorkingPath != null, null, SVNLogType.WC);
            File parentPath = deletedWorkingPath.getParentFile();
            SVNEntryInfo additionInfo = myWCDb.scanAddition(parentPath, true, true, true, false, false, false, false, false, false);
            entry.setRepositoryRoot(additionInfo.getReposRootURL());
            entry.setUUID(additionInfo.getUUID());
            String parentReposPath = additionInfo.getReposPath();
            reposPath = SVNPathUtil.append(parentReposPath, SVNPathUtil.getPathAsChild(parentPath.getAbsolutePath(), path.getAbsolutePath()));
            wasError = true;
        }
        
        if (!wasError) {
            SVNRepositoryScanResult result = myWCDb.scanBaseRepos(path);
            entry.setRepositoryRoot(result.getReposInfo().getRootURL());
            entry.setUUID(result.getReposInfo().getUUID());
            reposPath = result.getReposPath();
        }
        
        if (parentEntry != null) {
            if (!SVNRevision.isValidRevisionNumber(entry.getRevision())) {
                entry.setRevision(parentEntry.getRevision());
            }
        }
        
        if (parentEntry != null && parentEntry.isScheduledForDeletion()) {
            entry.setCopied(parentEntry.isCopied());
        } else {
            SVNEntryInfo deletionInfo = myWCDb.scanDeletion(path, false, true, false, true);
            boolean baseIsReplaced = deletionInfo.isBaseReplaced();
            File deletedWorkingPath = deletionInfo.getDeletedWorkingPath();
            if (deletedWorkingPath != null) {
                File parentPath = deletedWorkingPath.getParentFile();
                SVNEntryInfo additionInfo = myWCDb.scanAddition(parentPath, false, false, false, false, false, false, false, false, true);
                SVNWCDbStatus parentStatus = additionInfo.getWCDBStatus();
                if (parentStatus == SVNWCDbStatus.COPIED || parentStatus == SVNWCDbStatus.MOVED_HERE) {
                    if (SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                        entry.setCopied(baseIsReplaced);
                    } else {
                        entry.setCopied(true);
                    }
                } else {
                    SVNErrorManager.assertionFailure(parentStatus == SVNWCDbStatus.ADDED, null, SVNLogType.WC);
                }
            }
        }
        SVNEntryInfo result = new SVNEntryInfo();
        result.setWCDBKind(kind);
        result.setReposPath(reposPath);
        result.setChecksum(checksum);
        return result;
    }

    protected SVNVersionedProperties formatBaseProperties(SVNProperties srcProperties) {
        return null;
    }

    protected SVNVersionedProperties formatProperties(SVNEntry entry, SVNProperties srcProperties) {
        return null;
    }

    public SVNVersionedProperties getBaseProperties(String name) throws SVNException {
        return null;
    }

    public int getFormatVersion() {
        return 0;
    }

    public SVNVersionedProperties getProperties(String name) throws SVNException {
        return null;
    }

    public SVNVersionedProperties getRevertProperties(String name) throws SVNException {
        return null;
    }

    public String getThisDirName() {
        return "";
    }

    public SVNTreeConflictDescription getTreeConflict(String name) throws SVNException {
        return null;
    }

    public SVNVersionedProperties getWCProperties(String name) throws SVNException {
        return null;
    }

    public void handleKillMe() throws SVNException {
    }

    public boolean hasPropModifications(String entryName) throws SVNException {
        return false;
    }

    public boolean hasProperties(String entryName) throws SVNException {
        return false;
    }

    public boolean hasTreeConflict(String name) throws SVNException {
        return false;
    }

    public void installProperties(String name, SVNProperties baseProps, SVNProperties workingProps, SVNLog log, boolean writeBaseProps, boolean close) throws SVNException {
    }

    protected boolean isEntryPropertyApplicable(String name) {
        return false;
    }

    public boolean isLocked() throws SVNException {
        return false;
    }

    public boolean isVersioned() {
        return false;
    }

    public boolean lock(boolean stealLock) throws SVNException {
        return false;
    }

    public void postCommit(String fileName, long revisionNumber, boolean implicit, SVNErrorCode errorCode) throws SVNException {
    }

    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        return false;
    }

    public void saveEntries(boolean close) throws SVNException {
    }

    public void saveVersionedProperties(SVNLog log, boolean close) throws SVNException {
    }

    public void saveWCProperties(boolean close) throws SVNException {
    }

    public void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException {
    }

    public boolean unlock() throws SVNException {
        return false;
    }

    protected void writeEntries(Writer writer) throws IOException, SVNException {
    }

    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        return 0;
    }

}
