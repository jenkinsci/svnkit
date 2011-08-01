package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnSwitch extends AbstractSvnUpdate<Long> {

    private boolean depthIsSticky;
    private boolean ignoreAncestry;
    
    private SVNURL switchUrl;

    protected SvnSwitch(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isDepthIsSticky() {
        return depthIsSticky;
    }

    public boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    public SVNURL getSwitchUrl() {
        return switchUrl;
    }

    public void setDepthIsSticky(boolean depthIsSticky) {
        this.depthIsSticky = depthIsSticky;
    }

    public void setIgnoreAncestry(boolean ignoreAncestry) {
        this.ignoreAncestry = ignoreAncestry;
    }

    public void setSwitchUrl(SVNURL switchUrl) {
        this.switchUrl = switchUrl;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.EXCLUDE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot both exclude and switch a path");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    
}
