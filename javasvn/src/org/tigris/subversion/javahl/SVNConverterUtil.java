/*
 * Created on 17.06.2005
 */
package org.tigris.subversion.javahl;

import java.util.HashMap;
import java.util.Map;

import org.tigris.subversion.javahl.Notify.Action;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author evgeny
 */
public class SVNConverterUtil {
    
    private static final Map STATUS_CONVERSION_MAP = new HashMap();
    private static final Map REVISION_KIND_CONVERSION_MAP = new HashMap();
    private static final Map ACTION_CONVERSION_MAP = new HashMap();
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

        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.base), SVNRevision.BASE);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.committed), SVNRevision.COMMITTED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.head), SVNRevision.HEAD);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.previous), SVNRevision.PREVIOUS);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.unspecified), SVNRevision.UNDEFINED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.working), SVNRevision.WORKING);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.number), SVNRevision.UNDEFINED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.date), SVNRevision.UNDEFINED);
        
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
        String url = "";
        int nodeKind = getNodeKind(status.getKind());
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
            nodeKind = NodeKind.unknown;
        }
        long revision = status.getRevision().getNumber();
        long lastChangedRevision = status.getCommittedRevision().getNumber();
        long lastChangedDate = status.getCommittedDate().getTime();
        String lastCommitAuthor = status.getAuthor();
        int textStatus = getStatusValue(status.getContentsStatus());
        int propStatus = getStatusValue(status.getPropertiesStatus());
        int repositoryTextStatus = getStatusValue(status.getRemoteContentsStatus());
        int repositoryPropStatus = getStatusValue(status.getRemotePropertiesStatus());
        boolean locked = status.isLocked();
        boolean copied = status.isCopied();
        boolean switched = status.isSwitched();
        
        String conflictOld = status.getConflictOldFile().getAbsolutePath();
        String conflictNew = status.getConflictNewFile().getAbsolutePath();
        String conflictWorking = status.getConflictWrkFile().getAbsolutePath();
        String urlCopiedFrom = status.getCopyFromURL();
        long revisionCopiedFrom = status.getCopyFromRevision().getNumber();
        String lockToken = status.getLocalLock().getID();
        String lockOwner = status.getLocalLock().getOwner();
        String lockComment = status.getLocalLock().getComment();
        long lockCreationDate = status.getLocalLock().getCreationDate().getTime();
        Lock reposLock = new Lock(lockOwner, status.getLocalLock().getPath(), lockToken, lockComment, lockCreationDate, status.getLocalLock().getExpirationDate().getTime());
        
        Status st = new Status(path, url, nodeKind, revision, lastChangedRevision, lastChangedDate, lastCommitAuthor, textStatus, propStatus,
                repositoryTextStatus, repositoryPropStatus, locked, copied, conflictOld, conflictNew, conflictWorking, urlCopiedFrom, revisionCopiedFrom,
                switched, lockToken, lockOwner, lockComment, lockCreationDate, reposLock);
        DebugLog.log(path + ": created status: " + st.getTextStatus() + ":" + st.getPropStatus() + ":" + st.getNodeKind());
        return st;
    }
    
    public static SVNRevision getSVNRevision(Revision r){
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
    
    public static int getNotifyActionValue(SVNEventAction action){
        Object status = ACTION_CONVERSION_MAP.get(action);
        if(status == null){
            return -1;
        }
        return ((Integer)status).intValue();
    }
}
