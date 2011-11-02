package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnRevisionRange extends SvnObject {
    
    private SVNRevision start;
    private SVNRevision end;
    
    public static SvnRevisionRange create(SVNRevision start, SVNRevision end) {
        return new SvnRevisionRange(start, end);
    }
    
    private SvnRevisionRange(SVNRevision start, SVNRevision end) {
        this.start = start == null ? SVNRevision.UNDEFINED : start;
        this.end = end == null ? SVNRevision.UNDEFINED : end;
    }
    
    @Override
    public String toString() {
        return getStart() + ":" + getEnd();
    }

    public SVNRevision getStart() {
        return start;
    }

    public SVNRevision getEnd() {
        return end;
    }
}
