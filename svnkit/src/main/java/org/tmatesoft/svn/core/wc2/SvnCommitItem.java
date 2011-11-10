package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;

public class SvnCommitItem {
    
    public static final int ADD = 0x01;
    public static final int DELETE = 0x02;
    public static final int TEXT_MODIFIED = 0x04;
    public static final int PROPS_MODIFIED = 0x08;
    public static final int COPY = 0x10;
    public static final int LOCK = 0x20;
    
    private File path;
    private SVNNodeKind kind;
    private SVNURL url;
    private long revision;
    
    private SVNURL copyFromUrl;
    private long copyFromRevision;
    
    private int flags;
    private Map<String, SVNPropertyValue> outgoingProperties;
    
    public File getPath() {
        return path;
    }
    public SVNNodeKind getKind() {
        return kind;
    }
    public SVNURL getUrl() {
        return url;
    }
    public long getRevision() {
        return revision;
    }
    public SVNURL getCopyFromUrl() {
        return copyFromUrl;
    }
    public long getCopyFromRevision() {
        return copyFromRevision;
    }
    public int getFlags() {
        return flags;
    }
    public void setPath(File path) {
        this.path = path;
    }
    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }
    public void setUrl(SVNURL url) {
        this.url = url;
    }
    public void setRevision(long revision) {
        this.revision = revision;
    }
    public void setCopyFromUrl(SVNURL copyFromUrl) {
        this.copyFromUrl = copyFromUrl;
    }
    public void setCopyFromRevision(long copyFromRevision) {
        this.copyFromRevision = copyFromRevision;
    }
    
    public void setFlags(int commitFlags) {
        this.flags = commitFlags;
    }
    public boolean hasFlag(int flag) {
        return (getFlags() & flag) != 0;
    }
    
    public Map<String, SVNPropertyValue> getOutgoingProperties() {
        return outgoingProperties;
    }

    public void addOutgoingProperty(String name, SVNPropertyValue value) {
        if (outgoingProperties == null) {
            outgoingProperties = new HashMap<String, SVNPropertyValue>();
        }
        if (name != null) {
            if (value != null) {
                outgoingProperties.put(name, value);
            } else {
                outgoingProperties.remove(name);
            }
        }
    }
}
