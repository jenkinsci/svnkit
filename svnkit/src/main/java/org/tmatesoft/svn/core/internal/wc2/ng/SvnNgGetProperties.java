package org.tmatesoft.svn.core.internal.wc2.ng;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgGetProperties extends SvnNgOperationRunner<SvnGetProperties> {

    @Override
    protected void run(SVNWCContext context) throws SVNException {
        boolean pristine = getOperation().getRevision() == SVNRevision.COMMITTED || getOperation().getRevision() == SVNRevision.BASE;
        SVNNodeKind kind = context.readKind(getFirstTarget(), false);
        
        if (kind == SVNNodeKind.UNKNOWN || kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", getFirstTarget());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (kind == SVNNodeKind.DIR) {
            if (getOperation().getDepth() == SVNDepth.EMPTY) {
                SVNProperties properties = null;
                if (pristine) {
                    properties = context.getDb().readPristineProperties(getFirstTarget());
                } else {
                    properties = context.getDb().readProperties(getFirstTarget());
                }
                if (properties != null && !properties.isEmpty()) {
                    getOperation().getReceiver().receive(getOperation().getFirstTarget(), properties);
                }
            } else {
                SVNWCDb db = (SVNWCDb) context.getDb();
                db.readPropertiesRecursively(
                        getFirstTarget(), 
                        getOperation().getDepth(), 
                        false, 
                        pristine, 
                        getOperation().getApplicableChangelists(), 
                        getOperation().getReceiver());
            }
        } else {
            SVNProperties properties = null;
            if (pristine) {
                properties = context.getDb().readPristineProperties(getFirstTarget());
            } else {
                if (!context.isNodeStatusDeleted(getFirstTarget())) {
                    properties = context.getDb().readProperties(getFirstTarget());
                }
            }
            if (properties != null && !properties.isEmpty()) {
                getOperation().getReceiver().receive(getOperation().getFirstTarget(), properties);
            }
        }
        
    }

}
