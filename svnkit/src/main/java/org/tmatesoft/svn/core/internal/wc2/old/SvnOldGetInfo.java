package org.tmatesoft.svn.core.internal.wc2.old;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc16.SVNWCClient16;
import org.tmatesoft.svn.core.internal.wc17.SVNWCConflictDescription17;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnSchedule;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnWorkingCopyInfo;

public class SvnOldGetInfo extends SvnLocalOperationRunner<SvnGetInfo> implements ISVNInfoHandler {
    
    @Override
    protected void run() throws SVNException {        
        SVNWCClient16 client = new SVNWCClient16(getOperation().getRepositoryPool(), getOperation().getOptions());        
        client.doInfo(getFirstTarget(), 
                getOperation().getPegRevision(), 
                getOperation().getRevision(), 
                getOperation().getDepth(), 
                getOperation().getApplicableChangelists(), 
                this);
    }

    public void handleInfo(SVNInfo info) throws SVNException {
        SvnInfo info2 = convert(info);
        getOperation().getReceiver().receive(SvnTarget.fromFile(info.getFile()), info2);
    }

    private SvnInfo convert(SVNInfo info) {
        SvnInfo result = new SvnInfo();
        result.setUserData(info);
        result.setKind(info.getKind());
        result.setLastChangedAuthor(info.getAuthor());
        result.setLastChangedDate(getSvnDate(info.getCommittedDate()));
        result.setLastChangedRevision(info.getCommittedRevision().getNumber());
        result.setLock(info.getLock());
        result.setRepositoryRootURL(info.getRepositoryRootURL());
        result.setRepositoryUUID(info.getRepositoryUUID());
        result.setRevision(info.getRevision().getNumber());
        result.setSize(-1);
        result.setUrl(info.getURL());
        
        SvnWorkingCopyInfo wcInfo = new SvnWorkingCopyInfo();
        
        result.setWcInfo(wcInfo);
        wcInfo.setChangelist(info.getChangelistName());
        SvnChecksum checksum = new SvnChecksum();
        checksum.setKind(SvnChecksum.Kind.md5);
        checksum.setDigest(info.getChecksum());
        wcInfo.setChecksum(checksum);
        
        if (info.getTreeConflict() != null || 
                info.getConflictWrkFile() != null || info.getConflictNewFile() != null || info.getConflictOldFile() != null || 
                info.getPropConflictFile() != null) {
            Collection<SVNConflictDescription> conflicts = new ArrayList<SVNConflictDescription>();
            if (info.getTreeConflict() != null) {
                conflicts.add(info.getTreeConflict());
            }
            if (info.getConflictWrkFile() != null || info.getConflictNewFile() != null || info.getConflictOldFile() != null) {
                SVNWCConflictDescription17 cd = SVNWCConflictDescription17.createText(info.getFile());
                cd.setTheirFile(info.getConflictNewFile());
                cd.setBaseFile(info.getConflictOldFile());
                cd.setMyFile(info.getConflictWrkFile());
                conflicts.add(cd.toConflictDescription());
            }
            if (info.getPropConflictFile() != null) {
                SVNWCConflictDescription17 cd = SVNWCConflictDescription17.createProp(info.getFile(), info.getKind(), null);
                cd.setTheirFile(info.getPropConflictFile());
                conflicts.add(cd.toConflictDescription());
            }
            wcInfo.setConflicts(conflicts);
        }
        
        wcInfo.setCopyFromRevision(info.getCommittedRevision().getNumber());
        wcInfo.setCopyFromUrl(info.getCopyFromURL());
        wcInfo.setDepth(info.getDepth());
        wcInfo.setPath(info.getFile());
        wcInfo.setRecordedSize(info.getWorkingSize());
        if (info.getTextTime() != null) {
            wcInfo.setRecordedTime(info.getTextTime().getTime());
        }
        if (info.getSchedule() != null) {
            wcInfo.setSchedule(SvnSchedule.valueOf(info.getSchedule()));
        } else {
            wcInfo.setSchedule(SvnSchedule.NORMAL);
        }
        
        File wcRoot = null;
        try {
            wcRoot = SVNWCUtil.getWorkingCopyRoot(info.getFile(), true);
        } catch (SVNException e) {
        }
        wcInfo.setWcRoot(wcRoot);
        
        return result;
    }

}
