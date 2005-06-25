/*
 * Created on 17.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author evgeny
 */
public class SVNConverterUtil {

    private static final Map STATUS_CONVERSION_MAP = new HashMap();
    private static final Map REVISION_KIND_CONVERSION_MAP = new HashMap();
    private static final Map ACTION_CONVERSION_MAP = new HashMap();
    private static final Map LOCK_CONVERSION_MAP = new HashMap();

    static{
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_ADDED, new Integer(StatusKind.added));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_CONFLICTED, new Integer(StatusKind.conflicted));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_DELETED, new Integer(StatusKind.deleted));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_EXTERNAL, new Integer(StatusKind.external));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_IGNORED, new Integer(StatusKind.ignored));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_INCOMPLETE, new Integer(StatusKind.incomplete));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MERGED, new Integer(StatusKind.merged));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MISSING, new Integer(StatusKind.missing));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_MODIFIED, new Integer(StatusKind.modified));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NONE, new Integer(StatusKind.none));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NORMAL, new Integer(StatusKind.normal));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_OBSTRUCTED, new Integer(StatusKind.obstructed));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_REPLACED, new Integer(StatusKind.replaced));
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_UNVERSIONED, new Integer(StatusKind.unversioned));

        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_INAPPLICABLE, new Integer(LockStatus.inapplicable));
        LOCK_CONVERSION_MAP.put(SVNStatusType.LOCK_LOCKED, new Integer(LockStatus.inapplicable));
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
    }

    public static Status createStatus(String path, SVNStatus status) {
        if(status == null){
            return null;
        }
        String url = status.getURL();
        int nodeKind = getNodeKind(status.getKind());
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
            nodeKind = NodeKind.unknown;
        }
        long revision = status.getRevision().getNumber();
        long lastChangedRevision = 0;
        if(status.getCommittedRevision() != null){
            lastChangedRevision = status.getCommittedRevision().getNumber();
        }
        Date d = status.getCommittedDate();
        long lastChangedDate = 0;
        if(d != null){
            lastChangedDate = d.getTime();
        }
        String lastCommitAuthor = status.getAuthor();
        int textStatus = getStatusValue(status.getContentsStatus());
        int propStatus = getStatusValue(status.getPropertiesStatus());
        int repositoryTextStatus = getStatusValue(status.getRemoteContentsStatus());
        int repositoryPropStatus = getStatusValue(status.getRemotePropertiesStatus());
        boolean locked = status.isLocked();
        boolean copied = status.isCopied();
        boolean switched = status.isSwitched();

        String conflictOld = "";
        if(status.getConflictOldFile()!=null){
            conflictOld = status.getConflictOldFile().getAbsolutePath();
        }
        String conflictNew = "";
        if(status.getConflictNewFile()!=null){
            conflictNew = status.getConflictNewFile().getAbsolutePath();
        }
        String conflictWorking = "";
        if(status.getConflictWrkFile()!=null){
            conflictWorking = status.getConflictWrkFile().getAbsolutePath();
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
            lockCreationDate = status.getLocalLock().getCreationDate().getTime();
        }
        Lock reposLock = createLock(status.getRemoteLock());

        Status st = new Status(path, url, nodeKind, revision, lastChangedRevision, lastChangedDate, lastCommitAuthor, textStatus, propStatus,
                repositoryTextStatus, repositoryPropStatus, locked, copied, conflictOld, conflictNew, conflictWorking, urlCopiedFrom, revisionCopiedFrom,
                switched, lockToken, lockOwner, lockComment, lockCreationDate, reposLock);
        DebugLog.log(path + ": created status: " + st.getTextStatus() + ":" + st.getPropStatus() + ":" + st.getNodeKind());
        return st;
    }

    public static SVNRevision getSVNRevision(Revision r){
        if(r == null){
            return SVNRevision.UNDEFINED;
        }
        if(r.getKind() == RevisionKind.number){
            return SVNRevision.create(((Revision.Number)r).getNumber());
        }
        if(r.getKind() == RevisionKind.date){
            return SVNRevision.create(((Revision.DateSpec)r).getDate());
        }
        return (SVNRevision)REVISION_KIND_CONVERSION_MAP.get(new Integer(r.getKind()));
    }

    public static int getNodeKind(SVNNodeKind svnKind){
        if(svnKind == SVNNodeKind.DIR ){
            return NodeKind.dir;
        }else if(svnKind == SVNNodeKind.DIR ){
            return NodeKind.dir;
        }else if(svnKind == SVNNodeKind.FILE ){
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
        return new DirEntry(
                dirEntry.getName(),
                getNodeKind(dirEntry.getKind()),
                dirEntry.size(),
                dirEntry.hasProperties(),
                dirEntry.getRevision(),
                dirEntry.getDate() != null ? dirEntry.getDate().getTime() : 0,
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
                    clientChangePaths.add(new ChangePath(path, entryPath.getCopyRevision(), entryPath.getCopyPath(), entryPath.getType()));
                }
            }
            cp = (ChangePath[]) clientChangePaths.toArray(new ChangePath[clientChangePaths.size()]);
        }
        return new LogMessage(logEntry.getMessage(), logEntry.getDate(),
                logEntry.getRevision(), logEntry.getAuthor(), cp);
    }

    public static CommitItem[] getCommitItems(SVNCommitItem[] commitables) {
        if(commitables == null){
            return null;
        }
        CommitItem[] items = new CommitItem[commitables.length];
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem sc = commitables[i];
            if(sc == null){
                items[i] = null;
            }else{
                int stateFlag = 0;
                if (sc.isDeleted()) {
                    stateFlag += CommitItemStateFlags.Delete;
                } else if (sc.isAdded()) {
                    stateFlag += CommitItemStateFlags.Add;
                } else if (sc.isContentsModified()) {
                    stateFlag += CommitItemStateFlags.TextMods;
                }
                if (sc.isPropertiesModified()) {
                    stateFlag += CommitItemStateFlags.PropMods;
                }
                if(sc.isCopied()){
                    stateFlag += CommitItemStateFlags.IsCopy;
                }
                items[i] = new CommitItem(sc.getPath(), getNodeKind(sc.getKind()), stateFlag, sc.getURL(), sc.getCopyFromURL(), sc.getRevision().getNumber());
            }
        }
        return items;
    }

    public static Lock createLock(SVNLock svnLock){
        if(svnLock == null){
            return null;
        }
        return new Lock(svnLock.getOwner(), svnLock.getPath(), svnLock.getID(),
                svnLock.getComment(), svnLock.getCreationDate() != null ? svnLock.getCreationDate().getTime() : 0,
                svnLock.getExpirationDate() != null ? svnLock.getExpirationDate().getTime() : 0);
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

        boolean deleted = !file.exists() && schedule == ScheduleKind.delete;
        boolean absent = !deleted && !file.exists();
        boolean incomplete = false;

        long copyRev = info.getCopyFromRevision().getNumber();
        String copyUrl = info.getCopyFromURL();

        return new Info(
                info.getFile() != null ? info.getFile().getName() : PathUtil.tail(info.getPath()),
                info.getURL(),
                info.getRepositoryUUID(),
                info.getRepositoryRootURL(),
                schedule,
                getNodeKind(info.getKind()),
                info.getAuthor(),
                info.getRevision().getNumber(),
                info.getCommittedRevision().getNumber(),
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
        long copyRev = info.getCopyFromRevision().getNumber();
        String copyUrl = info.getCopyFromURL();

        return new Info2(
                info.getFile() != null ? info.getFile().getAbsolutePath() : info.getPath(),
                info.getURL(),
                info.getRevision().getNumber(),
                getNodeKind(info.getKind()),
                info.getRepositoryRootURL(),
                info.getRepositoryUUID(),
                info.getCommittedRevision().getNumber(),
                info.getCommittedDate() != null ? info.getCommittedDate().getTime() : 0,
                info.getAuthor(),
                createLock(info.getLock()),
                !info.isRemote(),
                schedule, copyUrl, copyRev,
                info.getTextTime() != null  ? info.getTextTime().getTime() : 0,
                info.getPropTime() != null ? info.getPropTime().getTime() : 0,
                info.getChecksum(),
                info.getConflictOldFile() != null ? info.getConflictOldFile().getName() : null,
                info.getConflictNewFile() != null ? info.getConflictNewFile().getName() : null,
                info.getConflictWrkFile() != null ? info.getConflictWrkFile().getName() : null,
                info.getPropConflictFile() != null ? info.getPropConflictFile().getName() : null
                );
    }
}
