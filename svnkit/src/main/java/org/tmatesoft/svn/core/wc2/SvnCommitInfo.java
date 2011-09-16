package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.internal.util.SVNDate;

public class SvnCommitInfo {
    
    private long revision;
    private SVNDate date;
    private String author;
    
    public long getRevision() {
        return revision;
    }
    public SVNDate getDate() {
        return date;
    }
    public String getAuthor() {
        return author;
    }
    public void setRevision(long revision) {
        this.revision = revision;
    }
    public void setDate(SVNDate date) {
        this.date = date;
    }
    public void setAuthor(String author) {
        this.author = author;
    }
}
