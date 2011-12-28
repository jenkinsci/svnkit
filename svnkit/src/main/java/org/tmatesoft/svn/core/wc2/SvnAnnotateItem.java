package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNDate;

public class SvnAnnotateItem {

    private long revision;
    private SVNProperties revisionProperties;
    private SVNProperties mergedRevisionProperties;
    private String line;
    private long mergedRevision;
    private String mergedPath;
    private int lineNumber;
    private File contents;
    private boolean isEof;
    private boolean isRevision;
    private boolean isLine;
    private boolean returnResult;

    public SvnAnnotateItem(boolean isEof) {
        this.isEof = true;
    }

    public SvnAnnotateItem(Date date, long revision, String author, String line, Date mergedDate,
                           long mergedRevision, String mergedAuthor, String mergedPath, int lineNumber) {
        this.isLine = true;
        this.revisionProperties = createRevisionProperties(author, date);
        this.revision = revision;
        this.line = line;
        this.mergedRevisionProperties = createRevisionProperties(mergedAuthor, mergedDate);
        this.mergedRevision = mergedRevision;
        this.mergedPath = mergedPath;
        this.lineNumber = lineNumber;
    }

    public SvnAnnotateItem(Date date, long revision, String author, File contents) {
        this.isRevision = true;
        this.revisionProperties = createRevisionProperties(author, date);
        this.revision = revision;
        this.contents = contents;
    }

    public Date getDate() {
        return getDate(getRevisionProperties());
    }

    public long getRevision() {
        return revision;
    }

    public SVNProperties getRevisionProperties() {
        return revisionProperties;
    }

    public String getAuthor() {
        return getAuthor(getRevisionProperties());
    }

    public Date getMergedDate() {
        return getDate(getMergedRevisionProperties());
    }

    public String getLine() {
        return line;
    }

    public long getMergedRevision() {
        return mergedRevision;
    }

    public SVNProperties getMergedRevisionProperties() {
        return mergedRevisionProperties;
    }

    public String getMergedAuthor() {
        return getAuthor(getMergedRevisionProperties());
    }

    public String getMergedPath() {
        return mergedPath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public File getContents() {
        return contents;
    }

    public boolean isEof() {
        return isEof;
    }

    public boolean isLine() {
        return isLine;
    }

    public boolean isRevision() {
        return isRevision;
    }

    public void setReturnResult(boolean returnResult) {
        this.returnResult = returnResult;
    }

    public boolean getReturnResult() {
        return returnResult;
    }

    private SVNProperties createRevisionProperties(String author, Date date) {
        if (author == null && date == null) {
            return null;
        }
        SVNProperties properties = new SVNProperties();
        if (author != null) {
            properties.put(SVNRevisionProperty.AUTHOR, author);
        }
        if (date != null) {
            properties.put(SVNRevisionProperty.DATE, SVNDate.fromDate(date).format());
        }
        return properties;
    }

    private String getAuthor(SVNProperties revisionProperties) {
        if (revisionProperties == null) {
            return null;
        }
        return revisionProperties.getStringValue(SVNRevisionProperty.AUTHOR);
    }

    private Date getDate(SVNProperties revisionProperties) {
        if (revisionProperties == null) {
            return null;
        }
        String dateString = revisionProperties.getStringValue(SVNRevisionProperty.DATE);
        if (dateString == null) {
            return null;
        }
        return SVNDate.parseDate(dateString);
    }
}
