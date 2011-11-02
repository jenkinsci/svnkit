package org.tmatesoft.svn.core.wc2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnCopy extends SvnOperation<Long> {
    
    private Collection<SvnCopySource> sources = new HashSet<SvnCopySource>();
    private boolean isMove;
    private boolean isMakeParents;

    protected SvnCopy(SvnOperationFactory factory) {
        super(factory);
        this.sources = new HashSet<SvnCopySource>();
    }

    public Collection<SvnCopySource> getSources() {
        return Collections.unmodifiableCollection(sources);
    }
    
    public void addCopySource(SvnCopySource source) {
        if (source != null) {
            this.sources.add(source);
        }
    }
    
    public boolean isMove() {
        return isMove;
    }

    public void setMove(boolean isMove) {
        this.isMove = isMove;
    }
    

    protected void ensureHomohenousSources() throws SVNException {
        if (getSources().size() <= 1) {
            return;
        }
        boolean remote = false;
        boolean local = false;
        for (SvnCopySource source : getSources()) {
            remote |= source.isLocal();
            local |= !source.isLocal();
        }
        if (remote && local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot mix repository and working copy sources");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    public boolean isMakeParents() {
        return isMakeParents;
    }

    public void setMakeParents(boolean isMakeParents) {
        this.isMakeParents = isMakeParents;
    }
}
