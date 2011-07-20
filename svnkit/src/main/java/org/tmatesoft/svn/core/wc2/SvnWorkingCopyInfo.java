package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;

public class SvnWorkingCopyInfo {
    
    private File path;
    
    private SvnSchedule schedule;
    private SVNURL copyFromUrl;
    private long copyFromRevision;
    private SvnChecksum checksum;
    private String changelist;    
    private SVNDepth depth;
    
    private long recordedSize;
    private long recordedTime;
    
    private Collection<SVNConflictDescription> conflicts;
    
    private File wcRoot;
    
    public File getPath() {
        return path;
    }

    public SvnSchedule getSchedule() {
        return schedule;
    }

    public SVNURL getCopyFromUrl() {
        return copyFromUrl;
    }

    public SvnChecksum getChecksum() {
        return checksum;
    }

    public String getChangelist() {
        return changelist;
    }

    public SVNDepth getDepth() {
        return depth;
    }

    public long getRecordedSize() {
        return recordedSize;
    }

    public long getRecordedTime() {
        return recordedTime;
    }

    public Collection<SVNConflictDescription> getConflicts() {
        return conflicts;
    }

    public File getWcRoot() {
        return wcRoot;
    }

    public void setPath(File path) {
        this.path = path;
    }

    public void setSchedule(SvnSchedule schedule) {
        this.schedule = schedule;
    }

    public void setCopyFromUrl(SVNURL copyFromURL) {
        this.copyFromUrl = copyFromURL;
    }

    public void setChecksum(SvnChecksum checksum) {
        this.checksum = checksum;
    }

    public void setChangelist(String changelist) {
        this.changelist = changelist;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public void setRecordedSize(long recordedSize) {
        this.recordedSize = recordedSize;
    }

    public void setRecordedTime(long recordedTime) {
        this.recordedTime = recordedTime;
    }

    public void setConflicts(Collection<SVNConflictDescription> conflicts) {
        this.conflicts = conflicts;
    }

    public void setWcRoot(File wcRoot) {
        this.wcRoot = wcRoot;
    }

    public long getCopyFromRevision() {
        return copyFromRevision;
    }

    public void setCopyFromRevision(long copyFromRevision) {
        this.copyFromRevision = copyFromRevision;
    }

}
