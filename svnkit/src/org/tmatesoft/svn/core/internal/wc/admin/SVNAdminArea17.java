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

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.internal.wc.db.SVNActualNode;
import org.tmatesoft.svn.core.internal.wc.db.SVNBaseNode;
import org.tmatesoft.svn.core.internal.wc.db.SVNDbCommand;
import org.tmatesoft.svn.core.internal.wc.db.SVNDbTableField;
import org.tmatesoft.svn.core.internal.wc.db.SVNDbTables;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNRepositoryScanResult;
import org.tmatesoft.svn.core.internal.wc.db.SVNSqlJetUtil;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingNode;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17.IsDirDeletedResult;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
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
    public static final int WC_FORMAT = SVNAdminArea17Factory.WC_FORMAT;

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
        
        SqlJetDb sdb = null;
        SVNEntry parentEntry = null;
        Map<String, SVNEntry> entries = new HashMap<String, SVNEntry>();

        try {
            sdb = myWCDb.getDBTemp(path, false);
            List childNames = myWCDb.gatherChildren(path, false);
            childNames.add(getThisDirName());
            for (ListIterator iterator = childNames.listIterator(childNames.size()); iterator.hasPrevious();) {
                String name = (String) iterator.previous();
                Map entryAttributes = new HashMap();
                SVNEntry entry = new SVNEntry(entryAttributes, this, name);
                entries.put(name, entry);
                if (entry.isThisDir()) {
                    parentEntry = entry;
                }
                File entryPath = getFile(name);
                SVNEntryInfo info = myWCDb.readInfo(entryPath, true, true, true, false, false, true, true, false, true, true, true, false);
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
                            SVNRepositoryScanResult reposScanResult = myWCDb.scanBaseRepos(entryPath);
                            SVNRepositoryInfo reposInfo = reposScanResult.getReposInfo();
                            entry.setRepositoryRoot(reposInfo.getRootURL());
                            entry.setUUID(reposInfo.getUUID());
                        }
                        entry.setIncomplete(status == SVNWCDbStatus.INCOMPLETE);
                    }
                } else if (status == SVNWCDbStatus.DELETED || status == SVNWCDbStatus.OBSTRUCTED_DELETE) {
                    entry.scheduleForDeletion();
                    if (entry.isThisDir()) {
                        entry.setKeepLocal(myWCDb.determineKeepLocal(entryPath));
                    }
                } else if (status == SVNWCDbStatus.ADDED || status == SVNWCDbStatus.OBSTRUCTED_ADD) {
                    if (!entry.isThisDir()) {
                        SVNErrorManager.assertionFailure(parentEntry != null, null, SVNLogType.WC);
                        SVNErrorManager.assertionFailure(!SVNRevision.isValidRevisionNumber(entry.getRevision()), null, SVNLogType.WC);
                        entry.setRevision(parentEntry.getRevision());
                    }
                    
                    if (info.isBaseShadowed()) {
                        SVNEntryInfo baseInfo = myWCDb.getBaseInfo(entryPath, false);
                        entry.setRevision(baseInfo.getRevision());
                        if (baseInfo.getWCDBStatus() == SVNWCDbStatus.NOT_PRESENT) {
                            entry.setDeleted(true);
                            entry.scheduleForAddition();
                        } else {
                            entry.scheduleForReplacement();
                        }
                    } else {
                        if (kind == SVNWCDbKind.DIR && !entry.isThisDir()) {
                            IsDirDeletedResult isDirDeletedInfo = myWCDb.isDirDeleted(entryPath);
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
                    
                    SVNEntryInfo additionInfo = myWCDb.scanAddition(entryPath, true, true, true, false, false, false, false, true, true);
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
                        File parentPath = entryPath.getParentFile();
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
                                String relPathToEntry = SVNPathUtil.getPathAsChild(operationRootPath.getAbsolutePath(), entryPath.getAbsolutePath());
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
                    SVNEntryInfo baseDeletionInfo = getBaseInfoForDeleted(entryPath, entry, parentEntry);
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
                    entry.setURL(SVNPathUtil.append(entry.getRepositoryRoot(), SVNEncodingUtil.uriEncode(reposPath)));
                }
                
                if (checksum != null) {
                    entry.setChecksum(checksum.toString());
                }
                
                if (conflicted) {
                    Collection childConflicts = myWCDb.readConflicts(entryPath);
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
        } finally {
            if (sdb != null) {
                try {
                    sdb.close();
                } catch (SqlJetException e) {
                    SVNSqlJetUtil.convertException(e);
                }
            }
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
        return myWCDb.readPristineProperties(getFile(name));
    }

    public int getFormatVersion() {
        return WC_FORMAT;
    }

    public SVNVersionedProperties getProperties(String name) throws SVNException {
        return myWCDb.readProperties(getFile(name));
    }

    public SVNVersionedProperties getRevertProperties(String name) throws SVNException {
        return myWCDb.getBaseProperties(getFile(name));
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
        File path = getFile(entryName);
        try {
            myWCDb.readInfo(path, false, true, false, false, false, false, false, false, false, false, false, false);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return false;
            }
            throw svne;
        }
        
        SVNProperties workingProperties = loadProperties(path, false, true);
        if (workingProperties == null) {
            return false;
        }
        
        boolean isReplaced = isReplaced(path);
        if (isReplaced) {
            return !workingProperties.isEmpty();
        }
        
        SVNProperties baseProperties = loadProperties(path, true, false);
        return !baseProperties.compareTo(workingProperties).isEmpty();
    }

    private boolean isReplaced(File path) throws SVNException {
        SVNEntryInfo info = myWCDb.readInfo(path, false, true, false, false, false, true, false, false, false, false, false, false);
        boolean isBaseShadowed = info.isBaseShadowed();
        SVNWCDbStatus status = info.getWCDBStatus();
        SVNWCDbStatus baseStatus = null;
        if (isBaseShadowed) {
            SVNEntryInfo baseInfo = myWCDb.getBaseInfo(path, false);
            baseStatus = baseInfo.getWCDBStatus();
        }
        
        return (status == SVNWCDbStatus.ADDED || status == SVNWCDbStatus.OBSTRUCTED_ADD) && isBaseShadowed && 
               baseStatus != SVNWCDbStatus.NOT_PRESENT;
    }
    
    private SVNProperties loadProperties(File path, boolean base, boolean working) throws SVNException {
        SVNWCDbKind kind = myWCDb.readKind(path, false);
        String relPath = null;
        if (base) {
            relPath = SVNAdminUtil.getPropBasePath(path.getName(), kind.toNodeKind(), false);
        } else if (working) {
            relPath = SVNAdminUtil.getPropPath(path.getName(), kind.toNodeKind(), false);
        } else {
            relPath = SVNAdminUtil.getPropRevertPath(path.getName(), kind.toNodeKind(), false);
        }
        
        File propFile = getFile(relPath);
        if (!propFile.exists()) {
            if (working) {
                return null;
            }
            return new SVNProperties();
        }
        return new SVNWCProperties(propFile, null).asMap();
    }
    
    
    public boolean hasProperties(String entryName) throws SVNException {
        return !getBaseProperties(entryName).isEmpty() || !getProperties(entryName).isEmpty();
    }

    public boolean hasTreeConflict(String name) throws SVNException {
        HasConflicts hasConflicts = hasConflicts(getFile(name));
        return hasConflicts.myHasTreeConflicts;
    }

    public boolean hasTextConflict(String name) throws SVNException {
        HasConflicts hasConflicts = hasConflicts(getFile(name));
        return hasConflicts.myHasTextConflicts;
    }
    
    public boolean hasPropConflict(String name) throws SVNException {
        HasConflicts hasConflicts = hasConflicts(getFile(name));
        return hasConflicts.myHasPropConflicts;
    }
    
    public void installProperties(String name, SVNProperties baseProps, SVNProperties workingProps, SVNLog log, boolean writeBaseProps, boolean close) throws SVNException {
    }

    protected boolean isEntryPropertyApplicable(String name) {
        return name != null;
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

    public void postCommit(String fileName, long revisionNumber, boolean implicit, boolean rerun, SVNErrorCode errorCode) throws SVNException {
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

    public void close() throws SVNException {
        if (myWCDb != null) {
            myWCDb.closeDB();
        }
    }
    
    public void writeEntry(final File path, final SVNEntry thisDir, final SVNEntry thisEntry) throws SVNException {
        
        SVNDbCommand command = new SVNDbCommand() {
            
            public Object execCommand() throws SqlJetException, SVNException {
                final SqlJetDb sdb = myWCDb.getDBTemp(path, false);
                File thisPath = new File(path, thisEntry.getName());
                long reposId = 0;
                String reposRootURL = null;
                
                if (thisDir.getUUID() != null) {
                    reposId = myWCDb.ensureRepos(path, thisDir.getRepositoryRoot(), thisDir.getUUID());
                    reposRootURL = thisDir.getRepositoryRoot();
                }
                
                long wcId = myWCDb.fetchWCId(sdb);
                SVNProperties davCache = null;
                try {
                    davCache = myWCDb.getBaseDAVCache(path);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage();
                    if (err.getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                        throw svne;
                    }
                }
                
                Object[] lookUpObjects = new Object[] { wcId, thisEntry.getName() };
                Map<SVNDbTableField, Object> baseResult = (Map<SVNDbTableField, Object>) myWCDb.runSelect(sdb, SVNDbTables.base_node, 
                        myWCDb.getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_BASE_NODE_FIELDS, null));
                Object baseProps = null;
                if (!baseResult.isEmpty()) {
                    baseProps = baseResult.get(SVNDbTableField.properties);
                }
                
                Map<SVNDbTableField, Object> workingResult = (Map<SVNDbTableField, Object>) myWCDb.runSelect(sdb, SVNDbTables.working_node, 
                        myWCDb.getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_WORKING_NODE_FIELDS, null));
                Object workingProps = null;
                if (!workingResult.isEmpty()) {
                    workingProps = workingResult.get(SVNDbTableField.properties);
                }
                
                Map<SVNDbTableField, Object> actualResult = (Map<SVNDbTableField, Object>) myWCDb.runSelect(sdb, SVNDbTables.actual_node, 
                        myWCDb.getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_ACTUAL_NODE_FIELDS, null));
                Object actualProps = null;
                if (!actualResult.isEmpty()) {
                    actualProps = actualResult.get(SVNDbTableField.properties);
                }
                
                myWCDb.runDelete(sdb, SVNDbTables.working_node, myWCDb.getCommonDbStrategy(lookUpObjects, null, null), null);
                myWCDb.runDelete(sdb, SVNDbTables.base_node, myWCDb.getCommonDbStrategy(lookUpObjects, null, null), null);
                myWCDb.runDelete(sdb, SVNDbTables.actual_node, myWCDb.getCommonDbStrategy(lookUpObjects, null, null), null);
                
                return null;
            }
        };
        
//        command.runDbCommand(sdb, null, SqlJetTransactionMode.WRITE, true);
    }
    
    private void writeEntry(SqlJetDb sdb, long wcId, long reposId, String reposRootURL, SVNEntry entry, 
            String localRelPath, File entryPath, SVNEntry thisDir, boolean alwaysCreateActual, boolean createLocks) throws SVNException {
        String parentRelPath = null;
        if (localRelPath != null && !"".equals(localRelPath)) {
            parentRelPath = SVNPathUtil.removeTail(localRelPath);
        }
        
        SVNWorkingNode workingNode = null;
        SVNBaseNode baseNode = null;
        SVNActualNode actualNode = null;
        if (entry.getSchedule() == null) {
            if (entry.isCopied()) {
                workingNode = SVNWorkingNode.maybeCreateWorkingNode(workingNode);
            } else {
                baseNode = SVNBaseNode.maybeCreateNewInstance(baseNode);
            }
        } else if (entry.isScheduledForAddition()) {
            workingNode = SVNWorkingNode.maybeCreateWorkingNode(workingNode);
        } else if (entry.isScheduledForDeletion()) {
            workingNode = SVNWorkingNode.maybeCreateWorkingNode(workingNode);
            if (!(entry.isCopied() || (thisDir.isCopied() && thisDir.isScheduledForAddition()))) {
               baseNode = SVNBaseNode.maybeCreateNewInstance(baseNode); 
            }
        } else if (entry.isScheduledForReplacement()) {
            workingNode = SVNWorkingNode.maybeCreateWorkingNode(workingNode);
            baseNode = SVNBaseNode.maybeCreateNewInstance(baseNode);
        }
        
        if (entry.isDeleted()) {
            baseNode = SVNBaseNode.maybeCreateNewInstance(baseNode);
        }
        
        if (entry.isCopied()) {
            workingNode = SVNWorkingNode.maybeCreateWorkingNode(workingNode);
            if (entry.getCopyFromURL() != null) {
                workingNode.setCopyFromReposId(reposId);
                String relativeURL = null;
                String copyFromURL = entry.getCopyFromURL();
                if (reposRootURL != null && copyFromURL != null && copyFromURL.startsWith(reposRootURL)) {
                    relativeURL = copyFromURL.substring(reposRootURL.length());
                    if (relativeURL.startsWith("/")) {
                        relativeURL = relativeURL.substring(1);
                    }
                }
                
                if (relativeURL == null) {
                    workingNode.setCopyFromReposPath("");
                } else {
                    workingNode.setCopyFromReposPath(SVNEncodingUtil.uriEncode(relativeURL));
                }
                workingNode.setCopyFromRevision(entry.getCopyFromRevision());
            } else {
                File parentPath = entryPath.getParentFile();
                try {
                    SVNEntryInfo info = myWCDb.scanAddition(parentPath, false, false, false, true, true, false, false, true, false);
                    File opRootPath = info.getOperationRootPath();
                    String originalReposPath = info.getOriginalReposPath();
                    long originalRevision = info.getOriginalRevision();
                    if (opRootPath != null && originalReposPath != null && SVNRevision.isValidRevisionNumber(originalRevision) && 
                            originalRevision != entry.getRevision()) {
                        String relPathToEntry = SVNPathUtil.getPathAsChild(opRootPath.getAbsolutePath(), entryPath.getAbsolutePath());
                        String newCopyFromPath = SVNPathUtil.append(originalReposPath, relPathToEntry);
                        workingNode.setCopyFromReposId(reposId);
                        workingNode.setCopyFromReposPath(newCopyFromPath);
                        workingNode.setCopyFromRevision(entry.getRevision());
                    }
                } catch (SVNException svne) {
                    //
                }
            }
        }
        
        if (entry.isKeepLocal()) {
            SVNErrorManager.assertionFailure(workingNode != null, null, SVNLogType.WC);
            SVNErrorManager.assertionFailure(entry.isScheduledForDeletion(), null, SVNLogType.WC);
            workingNode.setKeepLocal(true);
        }
        
        if (entry.isAbsent()) {
            SVNErrorManager.assertionFailure(workingNode == null, null, SVNLogType.WC);
            SVNErrorManager.assertionFailure(baseNode != null, null, SVNLogType.WC);
            baseNode.setStatus(SVNWCDbStatus.ABSENT);
        }
        
        if (entry.getConflictOld() != null) {
            actualNode = SVNActualNode.maybeCreateActualNode(actualNode);
            actualNode.setConflictOld(entry.getConflictOld());
            actualNode.setConflictNew(entry.getConflictNew());
            actualNode.setConflictWorking(entry.getConflictWorking());
        }
        
        if (entry.getPropRejectFile() != null) {
            actualNode = SVNActualNode.maybeCreateActualNode(actualNode);
            actualNode.setPropReject(entry.getPropRejectFile());
        }
        
        if (entry.getChangelistName() != null) {
            actualNode = SVNActualNode.maybeCreateActualNode(actualNode);
            actualNode.setChangeList(entry.getChangelistName());
        }
        
        if (entry.getTreeConflictData() != null) {
            actualNode = SVNActualNode.maybeCreateActualNode(actualNode);
            actualNode.setTreeConflictData(entry.getTreeConflictData());
        }
        
        if (entry.getExternalFilePath() != null) {
            baseNode = SVNBaseNode.maybeCreateNewInstance(baseNode);
        }
        
        if (baseNode != null) {
            baseNode.setWCId(wcId);
            baseNode.setLocalRelativePath(localRelPath);
            baseNode.setParentRelPath(parentRelPath);
            baseNode.setRevision(entry.getRevision());
            baseNode.setLastModifiedTime(SVNDate.parseDateString(entry.getTextTime()));
            baseNode.setTranslatedSize(entry.getWorkingSize());
            if (entry.getDepth() != SVNDepth.EXCLUDE) {
                baseNode.setDepth(entry.getDepth());
            } else {
                baseNode.setStatus(SVNWCDbStatus.EXCLUDED);
                baseNode.setDepth(SVNDepth.INFINITY);
            }
            
            if (entry.isDeleted()) {
                SVNErrorManager.assertionFailure(!entry.isIncomplete(), null, SVNLogType.WC);
                baseNode.setStatus(SVNWCDbStatus.NOT_PRESENT);
                baseNode.setNodeKind(entry.getKind());
            } else {
                baseNode.setNodeKind(entry.getKind());
                if (entry.isIncomplete()) {
                    SVNErrorManager.assertionFailure(baseNode.getStatus() == SVNWCDbStatus.NORMAL, null, SVNLogType.WC);
                    baseNode.setStatus(SVNWCDbStatus.INCOMPLETE);
                }
            }
            
            if (entry.isDirectory()) {
                baseNode.setChecksum(null);
            } else {
                baseNode.setChecksum(new SVNChecksum(SVNChecksumKind.MD5, entry.getChecksum()));
            }
            
            if (reposRootURL != null) {
                baseNode.setReposId(reposId);
                if (entry.getURL() != null) {
                    String relativeURL = null;
                    if (entry.getURL().startsWith(reposRootURL)) {
                        relativeURL = entry.getURL().substring(reposRootURL.length());
                        if (relativeURL.startsWith("/")) {
                            relativeURL = relativeURL.substring(1);
                        }
                    }
                    
                    if (relativeURL == null) {
                        baseNode.setReposPath("");
                    } else {
                        baseNode.setReposPath(SVNEncodingUtil.uriDecode(relativeURL));
                    }
                } else {
                    String basePath = null;
                    if (thisDir.getURL() != null && thisDir.getURL().startsWith(reposRootURL)) {
                        basePath = thisDir.getURL().substring(reposRootURL.length());
                        if (basePath.startsWith("/")) {
                            
                        }
                    }
                    
                    if (basePath == null) {
                        baseNode.setReposPath(entry.getName());
                    } else {
                        baseNode.setReposPath(SVNPathUtil.append(SVNEncodingUtil.uriDecode(basePath), entry.getName()));
                    }
                }
            }
            
            baseNode.setChangedRevision(entry.getCommittedRevision());
            baseNode.setChangedDate(SVNDate.parseDate(entry.getCommittedDate()));
            baseNode.setChangedAuthor(entry.getAuthor());
            insertBaseNode(baseNode, sdb);
            if (entry.getLockToken() != null && createLocks) {
                SVNWCDbLock lock = new SVNWCDbLock(entry.getLockToken(), entry.getLockOwner(), entry.getLockComment(), 
                        SVNDate.parseDate(entry.getLockCreationDate()));
                myWCDb.addLock(entryPath, lock);
            }
            
            if (entry.getExternalFilePath() != null) {
                String serializedExternal = SVNAdminUtil.serializeExternalFileData(entry.asMap());
                Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
                fieldsToValues.put(SVNDbTableField.file_external, serializedExternal);
                myWCDb.runUpdate(sdb, SVNDbTables.base_node, myWCDb.getCommonDbStrategy(new Object[] { 1, entry.getName() }, 
                        SVNSqlJetUtil.OUR_FILE_EXTERNAL_FIELD, null), fieldsToValues);
            }
        }
        
        if (workingNode != null) {
            workingNode.setWCId(wcId);
            workingNode.setLocalRelPath(localRelPath);
            workingNode.setParentRelPath(parentRelPath);
            workingNode.setChangedRevision(SVNRepository.INVALID_REVISION);
            workingNode.setLastModifiedTime(SVNDate.parseDate(entry.getTextTime()));
            workingNode.setTranslatedSize(entry.getWorkingSize());
            
            if (entry.getDepth() != SVNDepth.EXCLUDE) {
                workingNode.setDepth(entry.getDepth());
            } else {
                workingNode.setStatus(SVNWCDbStatus.EXCLUDED);
                workingNode.setDepth(SVNDepth.INFINITY);
            }
            
            if (entry.isDirectory()) {
                workingNode.setChecksum(null);
            } else {
                workingNode.setChecksum(new SVNChecksum(SVNChecksumKind.MD5, entry.getChecksum()));
            }
            
            if (entry.isScheduledForDeletion()) {
                if (entry.isIncomplete()) {
                    workingNode.setStatus(SVNWCDbStatus.INCOMPLETE);
                } else {
                    if (entry.isCopied() || (thisDir.isCopied() && thisDir.isScheduledForAddition())) {
                        workingNode.setStatus(SVNWCDbStatus.NOT_PRESENT);
                    } else {
                        workingNode.setStatus(SVNWCDbStatus.DELETED);
                    }
                }
                workingNode.setKind(entry.getKind());
            } else {
                workingNode.setKind(entry.getKind());
                if (entry.isIncomplete()) {
                    SVNErrorManager.assertionFailure(workingNode.getStatus() == SVNWCDbStatus.NORMAL, null, SVNLogType.WC);
                    workingNode.setStatus(SVNWCDbStatus.INCOMPLETE);
                }
            }
            
            workingNode.setChangedRevision(entry.getCommittedRevision());
            workingNode.setChangedAuthor(entry.getAuthor());
            workingNode.setChangedDate(SVNDate.parseDate(entry.getCommittedDate()));
            insertWorkingNode(sdb, workingNode);
        }
        
        if (actualNode != null || alwaysCreateActual) {
            actualNode = SVNActualNode.maybeCreateActualNode(actualNode);
            actualNode.setWCId(wcId);
            actualNode.setLocalRelPath(localRelPath);
            actualNode.setParentRelPath(parentRelPath);
            insertActualNode(sdb, actualNode);
        }
    }

    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        return 0;
    }
    
    private void insertBaseNode(SVNBaseNode baseNode, SqlJetDb sdb) throws SVNException {
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        fieldsToValues.put(SVNDbTableField.wc_id, baseNode.getWCId());
        fieldsToValues.put(SVNDbTableField.local_relpath, baseNode.getLocalRelativePath());
        if (baseNode.getReposId() > 0) {
            fieldsToValues.put(SVNDbTableField.repos_id, baseNode.getReposId());
            String reposPath = baseNode.getReposPath();
            if (reposPath != null && reposPath.startsWith("/")) {
                reposPath = reposPath.substring(1);
            }
            fieldsToValues.put(SVNDbTableField.repos_relpath, reposPath);
        }
        
        if (baseNode.getParentRelPath() != null) {
            fieldsToValues.put(SVNDbTableField.parent_relpath, baseNode.getParentRelPath());
        }
        
        if (baseNode.getStatus() == SVNWCDbStatus.NOT_PRESENT || baseNode.getStatus() == SVNWCDbStatus.NORMAL || 
                baseNode.getStatus() == SVNWCDbStatus.ABSENT || baseNode.getStatus() == SVNWCDbStatus.INCOMPLETE ||
                baseNode.getStatus() == SVNWCDbStatus.EXCLUDED) {
            fieldsToValues.put(SVNDbTableField.presence, baseNode.getStatus().toString());
        }
        
        fieldsToValues.put(SVNDbTableField.revnum, baseNode.getRevision());
        
        if (baseNode.getNodeKind() == SVNNodeKind.DIR && !getThisDirName().equals(baseNode.getLocalRelativePath())) {
            fieldsToValues.put(SVNDbTableField.kind, SVNWCDbKind.SUBDIR.toString());
        } else {
            fieldsToValues.put(SVNDbTableField.kind, baseNode.getNodeKind().toString());
        }
        
        if (baseNode.getChecksum() != null) {
            fieldsToValues.put(SVNDbTableField.checksum, baseNode.getChecksum().toString());
        }
        
        if (baseNode.getTranslatedSize() != -1) {
            fieldsToValues.put(SVNDbTableField.translated_size, baseNode.getTranslatedSize());
        }
        
        if (SVNRevision.isValidRevisionNumber(baseNode.getChangedRevision())) {
            fieldsToValues.put(SVNDbTableField.changed_rev, baseNode.getChangedRevision());
        }
        
        if (baseNode.getChangedDate() != null) {
            fieldsToValues.put(SVNDbTableField.changed_date, baseNode.getChangedDate().getTime());
        }
        
        if (baseNode.getChangedAuthor() != null) {
            fieldsToValues.put(SVNDbTableField.changed_author, baseNode.getChangedAuthor());
        }
        
        fieldsToValues.put(SVNDbTableField.depth, baseNode.getDepth().toString());
        fieldsToValues.put(SVNDbTableField.last_mod_time, baseNode.getLastModifiedTime().getTime());
        if (baseNode.getProperties() != null) {
            SVNSkel skel = SVNSkel.createPropList(baseNode.getProperties().asMap());
            byte[] propBytes = skel.unparse();
            fieldsToValues.put(SVNDbTableField.properties, propBytes);
        }
        myWCDb.runInsertByFieldNames(sdb, SVNDbTables.base_node, SqlJetConflictAction.REPLACE, 
                myWCDb.getCommonDbStrategy(null, null, null), fieldsToValues);
    }

    private void insertWorkingNode(SqlJetDb sdb, SVNWorkingNode workingNode) throws SVNException {
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        fieldsToValues.put(SVNDbTableField.wc_id, workingNode.getWCId());
        fieldsToValues.put(SVNDbTableField.local_relpath, workingNode.getLocalRelPath());
        fieldsToValues.put(SVNDbTableField.parent_relpath, workingNode.getParentRelPath());
        
        if (workingNode.getStatus() == SVNWCDbStatus.NORMAL || workingNode.getStatus() == SVNWCDbStatus.NOT_PRESENT || 
                workingNode.getStatus() == SVNWCDbStatus.BASE_DELETED || workingNode.getStatus() == SVNWCDbStatus.INCOMPLETE || 
                workingNode.getStatus() == SVNWCDbStatus.EXCLUDED) {
            fieldsToValues.put(SVNDbTableField.presence, workingNode.getStatus().toString());
        }
        
        if (workingNode.getKind() == SVNNodeKind.DIR && !getThisDirName().equals(workingNode.getLocalRelPath())) {
            fieldsToValues.put(SVNDbTableField.kind, SVNWCDbKind.SUBDIR.toString());
        } else {
            fieldsToValues.put(SVNDbTableField.kind, workingNode.getKind().toString());
        }
     
        if (workingNode.getCopyFromReposPath() != null) {
            fieldsToValues.put(SVNDbTableField.copyfrom_repos_id, workingNode.getCopyFromReposId());
            fieldsToValues.put(SVNDbTableField.copyfrom_repos_path, workingNode.getCopyFromReposPath());
            fieldsToValues.put(SVNDbTableField.copyfrom_revnum, workingNode.getCopyFromRevision());
        }
        
        if (workingNode.isMovedHere()) {
            fieldsToValues.put(SVNDbTableField.moved_here, 1);
        }
        
        if (workingNode.getMovedTo() != null) {
            fieldsToValues.put(SVNDbTableField.moved_to, workingNode.getMovedTo());
        }
        
        if (workingNode.getChecksum() != null) {
            fieldsToValues.put(SVNDbTableField.checksum, workingNode.getChecksum().toString());
        }
        
        if (workingNode.getTranslatedSize() != -1) {
            fieldsToValues.put(SVNDbTableField.translated_size, workingNode.getTranslatedSize());
        }
        
        if (SVNRevision.isValidRevisionNumber(workingNode.getChangedRevision())) {
            fieldsToValues.put(SVNDbTableField.changed_rev, workingNode.getChangedRevision());
        }
        
        if (workingNode.getChangedDate() != null) {
            fieldsToValues.put(SVNDbTableField.changed_date, workingNode.getChangedDate().getTime());
        }
        
        if (workingNode.getChangedAuthor() != null) {
            fieldsToValues.put(SVNDbTableField.changed_author, workingNode.getChangedAuthor());
        }
        
        fieldsToValues.put(SVNDbTableField.depth, workingNode.getDepth().toString());
        fieldsToValues.put(SVNDbTableField.last_mod_time, workingNode.getLastModifiedTime().getTime());
        if (workingNode.getProperties() != null) {
            SVNSkel skel = SVNSkel.createPropList(workingNode.getProperties().asMap());
            byte[] propBytes = skel.unparse();
            fieldsToValues.put(SVNDbTableField.properties, propBytes);
        }
        
        fieldsToValues.put(SVNDbTableField.keep_local, workingNode.isKeepLocal() ? 1 : 0);
        myWCDb.runInsertByFieldNames(sdb, SVNDbTables.working_node, SqlJetConflictAction.REPLACE, 
                myWCDb.getCommonDbStrategy(null, null, null), fieldsToValues);
    }
    
    private void insertActualNode(SqlJetDb sdb, SVNActualNode actualNode) throws SVNException {
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        fieldsToValues.put(SVNDbTableField.wc_id, actualNode.getWCId());
        fieldsToValues.put(SVNDbTableField.local_relpath, actualNode.getLocalRelPath());
        fieldsToValues.put(SVNDbTableField.parent_relpath, actualNode.getParentRelPath());
        
        if (actualNode.getProperties() != null) {
            SVNSkel skel = SVNSkel.createPropList(actualNode.getProperties().asMap());
            byte[] propBytes = skel.unparse();
            fieldsToValues.put(SVNDbTableField.properties, propBytes);
        }
        
        if (actualNode.getConflictOld() != null) {
            fieldsToValues.put(SVNDbTableField.conflict_old, actualNode.getConflictOld());
            fieldsToValues.put(SVNDbTableField.conflict_new, actualNode.getConflictNew());
            fieldsToValues.put(SVNDbTableField.conflict_working, actualNode.getConflictWorking());
        }
        
        if (actualNode.getPropReject() != null) {
            fieldsToValues.put(SVNDbTableField.prop_reject, actualNode.getPropReject());
        }
        
        if (actualNode.getChangeList() != null) {
            fieldsToValues.put(SVNDbTableField.changelist, actualNode.getChangeList());
        }
        
        if (actualNode.getTreeConflictData() != null) {
            fieldsToValues.put(SVNDbTableField.tree_conflict_data, actualNode.getTreeConflictData());
        }
        
        myWCDb.runInsertByFieldNames(sdb, SVNDbTables.actual_node, SqlJetConflictAction.REPLACE, 
                myWCDb.getCommonDbStrategy(null, null, null), fieldsToValues);
    }
    
    private HasConflicts hasConflicts(File path) throws SVNException {
        SVNEntryInfo info = myWCDb.readInfo(path, false, false, true, false, false, false, true, false, false, false, false, false);
        boolean textConflicted = false;
        boolean propConflicted = false;
        boolean treeConflicted = false;
     
        List<SVNConflictDescription> conflicts = myWCDb.readConflicts(path);
        for (SVNConflictDescription conflictDescription : conflicts) {
            if (conflictDescription.isTextConflict()) {
                SVNMergeFileSet files = conflictDescription.getMergeFiles();
                if (files.getBaseFile() != null) {
                    textConflicted = files.getBaseFile().isFile();
                    if (textConflicted) {
                        break;
                    }
                }
                if (files.getRepositoryFile() != null) {
                    textConflicted = files.getRepositoryFile().isFile();
                    if (textConflicted) {
                        break;
                    }
                }
                if (files.getLocalFile() != null) {
                    textConflicted = files.getRepositoryFile().isFile();
                }
            } else if (conflictDescription.isPropertyConflict()) {
                SVNMergeFileSet files = conflictDescription.getMergeFiles();
                if (files.getRepositoryFile() != null) {
                    propConflicted = files.getRepositoryFile().isFile();
                }
            } else if (conflictDescription.isTreeConflict()) {
                treeConflicted = true;
            }
        }
        
        HasConflicts hasConflicts = new HasConflicts();
        hasConflicts.myHasPropConflicts = propConflicted;
        hasConflicts.myHasTextConflicts = textConflicted;
        hasConflicts.myHasTreeConflicts = treeConflicted;
        return hasConflicts;
    }
    
    private static class HasConflicts {
        private boolean myHasTreeConflicts;
        private boolean myHasPropConflicts;
        private boolean myHasTextConflicts;
    }
}
