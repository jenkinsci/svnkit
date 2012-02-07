package org.tmatesoft.svn.core.wc2;

import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc2.hooks.ISvnExternalsHandler;

public class SvnRemoteCopy extends AbstractSvnCommit {
    
    private boolean move;
    private boolean makeParents;
    private boolean failWhenDstExists;
    private ISvnExternalsHandler externalsHandler;
    private ISVNCommitParameters commitParameters;
    private boolean disableLocalModifications;

    private Collection<SvnCopySource> sources;
    
    protected SvnRemoteCopy(SvnOperationFactory factory) {
        super(factory);
        sources = new ArrayList<SvnCopySource>();
    }

    public boolean isMove() {
        return move;
    }

    public void setMove(boolean move) {
        this.move = move;
    }

    public boolean isMakeParents() {
        return makeParents;
    }

    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    public boolean isDisableLocalModifications() {
        return disableLocalModifications;
    }

    public void setDisableLocalModifications(boolean disableLocalModifications) {
        this.disableLocalModifications = disableLocalModifications;
    }

    public Collection<SvnCopySource> getSources() {
        return sources;
    }

    public void addCopySource(SvnCopySource source) {
        if (source != null) {
            this.sources.add(source);
        }
    }

    public boolean isFailWhenDstExists() {
        return failWhenDstExists;
    }

    public void setFailWhenDstExists(boolean failWhenDstExists) {
        this.failWhenDstExists = failWhenDstExists;
    }

    @Override
    public SVNCommitInfo run() throws SVNException {
        return super.run();
    }

    public ISvnExternalsHandler getExternalsHandler() {
        return externalsHandler;
    }

    public void setExternalsHandler(ISvnExternalsHandler externalsHandler) {
        this.externalsHandler = externalsHandler;
    }

    public ISVNCommitParameters getCommitParameters() {
        return commitParameters;
    }

    public void setCommitParameters(ISVNCommitParameters commitParameters) {
        this.commitParameters = commitParameters;
    }
}