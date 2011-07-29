package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnCheckout extends AbstractSvnUpdate<Long> {
    
    private boolean allowUnversionedObstructions;
    private SVNURL url;

    protected SvnCheckout(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isAllowUnversionedObstructions() {
        return allowUnversionedObstructions;
    }

    public SVNURL getUrl() {
        return url;
    }

    public void setAllowUnversionedObstructions(boolean allowUnversionedObstructions) {
        this.allowUnversionedObstructions = allowUnversionedObstructions;
    }

    public void setUrl(SVNURL url) {
        this.url = url;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getRevision() == null) {
            setRevision(SVNRevision.UNDEFINED);
        }
        if (getPegRevision() == null) {
            setPegRevision(SVNRevision.UNDEFINED);
        }
        if (!getRevision().isValid() && getPegRevision().isValid()) {
            setRevision(getPegRevision());            
        }
        if (!getRevision().isValid()) {
            setRevision(SVNRevision.HEAD);
        }
        
        if (getUrl() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (getFirstTarget() == null || hasRemoteTargets()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (getRevision().getNumber() < 0 && getRevision().getDate() == null && getRevision() != SVNRevision.HEAD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    

}
