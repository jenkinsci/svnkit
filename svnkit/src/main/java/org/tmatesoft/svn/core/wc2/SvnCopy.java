package org.tmatesoft.svn.core.wc2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class SvnCopy extends SvnOperation<Void> {
    
    private Collection<SvnCopySource> sources = new HashSet<SvnCopySource>();
    private boolean move;
    private boolean makeParents;
    private boolean failWhenDstExist;
    private boolean ignoreExternals;

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
        return move;
    }

    public void setMove(boolean isMove) {
        this.move = isMove;
    }
    
    public boolean isMakeParents() {
        return makeParents;
    }

    public void setMakeParents(boolean isMakeParents) {
        this.makeParents = isMakeParents;
    }

    public boolean isFailWhenDstExists() {
        return failWhenDstExist;
    }

    public void setFailWhenDstExists(boolean isFailWhenDstExist) {
        this.failWhenDstExist = isFailWhenDstExist;
    }

    public boolean isIgnoreExternals() {
        return ignoreExternals;
    }

    public void setIgnoreExternals(boolean ignoreExternals) {
        this.ignoreExternals = ignoreExternals;
    }
}
