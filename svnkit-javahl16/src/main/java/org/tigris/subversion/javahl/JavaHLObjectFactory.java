/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class JavaHLObjectFactory {

    private static final Map STATUS_CONVERSION_MAP = new SVNHashMap();
    private static final Map REVISION_KIND_CONVERSION_MAP = new SVNHashMap();
    private static final Map ACTION_CONVERSION_MAP = new SVNHashMap();
    private static final Map LOCK_CONVERSION_MAP = new SVNHashMap();
    private static final Map CONFLICT_REASON_CONVERSATION_MAP = new SVNHashMap();
    
    private static final Comparator CHANGE_PATH_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            ChangePath cp1 = (ChangePath) o1;
            ChangePath cp2 = (ChangePath) o2;
            return SVNPathUtil.PATH_COMPARATOR.compare(cp1.getPath(), cp2.getPath());
        }
    };

    static{
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_ADDED, new Integer(StatusKind.added));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_CONFLICTED, new Integer(StatusKind.conflicted));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_DELETED, new Integer(StatusKind.deleted));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_EXTERNAL, new Integer(StatusKind.external));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_IGNORED, new Integer(StatusKind.ignored));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_INCOMPLETE, new Integer(StatusKind.incomplete));
        //STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MERGED, new Integer(StatusKind.merged));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MISSING, new Integer(StatusKind.missing));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MODIFIED, new Integer(StatusKind.modified));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NONE, new Integer(StatusKind.none));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NORMAL, new Integer(StatusKind.normal));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_OBSTRUCTED, new Integer(StatusKind.obstructed));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_REPLACED, new Integer(StatusKind.replaced));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_UNVERSIONED, new Integer(StatusKind.unversioned));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NAME_CONFLICT, new Integer(StatusKind.unversioned));

        STATUS_CONVERSION_MAP.put(SVNStatusType.CHANGED, new Integer(NotifyStatus.changed));
        STATUS_CONVERSION_MAP.put(SVNStatusType.CONFLICTED, new Integer(NotifyStatus.conflicted));
        STATUS_CONVERSION_MAP.put(SVNStatusType.INAPPLICABLE, new Integer(NotifyStatus.inapplicable));
        STATUS_CONVERSION_MAP.put(SVNStatusType.MERGED, new Integer(NotifyStatus.merged));
        STATUS_CONVERSION_MAP.put(SVNStatusType.MISSING, new Integer(NotifyStatus.missing));
        STATUS_CONVERSION_MAP.put(SVNStatusType.OBSTRUCTED, new Integer(NotifyStatus.obstructed));
        STATUS_CONVERSION_MAP.put(SVNStatusType.UNCHANGED, new Integer(NotifyStatus.unchanged));
        STATUS_CONVERSION_MAP.put(SVNStatusType.UNKNOWN, new Integer(NotifyStatus.unknown));

        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_INAPPLICABLE, new Integer(LockStatus.inapplicable));
        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_LOCKED, new Integer(LockStatus.locked));
        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_UNCHANGED, new Integer(LockStatus.unchanged));
        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_UNKNOWN, new Integer(LockStatus.unknown));
        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_UNLOCKED, new Integer(LockStatus.unlocked));

        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.base), SVNRevision.BASE);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.committed), SVNRevision.COMMITTED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.head), SVNRevision.HEAD);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.previous), SVNRevision.PREVIOUS);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.unspecified), SVNRevision.UNDEFINED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.working), SVNRevision.WORKING);

        ACTION_CONVERSION_MAP.put(SVNEventAction.ADD, new Integer(NotifyAction.add));
        ACTION_CONVERSION_MAP.put(SVNEventAction.ANNOTATE, new Integer(NotifyAction.blame_revision));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_ADDED, new Integer(NotifyAction.commit_added));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_DELETED, new Integer(NotifyAction.commit_deleted));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_DELTA_SENT, new Integer(NotifyAction.commit_postfix_txdelta));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_MODIFIED, new Integer(NotifyAction.commit_modified));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_REPLACED, new Integer(NotifyAction.commit_replaced));
        ACTION_CONVERSION_MAP.put(SVNEventAction.COPY, new Integer(NotifyAction.copy));
        ACTION_CONVERSION_MAP.put(SVNEventAction.DELETE, new Integer(NotifyAction.delete));
        ACTION_CONVERSION_MAP.put(SVNEventAction.FAILED_REVERT, new Integer(NotifyAction.failed_revert));
        ACTION_CONVERSION_MAP.put(SVNEventAction.LOCK_FAILED, new Integer(NotifyAction.failed_lock));
        ACTION_CONVERSION_MAP.put(SVNEventAction.LOCKED, new Integer(NotifyAction.locked));
        //ACTION_CONVERSION_MAP.put(SVNEventAction.PROGRESS, new Integer(NotifyAction.));
        ACTION_CONVERSION_MAP.put(SVNEventAction.RESOLVED, new Integer(NotifyAction.resolved));
        ACTION_CONVERSION_MAP.put(SVNEventAction.RESTORE, new Integer(NotifyAction.restore));
        ACTION_CONVERSION_MAP.put(SVNEventAction.REVERT, new Integer(NotifyAction.revert));
        ACTION_CONVERSION_MAP.put(SVNEventAction.SKIP, new Integer(NotifyAction.skip));
        ACTION_CONVERSION_MAP.put(SVNEventAction.STATUS_COMPLETED, new Integer(NotifyAction.status_completed));
        ACTION_CONVERSION_MAP.put(SVNEventAction.STATUS_EXTERNAL, new Integer(NotifyAction.status_external));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UNLOCK_FAILED, new Integer(NotifyAction.failed_unlock));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UNLOCKED, new Integer(NotifyAction.unlocked));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_ADD, new Integer(NotifyAction.update_add));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_COMPLETED, new Integer(NotifyAction.update_completed));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_DELETE, new Integer(NotifyAction.update_delete));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_EXTERNAL, new Integer(NotifyAction.update_external));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_UPDATE, new Integer(NotifyAction.update_update));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_NONE, new Integer(NotifyAction.update_update));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_EXISTS, new Integer(NotifyAction.exists));
        ACTION_CONVERSION_MAP.put(SVNEventAction.UPDATE_REPLACE, new Integer(NotifyAction.update_replaced));
        ACTION_CONVERSION_MAP.put(SVNEventAction.CHANGELIST_SET, new Integer(NotifyAction.changelist_set));
        ACTION_CONVERSION_MAP.put(SVNEventAction.CHANGELIST_CLEAR, new Integer(NotifyAction.changelist_clear));
        ACTION_CONVERSION_MAP.put(SVNEventAction.MERGE_BEGIN, new Integer(NotifyAction.merge_begin));
        ACTION_CONVERSION_MAP.put(SVNEventAction.FOREIGN_MERGE_BEGIN, new Integer(NotifyAction.foreign_merge_begin));

        ACTION_CONVERSION_MAP.put(SVNEventAction.PROPERTY_ADD, new Integer(NotifyAction.property_added));
        ACTION_CONVERSION_MAP.put(SVNEventAction.PROPERTY_MODIFY, new Integer(NotifyAction.property_modified));
        ACTION_CONVERSION_MAP.put(SVNEventAction.PROPERTY_DELETE, new Integer(NotifyAction.property_deleted));
        ACTION_CONVERSION_MAP.put(SVNEventAction.PROPERTY_DELETE_NONEXISTENT, new Integer(NotifyAction.property_deleted_nonexistent));
        ACTION_CONVERSION_MAP.put(SVNEventAction.REVPROPER_SET, new Integer(NotifyAction.revprop_set));
        ACTION_CONVERSION_MAP.put(SVNEventAction.REVPROP_DELETE, new Integer(NotifyAction.revprop_deleted));
        ACTION_CONVERSION_MAP.put(SVNEventAction.MERGE_COMPLETE, new Integer(NotifyAction.merge_completed));
        ACTION_CONVERSION_MAP.put(SVNEventAction.TREE_CONFLICT, new Integer(NotifyAction.tree_conflict));

        // undocumented thing.
        ACTION_CONVERSION_MAP.put(SVNEventAction.COMMIT_COMPLETED, new Integer(-11));

        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.ADDED, new Integer(ConflictDescriptor.Reason.added));
        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.DELETED, new Integer(ConflictDescriptor.Reason.deleted));
        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.EDITED, new Integer(ConflictDescriptor.Reason.edited));
        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.MISSING, new Integer(ConflictDescriptor.Reason.missing));
        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.OBSTRUCTED, new Integer(ConflictDescriptor.Reason.obstructed));
        CONFLICT_REASON_CONVERSATION_MAP.put(SVNConflictReason.UNVERSIONED, new Integer(ConflictDescriptor.Reason.unversioned));
    }

    public static Collection getChangeListsCollection(String[] changelists) {
        if (changelists != null) {
            return Arrays.asList(changelists);
        }
        return null;
    }
    
    public static Status createStatus(String path, SVNStatus status) {
        if (status == null){
            return null;
        }
        String url = status.getURL() != null ? status.getURL().toString() : null;
        if (url == null && status.getEntryProperties() != null) {
            url = (String) status.getEntryProperties().get(SVNProperty.URL);
        }
        if (url == null && status.getRemoteURL() != null) {
            url = status.getRemoteURL().toString();
        }
        int nodeKind = getNodeKind(status.getKind());
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
            nodeKind = NodeKind.unknown;
        }
        long revision = status.getRevision().getNumber();
        long lastChangedRevision = -1;
        if(status.getCommittedRevision() != null){
            lastChangedRevision = status.getCommittedRevision().getNumber();
        }
        Date d = status.getCommittedDate();
        long lastChangedDate = -1;
        if(d != null){
            lastChangedDate = d.getTime() * 1000;
        }
        String lastCommitAuthor = status.getAuthor();
        int textStatus = getStatusValue(status.getContentsStatus());
        int propStatus = getStatusValue(status.getPropertiesStatus());
        int repositoryTextStatus = getStatusValue(status.getRemoteContentsStatus());
        int repositoryPropStatus = getStatusValue(status.getRemotePropertiesStatus());
        boolean locked = status.isLocked();
        boolean copied = status.isCopied();
        boolean switched = status.isSwitched();

        String conflictOld = null;
        if(status.getConflictOldFile()!=null){
            conflictOld = status.getConflictOldFile().getName();
        }
        String conflictNew = null;
        if(status.getConflictNewFile()!=null){
            conflictNew = status.getConflictNewFile().getName();
        }
        String conflictWorking = null;
        if(status.getConflictWrkFile()!=null){
            conflictWorking = status.getConflictWrkFile().getName();
        }
        String urlCopiedFrom = status.getCopyFromURL();
        long revisionCopiedFrom = status.getCopyFromRevision().getNumber();
        String lockToken = null;
        String lockOwner = null;
        String lockComment = null;
        long lockCreationDate = 0;
        if(status.getLocalLock() != null){
            lockToken = status.getLocalLock().getID();
            lockOwner = status.getLocalLock().getOwner();
            lockComment = status.getLocalLock().getComment();
            lockCreationDate = status.getLocalLock().getCreationDate().getTime() * 1000;
        }
        Lock reposLock = createLock(status.getRemoteLock());
        if (path != null) {
            path = path.replace(File.separatorChar, '/');
        }
        
        long reposRev = status.getRemoteRevision() != null ? status.getRemoteRevision().getNumber() : -1;
        long reposDate = status.getRemoteDate() != null ? status.getRemoteDate().getTime() * 1000 : -1;
        String reposAuthor = status.getRemoteAuthor();
        int reposKind = getNodeKind(status.getRemoteKind());
        if (status.getRemoteKind() == null) {
            reposKind = NodeKind.none;
        }
        
        SVNTreeConflictDescription tc = status.getTreeConflict();
        Status st = new Status(path, url, nodeKind, revision, lastChangedRevision, lastChangedDate, lastCommitAuthor, textStatus, propStatus,
                repositoryTextStatus, repositoryPropStatus, locked, copied, tc != null, createConflictDescription(tc), conflictOld, conflictNew, conflictWorking, urlCopiedFrom, revisionCopiedFrom,
                switched, false, lockToken, lockOwner, lockComment, lockCreationDate, reposLock,
                /* remote: rev, date, kind, author */
                reposRev, reposDate, reposKind, reposAuthor, status.getChangelistName());
        return st;
    }

    public static SVNRevision getSVNRevision(Revision r){
        if(r == null){
            return SVNRevision.UNDEFINED;
        } else if(r.getKind() == RevisionKind.number){
            return SVNRevision.create(((Revision.Number)r).getNumber());
        } else if(r.getKind() == RevisionKind.date){
            return SVNRevision.create(((Revision.DateSpec)r).getDate());
        }
        return (SVNRevision)REVISION_KIND_CONVERSION_MAP.get(new Integer(r.getKind()));
    }

    public static SVNDepth getSVNDepth(int depth) {
        switch (depth) {
            case Depth.empty:
                return SVNDepth.EMPTY;
            case Depth.exclude:
                return SVNDepth.EXCLUDE;
            case Depth.files:
                return SVNDepth.FILES;
            case Depth.immediates:
                return SVNDepth.IMMEDIATES;
            case Depth.infinity:
                return SVNDepth.INFINITY;
            default:
                return SVNDepth.UNKNOWN;
        }
    }

    public static ConflictDescriptor createConflictDescription(SVNConflictDescription conflictDescription) {
        if (conflictDescription == null){
            return null;
        }
        SVNMergeFileSet mergeFiles = conflictDescription.getMergeFiles();
        String basePath = null;
        String repositoryPath = null;
        try {
            basePath = mergeFiles.getBasePath();
            repositoryPath = mergeFiles.getRepositoryPath();
        } catch (SVNException e) {
            //
        }
        ConflictVersion left = null;
        ConflictVersion right = null;
        int op = 0;
        if (conflictDescription.isTreeConflict()) {
        	SVNTreeConflictDescription tc = (SVNTreeConflictDescription) conflictDescription;
        	left = createConflictVersion(tc.getSourceLeftVersion());
        	right = createConflictVersion(tc.getSourceRightVersion());
        	op = getConflictOperation(tc.getOperation());
        }

        return new ConflictDescriptor(mergeFiles.getWCPath(),
                getConflictKind(conflictDescription.isPropertyConflict()),
                getNodeKind(conflictDescription.getNodeKind()),
                conflictDescription.getPropertyName(),
                mergeFiles.isBinary(),
                mergeFiles.getMimeType(),
                getConflictAction(conflictDescription.getConflictAction()),
                getConflictReason(conflictDescription.getConflictReason()),
                op,
                basePath,
                repositoryPath,
                mergeFiles.getWCPath(),
                mergeFiles.getResultPath(),
                left, 
                right
                );
    }

    private static int getConflictOperation(SVNOperation operation) {
    	if (operation == SVNOperation.MERGE) {
    		return Operation.merge;
    	} else if (operation == SVNOperation.NONE) {
    		return Operation.none;
    	} else if (operation == SVNOperation.UPDATE) {
    		return Operation.update;
    	} else if (operation == SVNOperation.SWITCH) {
    		return Operation.switched;
    	}
		return Operation.none;
	}

	private static ConflictVersion createConflictVersion(SVNConflictVersion version) {
		if (version == null) {
			return null;
		}
		String url = version.getRepositoryRoot() != null ? version.getRepositoryRoot().toString() : null;
		return new ConflictVersion(url, version.getPegRevision(), version.getPath(), getNodeKind(version.getKind()));
	}

	public static SVNConflictResult getSVNConflictResult(ConflictResult conflictResult) {
        if (conflictResult == null){
            return null;
        }
        return new SVNConflictResult(getSVNConflictChoice(conflictResult.getChoice()),
                conflictResult.getMergedPath() != null ? new File(conflictResult.getMergedPath()).getAbsoluteFile() : null);
    }

    public static int getConflictAction(SVNConflictAction conflictAction){
        if (conflictAction == SVNConflictAction.ADD){
            return ConflictDescriptor.Action.add;
        } else if (conflictAction == SVNConflictAction.DELETE) {
            return ConflictDescriptor.Action.delete;
        } else if (conflictAction == SVNConflictAction.EDIT) {
            return ConflictDescriptor.Action.edit;            
        }
        return -1;
    }

    public static SVNConflictChoice getSVNConflictChoice(int conflictResult){
        switch (conflictResult) {
            case ConflictResult.chooseBase:
                return SVNConflictChoice.BASE;
            case ConflictResult.chooseMerged:
                return SVNConflictChoice.MERGED;
            case ConflictResult.chooseMineConflict:
                return SVNConflictChoice.MINE_CONFLICT;
            case ConflictResult.chooseMineFull:
                return SVNConflictChoice.MINE_FULL;
            case ConflictResult.chooseTheirsConflict:
                return SVNConflictChoice.THEIRS_CONFLICT;
            case ConflictResult.chooseTheirsFull:
                return SVNConflictChoice.THEIRS_FULL;
            case ConflictResult.postpone:
                return SVNConflictChoice.POSTPONE;
            default:
                return null;
        }
    }

    public static int getConflictReason(SVNConflictReason conflictReason){
        Object reason = CONFLICT_REASON_CONVERSATION_MAP.get(conflictReason);
        if (reason != null){
            return ((Integer) reason).intValue();
        }
        return -1;
    }

    public static int getConflictKind(boolean isPropertyConflict){
        return isPropertyConflict ? ConflictDescriptor.Kind.property : ConflictDescriptor.Kind.text;        
    }

    public static DiffSummary createDiffSummary(SVNDiffStatus status) {
        int diffStatus = -1;
        if (status.getModificationType() == SVNStatusType.STATUS_NORMAL || 
                status.getModificationType() == SVNStatusType.STATUS_NONE) {
            diffStatus = 0;
        } else if (status.getModificationType() == SVNStatusType.STATUS_ADDED) {
            diffStatus = 1;
        } else if (status.getModificationType() == SVNStatusType.STATUS_MODIFIED) {
            diffStatus = 2;
        } else if (status.getModificationType() == SVNStatusType.STATUS_DELETED) {
            diffStatus = 3;
        }
        return new DiffSummary(status.getPath(), diffStatus, status.isPropertiesModified(), getNodeKind(status.getKind()));
    }
    
    public static int getNodeKind(SVNNodeKind svnKind){
        if(svnKind == SVNNodeKind.DIR ){
            return NodeKind.dir;
        } else if(svnKind == SVNNodeKind.NONE ){
            return NodeKind.none;
        } else if(svnKind == SVNNodeKind.FILE ){
            return NodeKind.file;
        }
        return NodeKind.unknown;
    }

    public static int getStatusValue(SVNStatusType svnStatusType){
        Object status = STATUS_CONVERSION_MAP.get(svnStatusType);
        if(status == null){
            return -1;
        }
        return ((Integer)status).intValue();
    }

    public static int getLockStatusValue(SVNStatusType svnStatusType){
        Object status = LOCK_CONVERSION_MAP.get(svnStatusType);
        if(status == null){
            return -1;
        }
        return ((Integer)status).intValue();
    }

    public static int getNotifyActionValue(SVNEventAction action){
        Object status = ACTION_CONVERSION_MAP.get(action);
        if(status == null){
            return -1;
        }
        return ((Integer)status).intValue();
    }

    public static DirEntry createDirEntry(SVNDirEntry dirEntry) {
        if(dirEntry == null){
            return null;
        }

        SVNURL url = dirEntry.getURL();
        SVNURL repositoryRoot = dirEntry.getRepositoryRoot();
        String relativeToRepositoryRoot = SVNPathUtil.getRelativePath(repositoryRoot.getPath(), url.getPath());
        String relativeToTargetPath = dirEntry.getRelativePath();
        String targetToRootPath = relativeToRepositoryRoot.substring(0, relativeToRepositoryRoot.length() - relativeToTargetPath.length());
        return new DirEntry(
                relativeToTargetPath,
                SVNPathUtil.getAbsolutePath(targetToRootPath),
                getNodeKind(dirEntry.getKind()),
                dirEntry.getSize(),
                dirEntry.hasProperties(),
                dirEntry.getRevision(),
                dirEntry.getDate() != null ? dirEntry.getDate().getTime()*1000 : 0,
                dirEntry.getAuthor()
                );
    }

    public static LogMessage createLogMessage(SVNLogEntry logEntry) {
        if(logEntry == null){
            return null;
        }
        Map cpaths = logEntry.getChangedPaths();
        ChangePath[] cp = null;
        if(cpaths == null){
            cp = new ChangePath[]{};
        }else{
            Collection clientChangePaths = new ArrayList();
            for (Iterator iter = cpaths.keySet().iterator(); iter.hasNext();) {
                String path = (String) iter.next();
                SVNLogEntryPath entryPath = (SVNLogEntryPath)cpaths.get(path);
                if(entryPath != null){
                    clientChangePaths.add(new ChangePath(path, entryPath.getCopyRevision(), entryPath.getCopyPath(), entryPath.getType(), getNodeKind(entryPath.getKind())));
                }
            }
            cp = (ChangePath[]) clientChangePaths.toArray(new ChangePath[clientChangePaths.size()]);
            // sort by paths.
            Arrays.sort(cp, CHANGE_PATH_COMPARATOR);
        }
        long time = 0;
        if (logEntry.getDate() != null) {
            time = logEntry.getDate().getTime()*1000;
            if (logEntry.getDate() instanceof SVNDate) {
                time = ((SVNDate) logEntry.getDate()).getTimeInMicros();
            }
        }
        return new LogMessage(cp, logEntry.getRevision(), logEntry.getAuthor(), time, logEntry.getMessage());
    }

    public static Mergeinfo createMergeInfo(Map mergeInfo) {
        if (mergeInfo == null) {
            return null;
        }

        Mergeinfo result = new Mergeinfo();
        for (Iterator iterator = mergeInfo.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            SVNURL mergeSrcURL = (SVNURL) entry.getKey();
            String url = mergeSrcURL.toString();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) entry.getValue();
            SVNMergeRange[] ranges = rangeList.getRanges();
            for (int i = 0; i < ranges.length; i++) {
                SVNMergeRange range = ranges[i];
                result.addRevisionRange(url, createRevisionRange(range));
            }
        }
        return result;
    }

    public static RevisionRange createRevisionRange(SVNMergeRange range){
        if (range == null){
            return null;
        }
        return new RevisionRange(new Revision.Number(range.getStartRevision()), new Revision.Number(range.getEndRevision()));
    }

    public static RevisionRange[] createRevisionRanges(SVNMergeRangeList rangeList) {
        if (rangeList == null) {
            return null;
        }
        SVNMergeRange[] ranges = rangeList.getRanges();
        RevisionRange[] result = new RevisionRange[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            result[i] = createRevisionRange(ranges[i]);
        }
        return result;
    }

    public static SVNRevisionRange getSVNRevisionRange(RevisionRange revisionRange) {
        return new SVNRevisionRange(getSVNRevision(revisionRange.getFromRevision()), getSVNRevision(revisionRange.getToRevision()));
    }

    public static void handleLogMessage(SVNLogEntry logEntry, LogMessageCallback handler) {
        if(logEntry == null || handler == null) {
            return;
        }
        Map cpaths = logEntry.getChangedPaths();
        ChangePath[] cp = null;
        if (cpaths == null) {
            cp = new ChangePath[]{};
        } else {
            Collection clientChangePaths = new ArrayList();
            for (Iterator iter = cpaths.keySet().iterator(); iter.hasNext();) {
                String path = (String) iter.next();
                SVNLogEntryPath entryPath = (SVNLogEntryPath)cpaths.get(path);
                if(entryPath != null){
                    clientChangePaths.add(new ChangePath(path, entryPath.getCopyRevision(), entryPath.getCopyPath(), entryPath.getType(), getNodeKind(entryPath.getKind())));
                }
            }
            cp = (ChangePath[]) clientChangePaths.toArray(new ChangePath[clientChangePaths.size()]);
        }
        SVNProperties revisionProperties = logEntry.getRevisionProperties();
        Map revisionPropertiesMap = new HashMap();
        for(Iterator names = revisionProperties.nameSet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            revisionPropertiesMap.put(name, revisionProperties.getStringValue(name));
        }
        handler.singleMessage(cp, logEntry.getRevision(), revisionPropertiesMap, logEntry.hasChildren());
    }

    public static CommitItem[] getCommitItems(SVNCommitItem[] commitables, boolean isImport, boolean isURLsOnly) {
        if (commitables == null) {
            return null;
        }
        CommitItem[] items = new CommitItem[commitables.length];
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem sc = commitables[i];
            if(sc == null) {
                items[i] = null;
            } else {
                int stateFlag = 0;
                if (sc.isDeleted()) {
                    stateFlag += CommitItemStateFlags.Delete;
                }
                if (sc.isAdded()) {
                    stateFlag += CommitItemStateFlags.Add;
                } 
                if (sc.isContentsModified()) {
                    stateFlag += CommitItemStateFlags.TextMods;
                }
                if (sc.isPropertiesModified()) {
                    stateFlag += CommitItemStateFlags.PropMods;
                }
                if(sc.isCopied()){
                    stateFlag += CommitItemStateFlags.IsCopy;
                }
                String url = sc.getURL() != null ? sc.getURL().toString() : null;
                String path = isURLsOnly ? null : sc.getFile() != null ? sc.getFile().getAbsolutePath() : null;
                if (path != null) {
                    path = path.replace(File.separatorChar, '/');
                } 
                if (path != null && isImport) {
                    url = null;
                }
                int kind = isImport ? NodeKind.none : getNodeKind(sc.getKind());
                items[i] = new CommitItem(path, kind, stateFlag, url, 
                        sc.getCopyFromURL() != null ? sc.getCopyFromURL().toString() : null, sc.getRevision().getNumber());
            }
        }
        return items;
    }

    public static Lock createLock(SVNLock svnLock){
        if(svnLock == null){
            return null;
        }
        return new Lock(svnLock.getOwner(), svnLock.getPath(), svnLock.getID(),
                svnLock.getComment(), svnLock.getCreationDate() != null ? svnLock.getCreationDate().getTime() * 1000 : 0,
                svnLock.getExpirationDate() != null ? svnLock.getExpirationDate().getTime() * 1000 : 0);
    }

    public static Info createInfo(SVNInfo info) {
        if(info == null){
            return null;
        }
        int schedule = ScheduleKind.normal;
        if (SVNProperty.SCHEDULE_ADD.equals(info.getSchedule())) {
            schedule = ScheduleKind.add;
        } else if (SVNProperty.SCHEDULE_DELETE.equals(info.getSchedule())) {
            schedule = ScheduleKind.delete;
        }
        File file = info.getFile();

        boolean deleted = file != null && !file.exists() && schedule == ScheduleKind.delete;
        boolean absent = file != null && !deleted && !file.exists();
        boolean incomplete = false;

        long copyRev = info.getCopyFromRevision() != null ? info.getCopyFromRevision().getNumber(): - 1;
        String copyUrl = info.getCopyFromURL() != null ? info.getCopyFromURL().toString() : null;

        String path = info.getFile() != null ? info.getFile().getName() : SVNPathUtil.tail(info.getPath());
        if (path != null) {
            path = path.replace(File.separatorChar, '/');
        }
        return new Info(
                path,
                info.getURL() != null ? info.getURL().toString() : null,
                info.getRepositoryUUID(),
                info.getRepositoryRootURL() != null ? info.getRepositoryRootURL().toString() : null,
                schedule,
                getNodeKind(info.getKind()),
                info.getAuthor(),
                info.getRevision() != null ? info.getRevision().getNumber() : -1,
                info.getCommittedRevision() != null ? info.getCommittedRevision().getNumber() : - 1,
                info.getCommittedDate(),
                info.getTextTime(),
                info.getPropTime(),
                info.getCopyFromRevision() != null || info.getCopyFromURL()!=null,
                deleted, absent, incomplete, copyRev, copyUrl
                );
    }

    public static Info2 createInfo2(SVNInfo info) {
        if(info == null){
            return null;
        }
        int schedule = ScheduleKind.normal;
        if (SVNProperty.SCHEDULE_ADD.equals(info.getSchedule())) {
            schedule = ScheduleKind.add;
        } else if (SVNProperty.SCHEDULE_DELETE.equals(info.getSchedule())) {
            schedule = ScheduleKind.delete;
        }
        long copyRev = info.getCopyFromRevision() != null ? info.getCopyFromRevision().getNumber() : -1;
        String copyUrl = info.getCopyFromURL() != null ? info.getCopyFromURL().toString() : null;

        String path = info.getFile() != null ? info.getFile().getAbsolutePath() : info.getPath();
        if (path != null) {
            path = path.replace(File.separatorChar, '/');
        }
        int depth = info.getDepth() != null ? info.getDepth().getId() : Depth.unknown;
        if (info.getKind() == SVNNodeKind.FILE) {
        	depth = 0;
        }
        return new Info2(
                path,
                info.getURL() != null ? info.getURL().toString() : null,
                info.getRevision() != null ? info.getRevision().getNumber() : -1,
                getNodeKind(info.getKind()),
                info.getRepositoryRootURL() != null ? info.getRepositoryRootURL().toString() : null,
                info.getRepositoryUUID(),
                info.getCommittedRevision() != null ? info.getCommittedRevision().getNumber() : - 1,
                info.getCommittedDate() != null ? info.getCommittedDate().getTime() * 1000 : 0,
                info.getAuthor(),
                createLock(info.getLock()),
                !info.isRemote(),
                schedule, copyUrl, copyRev,
                info.getTextTime() != null  ? info.getTextTime().getTime() * 1000 : 0,
                info.getPropTime() != null ? info.getPropTime().getTime() * 1000 : 0,
                info.getChecksum(),
                info.getConflictOldFile() != null ? info.getConflictOldFile().getName() : null,
                info.getConflictNewFile() != null ? info.getConflictNewFile().getName() : null,
                info.getConflictWrkFile() != null ? info.getConflictWrkFile().getName() : null,
                info.getPropConflictFile() != null ? info.getPropConflictFile().getName() : null,
                info.getChangelistName(), info.getWorkingSize(), info.getRepositorySize(),
                depth,
                createConflictDescription(info.getTreeConflict())
                );
    }

    public static ProgressEvent createProgressEvent(long onProgress, long total) {
        return new ProgressEvent(onProgress, total);        
    }
    
    public static PropertyData createPropertyData(Object client, String path, String name, SVNPropertyValue value) {
        if (client instanceof SVNClientImpl){
            if (value.isString()) {
                return new JavaHLPropertyData((SVNClientImpl) client, null, path, name, value.getString(), SVNPropertyValue.getPropertyAsBytes(value));
            }
            return new JavaHLPropertyData((SVNClientImpl) client, null, path, name, SVNPropertyValue.getPropertyAsString(value), value.getBytes());
        }
        if (value.isString()) {
            return new PropertyData((SVNClient) client, path, name, value.getString(), SVNPropertyValue.getPropertyAsBytes(value));
        }
        return new PropertyData((SVNClient) client, path, name, SVNPropertyValue.getPropertyAsString(value), value.getBytes());
    }

    public static NotifyInformation createNotifyInformation(SVNEvent event, String path) {
        final int actionId = getNotifyActionValue(event.getAction());
        if (actionId == -1) {
            return null;
        }
        // include full error message.
        String errMsg = null;
        if (event.getErrorMessage() != null) {
            errMsg = event.getErrorMessage().getFullMessage();
        }
        // TODO 16
        return new NotifyInformation(
                path,
                actionId,
                getNodeKind(event.getNodeKind()),
                event.getMimeType(),
                createLock(event.getLock()),
                errMsg,
                getStatusValue(event.getContentsStatus()),
                getStatusValue(event.getPropertiesStatus()),
                getLockStatusValue(event.getLockStatus()),
                event.getRevision(),
                event.getChangelistName(),
                createRevisionRange(event.getMergeRange()), 
                ""
        );
    }
    
    public static CopySource createCopySource(SVNLocationEntry location) {
        return new CopySource(location.getPath(), Revision.getInstance(location.getRevision()), null);
    }

    public static Level getLoggingLevel(int level) {
        if (level == SVNClientLogLevel.NoLog) {
            return Level.OFF;
        } else if (level == SVNClientLogLevel.ErrorLog) {
            return Level.SEVERE;
        } else if (level == SVNClientLogLevel.ExceptionLog) {
            return Level.FINE;
        } else if (level == SVNClientLogLevel.EntryLog) {
            return Level.FINEST;
        }
        return Level.OFF;
    }

    public static void throwException(SVNException e, SVNClientImpl svnClient) throws ClientException {
        int code = 0;
        if (e.getErrorMessage() != null) {
            code = e.getErrorMessage().getErrorCode().getCode();
        }
        ClientException ec = new ClientException(e.getMessage(), null, code);
        ec.initCause(e);
        svnClient.getDebugLog().logFine(SVNLogType.DEFAULT, ec);
        svnClient.getDebugLog().logFine(SVNLogType.DEFAULT, e);
        throw ec;
    }

    public static final int infinityOrEmpty(boolean recurse) {
        return Depth.infinityOrEmpty(recurse);
    }

    public static final int infinityOrFiles(boolean recurse) {
        return Depth.infinityOrFiles(recurse);
    }

    public static final int infinityOrImmediates(boolean recurse) {
        return Depth.infinityOrImmediates(recurse);
    }

    public static final int unknownOrFiles(boolean recurse) {
        return Depth.unknownOrFiles(recurse);
    }

    public static final int unknownOrImmediates(boolean recurse) {
        return Depth.unknownOrImmediates(recurse);
    }
}
