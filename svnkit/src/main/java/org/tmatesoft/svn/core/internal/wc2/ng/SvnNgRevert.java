package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgRevert extends SvnNgOperationRunner<SvnRevert, SvnRevert> {

    @Override
    protected SvnRevert run(SVNWCContext context) throws SVNException {
        boolean useCommitTimes = getOperation().getOptions().isUseCommitTimes();
        
        for (SvnTarget target : getOperation().getTargets()) {
            checkCancelled();
            
            boolean isWcRoot = context.getDb().isWCRoot(target.getFile());
            File lockTarget = isWcRoot ? target.getFile() : SVNFileUtil.getParentFile(target.getFile());
            File lockRoot = context.acquireWriteLock(lockTarget, false, true);
            try {
                revert(target.getFile(), getOperation().getDepth(), useCommitTimes, getOperation().getApplicableChangelists());
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                if (err.getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND 
                        || err.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE 
                        || err.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    SVNEvent event = SVNEventFactory.createSVNEvent(target.getFile(), SVNNodeKind.NONE, null, -1, SVNEventAction.SKIP, SVNEventAction.REVERT, err, null, -1, -1);
                    handleEvent(event);
                    continue;
                }
                if (!useCommitTimes) {
                    sleepForTimestamp();
                }
                throw e;
            } finally {
                context.releaseWriteLock(lockRoot);
            }
        }
        if (!useCommitTimes) {
            sleepForTimestamp();
        }
        return getOperation();
    }

    private void revert(File file, SVNDepth depth, boolean useCommitTimes, Collection<String> changelists) throws SVNException {
        if (changelists != null && changelists.size() > 0) {
            // TODO revert changelist
            return;
        }
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.INFINITY) {
            // normal revert
            revert(file, depth, useCommitTimes);
            return;
        }
        if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
            // TODO partial revert
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OPERATION_DEPTH);
        SVNErrorManager.error(err, SVNLogType.WC);
        
    }

    private void revert(File localAbsPath, SVNDepth depth, boolean useCommitTimes) throws SVNException {
        File wcRoot = getWcContext().getDb().getWCRoot(localAbsPath);
        if (!localAbsPath.equals(wcRoot)) {
            getWcContext().writeCheck(SVNFileUtil.getParentFile(localAbsPath));
        } else {
            getWcContext().writeCheck(localAbsPath);
        }
        
        getWcContext().getDb().opRevert(localAbsPath, depth);
        
        // TODO revert files in wc.
    }

}
