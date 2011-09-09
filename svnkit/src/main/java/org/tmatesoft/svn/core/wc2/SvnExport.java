package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnExport extends AbstractSvnUpdate<Long> {
    
    private boolean force;
    private boolean expandKeywords;
    private String eolStyle;
    private File destination;

    protected SvnExport(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isForce() {
        return force;
    }

    public boolean isExpandKeywords() {
        return expandKeywords;
    }

    public String getEolStyle() {
        return eolStyle;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public void setExpandKeywords(boolean expandKeywords) {
        this.expandKeywords = expandKeywords;
    }

    public void setEolStyle(String eolStyle) {
        this.eolStyle = eolStyle;
    }

    public File getDestination() {
        return destination;
    }
    
    public void setDestination(File destination) {
        this.destination = destination;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getDestination() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Destination path is required for export.");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (getDepth() == null || getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.INFINITY);
        }
        if (!hasRemoteTargets() && getRevision() == SVNRevision.UNDEFINED) {
            setRevision(SVNRevision.WORKING);
        }
        if (!hasRemoteTargets() && getPegRevision() == SVNRevision.UNDEFINED) {
            setPegRevision(SVNRevision.WORKING);
        }
        super.ensureArgumentsAreValid();
    }

    @Override
    protected void initDefaults() {
        super.initDefaults();
        setExpandKeywords(true);
        setDepth(SVNDepth.INFINITY);
    }
}
