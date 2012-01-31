package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnDiffSummarize extends SvnReceivingOperation<SvnDiffStatus> {

    private SvnTarget firstSource;
    private SvnTarget secondSource;
    
    private SvnTarget source;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    
    private boolean ignoreAncestry;

    protected SvnDiffSummarize(SvnOperationFactory factory) {
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
    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }
    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
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

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getSource() == null || getSource().getPegRevision() == null || getSource().getPegRevision() == SVNRevision.UNDEFINED) {
            final SvnTarget firstSource = getFirstSource();
            final SvnTarget secondSource = getSecondSource();
            ensureArgumentsAreValid(firstSource.getURL(), firstSource.getFile(), firstSource.getPegRevision(),
                    secondSource.getURL(), secondSource.getFile(), secondSource.getPegRevision(),
                    null);
        } else {
            final SvnTarget source = getSource();
            ensureArgumentsAreValid(source.getURL(), source.getFile(), getStartRevision(),
                    source.getURL(), source.getFile(), getEndRevision(),
                    source.getPegRevision());
        }
    }

    private void ensureArgumentsAreValid(SVNURL url1, File path1, SVNRevision revision1,
                                         SVNURL url2, File path2, SVNRevision revision2,
                                         SVNRevision pegRevision) throws SVNException {
        if (pegRevision == null) {
            pegRevision = SVNRevision.UNDEFINED;
        }
        ensureRevisionIsValid(revision1);
        ensureRevisionIsValid(revision2);

        final boolean isPath1Local = startRevision == SVNRevision.WORKING || startRevision == SVNRevision.BASE;
        final boolean isPath2Local = endRevision == SVNRevision.WORKING || endRevision == SVNRevision.BASE;

        final boolean isRepos1;
        final boolean isRepos2;

        if (pegRevision != SVNRevision.UNDEFINED) {
            if (isPath1Local && isPath2Local) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "At least one revision must be non-local for a pegged diff");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }

            isRepos1 = !isPath1Local;
            isRepos2 = !isPath2Local;
        } else {
            isRepos1 = !isPath1Local || (url1 != null);
            isRepos2 = !isPath2Local || (url2 != null);
        }

        if (!isRepos1 || !isRepos2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }

    private void ensureRevisionIsValid(SVNRevision revision) throws SVNException {
        final boolean revisionIsValid = revision != null && revision.isValid();
        if (!revisionIsValid) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }
}
