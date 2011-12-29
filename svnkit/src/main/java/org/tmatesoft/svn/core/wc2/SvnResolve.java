package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.SVNConflictChoice;

public class SvnResolve extends SvnOperation<Long> {

    private SVNConflictChoice conflictChoice;
    private boolean resolveContents = true;
    private boolean resolveProperties = true;
    private boolean resolveTree = true;
        
    protected SvnResolve(SvnOperationFactory factory) {
        super(factory);
    }

    public SVNConflictChoice getConflictChoice() {
        return conflictChoice;
    }

    public void setConflictChoice(SVNConflictChoice conflictChoice) {
        this.conflictChoice = conflictChoice;
    }
    
    public boolean isResolveContents() {
        return resolveContents;
    }

    public void setResolveContents(boolean resolveContents) {
        this.resolveContents = resolveContents;
    }
    
    public boolean isResolveProperties() {
        return resolveProperties;
    }

    public void setResolveProperties(boolean resolveProperties) {
        this.resolveProperties = resolveProperties;
    }
    
    public boolean isResolveTree() {
        return resolveTree;
    }

    public void setResolveTree(boolean resolveTree) {
        this.resolveTree = resolveTree;
    }
    
        

}
