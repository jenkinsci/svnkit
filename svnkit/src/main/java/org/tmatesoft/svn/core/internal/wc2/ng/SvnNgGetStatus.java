package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNExternalsStore;
import org.tmatesoft.svn.core.internal.wc17.SVNRemoteStatusEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNStatusEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNStatusReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgGetStatus extends SvnNgOperationRunner<SvnStatus, SvnGetStatus> implements ISvnObjectReceiver<SvnStatus> {
    
    private boolean targetDeletedInRepository;
    
    @Override
    protected SvnStatus run(SVNWCContext context) throws SVNException {
        File directoryPath;
        String targetName;
        
        SVNNodeKind kind = context.readKind(getFirstTarget(), false);
        if (kind == SVNNodeKind.DIR) {
            directoryPath = getFirstTarget();
            targetName = "";            
        } else {
            directoryPath = SVNFileUtil.getParentFile(getFirstTarget());
            targetName = SVNFileUtil.getFileName(getFirstTarget());
            if (kind != SVNNodeKind.FILE) {
                boolean notAWc = false;
                try {
                    kind = context.readKind(directoryPath, false);
                    notAWc = kind != SVNNodeKind.DIR;
                } catch (SVNException e) {
                    notAWc = true;
                }
                if (notAWc) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "''{0}'' is not a working copy", getFirstTarget());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        
        String[] globalIgnores = context.getOptions().getIgnorePatterns();
        
        if (getOperation().isRemote()) {
            SVNURL url = context.getUrlFromPath(directoryPath);
            if (url == null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry ''{0}'' has no URL", directoryPath);
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            SVNRepository repository = getRepositoryAccess().createRepository(url, null, true);
            long rev;
            if (getOperation().getRevision() == SVNRevision.HEAD) {
                rev = -1;
            } else {
                rev = context.getRevisionNumber(getOperation().getRevision(), null, repository, getFirstTarget());
            }
            kind = repository.checkPath("", -1);
            checkCancelled();
            SVNStatusEditor17 editor = null;
            SVNReporter17 reporter = null;
            if (kind == SVNNodeKind.NONE) {
                boolean added = context.isNodeAdded(directoryPath);
                if (added) {
                    boolean replaced = context.isNodeReplaced(directoryPath);
                    if (replaced) {
                        added = false;
                    }
                }
                setTargetDeletedInRepository(!added);
                editor = new SVNStatusEditor17(getFirstTarget(), context, 
                        getOperation().getOptions(), getOperation().isReportIgnored(), getOperation().isReportAll(), getOperation().getDepth(), this);
                editor.setFileListHook(getOperation().getFileListHook());

                checkCancelled();
                editor.closeEdit();
            } else {
                editor = new SVNRemoteStatusEditor17(directoryPath, targetName, context, 
                        getOperation().getOptions(), getOperation().isReportIgnored(), getOperation().isReportAll(), 
                        getOperation().getDepth(), this);
                editor.setFileListHook(getOperation().getFileListHook());
                
                SVNRepository locksRepos = getRepositoryAccess().createRepository(url, null, false);
                checkCancelled();
                boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
                reporter = new SVNReporter17(getFirstTarget(), context, false, !serverSupportsDepth, 
                        getOperation().getDepth(), false, true, true, false, null);
                SVNStatusReporter17 statusReporter = new SVNStatusReporter17(locksRepos, reporter, editor);
                String target = "".equals(targetName) ? null : targetName;
                repository.status(rev, target, getOperation().getDepth(), statusReporter, SVNCancellableEditor.newInstance((ISVNEditor) editor, this, null));
            }
            getOperation().setRemoteRevision(editor.getTargetRevision());                
            
            long reportedFiles = reporter != null ? reporter.getReportedFilesCount() : 0;
            long totalFiles = reporter != null ? reporter.getTotalFilesCount() : 0;
            SVNEvent event = SVNEventFactory.createSVNEvent(getFirstTarget(), SVNNodeKind.NONE, null, editor.getTargetRevision(), SVNEventAction.STATUS_COMPLETED, null, null, null,
                    reportedFiles, totalFiles);
            
            handleEvent(event, ISVNEventHandler.UNKNOWN);
        } else {
            SVNStatusEditor17 editor = new SVNStatusEditor17(directoryPath, context, context.getOptions(), 
                    getOperation().isReportIgnored(), 
                    getOperation().isReportAll(), 
                    getOperation().getDepth(), 
                    this);
            editor.setFileListHook(getOperation().getFileListHook());
            try {
                editor.walkStatus(getFirstTarget(), 
                        getOperation().getDepth(),
                        getOperation().isReportAll(),
                        getOperation().isReportIgnored(),
                        false,
                        globalIgnores != null ? Arrays.asList(globalIgnores) : null);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_MISSING) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "''{0}'' is not a working copy", getFirstTarget());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                throw e;
            }
        }
        
        if (getOperation().isReportExternals()) {
            SVNExternalsStore externalsStore = new SVNExternalsStore();
            context.getDb().gatherExternalDefinitions(getFirstTarget(), externalsStore);
            
            doExternalStatus(externalsStore.getNewExternals());
        }
        
        return getOperation().first();
    }
    
    private void doExternalStatus(Map<File, String> externalsNew) throws SVNException {
        for (Iterator<File> paths = externalsNew.keySet().iterator(); paths.hasNext();) {
            File path = paths.next();
            String propVal = externalsNew.get(path);
            SVNExternal[] externals = SVNExternal.parseExternals(path, propVal);
            
            for (int i = 0; i < externals.length; i++) {
                SVNExternal external = externals[i];
                File fullPath = new File(path, external.getPath());
                if (SVNFileType.getType(fullPath) != SVNFileType.DIRECTORY) {
                    continue;
                }
                handleEvent(SVNEventFactory.createSVNEvent(fullPath, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.STATUS_EXTERNAL, null, null, null), ISVNEventHandler.UNKNOWN);
                try {
                    
                    SvnGetStatus getStatus = getOperation().getOperationFactory().createGetStatus();
                    getStatus.setSingleTarget(SvnTarget.fromFile(fullPath));
                    getStatus.setRevision(SVNRevision.HEAD);
                    getStatus.setDepth(getOperation().getDepth());
                    getStatus.setRemote(getOperation().isRemote());
                    getStatus.setReportAll(getOperation().isReportAll());
                    getStatus.setReportIgnored(getOperation().isReportIgnored());
                    getStatus.setReportExternals(getOperation().isReportExternals());
                    getStatus.setReceiver(getOperation().getReceiver());
                    getStatus.setFileListHook(getOperation().getFileListHook());
                    
                    getStatus.run();
                } catch (SVNException e) {
                    if (e instanceof SVNCancelException) {
                        throw e;
                    }
                } 
            }
        }
    }

    private void setTargetDeletedInRepository(boolean deleted) {
        this.targetDeletedInRepository = deleted;
    }

    private boolean isTargetDeletedInRepository() {
        return targetDeletedInRepository;
    }

    public void receive(SvnTarget target, SvnStatus object) throws SVNException {
        if (getOperation().getApplicableChangelists() != null && !getOperation().getApplicableChangelists().isEmpty()) {
            if (!getOperation().getApplicableChangelists().contains(object.getChangelist())) {
                return;
            }
        }      
        if (isTargetDeletedInRepository()) {
            object.setRepositoryNodeStatus(SVNStatusType.STATUS_DELETED);
        }
        getOperation().receive(target, object);
    }

    @Override
    public void reset() {
        super.reset();
        setTargetDeletedInRepository(false);
    }
}
