package org.tmatesoft.svn.core.wc2;

import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;


public class SvnMerge extends SvnOperation<Void> {
    
    private SvnTarget firstSource;
    private SvnTarget secondSource;
    
    private boolean ignoreAncestry;
    private boolean force;
    private boolean recordOnly;
    private boolean dryRun;
    private boolean allowMixedRevisions;
    
    private SvnTarget source;
    private boolean reintegrate;

    private Collection<SvnRevisionRange> ranges;
    private SVNDiffOptions mergeOptions;
    
    protected SvnMerge(SvnOperationFactory factory) {
        super(factory);
    }

    public void addRevisionRange(SvnRevisionRange range) {
        if (ranges == null) {
            ranges = new ArrayList<SvnRevisionRange>();
        }
        SVNRevision start = range.getStart();
        SVNRevision end = range.getEnd();        
        if (start == SVNRevision.UNDEFINED && end == SVNRevision.UNDEFINED) {
            start = SVNRevision.create(0);
            end = getSource().getResolvedPegRevision();
            range  = SvnRevisionRange.create(start, end);
        }
        ranges.add(range);
    }
    
    public Collection<SvnRevisionRange> getRevisionRanges() {
        return ranges;
    }
    
    public void setSource(SvnTarget source, boolean reintegrate) {
        this.source = source;
        this.reintegrate = reintegrate;
        if (source != null) {
            setSources(null, null);
        }
    }
    
    public void setSources(SvnTarget source1, SvnTarget source2) {
        this.firstSource = source1;
        this.secondSource = source2;
        if (firstSource != null) {
            setSource(null, false);
        }
    }
    
    public SvnTarget getSource() {
        return this.source;
    }
    
    public SvnTarget getFirstSource() {
        return this.firstSource;
    }
    
    public SvnTarget getSecondSource() {
        return this.secondSource;
    }
    
    public boolean isReintegrate() {
        return this.reintegrate;
    }

    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isRecordOnly() {
        return recordOnly;
    }

    public void setRecordOnly(boolean recordOnly) {
        this.recordOnly = recordOnly;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isAllowMixedRevisions() {
        return allowMixedRevisions;
    }

    public void setAllowMixedRevisions(boolean allowMixedRevisions) {
        this.allowMixedRevisions = allowMixedRevisions;
    }

    public SVNDiffOptions getMergeOptions() {
        return mergeOptions;
    }

    public void setMergeOptions(SVNDiffOptions mergeOptions) {
        this.mergeOptions = mergeOptions;
    }
    
    
}
