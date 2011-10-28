package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnCopySource extends SvnObject {
    
    private SvnTarget source;
    private SVNRevision revision;
    
    public static SvnCopySource create(SvnTarget source, SVNRevision revision) {
        return new SvnCopySource(source, revision);
    }
    
    private SvnCopySource(SvnTarget source, SVNRevision revision) {
        setSource(source);
        if (revision == null || !revision.isValid()) {
            revision = source.getPegRevision();
        }
        setRevision(revision);
    }
    
    public boolean isLocal() {
        return getSource().isLocal() && getRevision().isLocal();
    }
    
    public SvnTarget getSource() {
        return source;
    }

    public SVNRevision getRevision() {
        return revision;
    }

    private void setSource(SvnTarget source) {
        this.source = source;
    }
    private void setRevision(SVNRevision revision) {
        this.revision = revision;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((revision == null) ? 0 : revision.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SvnCopySource other = (SvnCopySource) obj;
        if (revision == null) {
            if (other.revision != null) {
                return false;
            }
        } else if (!revision.equals(other.revision)) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return getSource().toString() + " r" + getRevision();
    }
}
