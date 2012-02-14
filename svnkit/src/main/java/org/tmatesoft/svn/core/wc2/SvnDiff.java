package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.io.OutputStream;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnDiff extends SvnOperation<Void> {
    
    private ISVNDiffGenerator diffGenerator;
    private SVNDiffOptions diffOptions;
    private OutputStream output;
    
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

    public void setTarget(SvnTarget source, SVNRevision start, SVNRevision end) {
        setSingleTarget(source);
        this.startRevision = start;
        this.endRevision = end;
    }
    
    public void setTargets(SvnTarget target1, SvnTarget target2) {
        setTwoTargets(target1, target2);
        this.startRevision = target1.getPegRevision();
        this.endRevision = target2.getPegRevision();
    }

    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }

    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }

    public SVNRevision getStartRevision() {
        return startRevision;
    }
    
    public SVNRevision getEndRevision() {
        return endRevision;
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

    public SvnTarget getSecondTarget() {
        if (getTargets().size() < 2) {
            return null;
        }

        final Iterator<SvnTarget> iterator = getTargets().iterator();
        iterator.next();
        return iterator.next();
    }

    @Override
    protected int getMinimumTargetsCount() {
        return super.getMinimumTargetsCount();
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getRelativeToDirectory() != null && hasRemoteTargets()) {
            //TODO
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Relative directory cannot be specified with remote targets");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        if (getOutput() == null) {
            //TODO
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "No output is specified.");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

    @Override
    protected File getOperationalWorkingCopy() {
        if (getTargets().size() == 1 && getFirstTarget().isFile()) {
            return getFirstTarget().getFile();
        } else if (getTargets().size() == 2 && getFirstTarget() != null && getFirstTarget().isFile()) {
            return getFirstTarget().getFile();
        } else if (getTargets().size() == 2 && getSecondTarget() != null && getSecondTarget().isFile()) {
            return getSecondTarget().getFile();
        }

        return null;
    }
}
