package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnDiff extends SvnOperation<Void> {
    
    private ISVNDiffGenerator diffGenerator;
    private SVNDiffOptions diffOptions;
    private OutputStream output;
    
    private SvnTarget firstSource;
    private SvnTarget secondSource;
    
    private SvnTarget source;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    
    private boolean ignoreAncestry;
    private boolean noDiffDeleted;
    private boolean showCopiesAsAdds;
    private boolean ignoreContentType;
    private File relativeToDirectory;

    protected SvnDiff(SvnOperationFactory factory) {
        super(factory);
    }

    public void setSource(SvnTarget source, SVNRevision start, SVNRevision end) {
        this.source = source;
        this.startRevision = start;
        this.endRevision = end;
        if (source != null) {
            setSources(null, null);
        }
    }
    
    public void setSources(SvnTarget source1, SvnTarget source2) {
        this.firstSource = source1;
        this.secondSource = source2;
        if (firstSource != null) {
            setSource(null, null, null);
        }
    }
    
    public SvnTarget getSource() {
        return source;
    }
    
    public SVNRevision getStartRevision() {
        return startRevision;
    }
    
    public SVNRevision getEndRevision() {
        return endRevision;
    }
    
    public SvnTarget getFirstSource() {
        return firstSource;
    }

    public SvnTarget getSecondSource() {
        return secondSource;
    }

    public void setRelativeToDirectory(File relativeToDirectory) {
        this.relativeToDirectory = relativeToDirectory;
    }

    public File getRelativeToDirectory() {
        return relativeToDirectory;
    }

    public ISVNDiffGenerator getDiffGenerator() {
        return diffGenerator;
    }

    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        this.diffGenerator = diffGenerator;
    }

    public SVNDiffOptions getDiffOptions() {
        return diffOptions;
    }

    public void setDiffOptions(SVNDiffOptions diffOptions) {
        this.diffOptions = diffOptions;
    }

    public OutputStream getOutput() {
        return output;
    }

    public void setOutput(OutputStream output) {
        this.output = output;
    }

    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    public boolean isNoDiffDeleted() {
        return noDiffDeleted;
    }

    public void setNoDiffDeleted(boolean noDiffDeleted) {
        this.noDiffDeleted = noDiffDeleted;
    }

    public boolean isShowCopiesAsAdds() {
        return showCopiesAsAdds;
    }

    public void setShowCopiesAsAdds(boolean showCopiesAsAdds) {
        this.showCopiesAsAdds = showCopiesAsAdds;
    }

    public boolean isIgnoreContentType() {
        return ignoreContentType;
    }

    public void setIgnoreContentType(boolean ignoreContentType) {
        this.ignoreContentType = ignoreContentType;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
    }

    @Override
    protected File getOperationalWorkingCopy() {
        if (getSource() != null && getSource().isFile()) {
            return getSource().getFile();
        } else if (getFirstSource() != null && getFirstSource().isFile()) {
            return getFirstSource().getFile();
        } else if (getSecondSource() != null && getSecondSource().isFile()) {
            return getSecondSource().getFile();
        }
        
        return null;
    }
}
