package org.tmatesoft.svn.core.javahl17;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.ClientNotifyInformation;
import org.apache.subversion.javahl.CommitInfo;
import org.apache.subversion.javahl.CommitItem;
import org.apache.subversion.javahl.ConflictDescriptor;
import org.apache.subversion.javahl.ConflictResult;
import org.apache.subversion.javahl.ConflictResult.Choice;
import org.apache.subversion.javahl.DiffSummary;
import org.apache.subversion.javahl.ISVNClient;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.BlameCallback;
import org.apache.subversion.javahl.callback.ChangelistCallback;
import org.apache.subversion.javahl.callback.ClientNotifyCallback;
import org.apache.subversion.javahl.callback.CommitCallback;
import org.apache.subversion.javahl.callback.CommitMessageCallback;
import org.apache.subversion.javahl.callback.ConflictResolverCallback;
import org.apache.subversion.javahl.callback.DiffSummaryCallback;
import org.apache.subversion.javahl.callback.InfoCallback;
import org.apache.subversion.javahl.callback.ListCallback;
import org.apache.subversion.javahl.callback.LogMessageCallback;
import org.apache.subversion.javahl.callback.PatchCallback;
import org.apache.subversion.javahl.callback.ProgressCallback;
import org.apache.subversion.javahl.callback.ProplistCallback;
import org.apache.subversion.javahl.callback.StatusCallback;
import org.apache.subversion.javahl.callback.UserPasswordCallback;
import org.apache.subversion.javahl.types.Checksum;
import org.apache.subversion.javahl.types.ConflictVersion;
import org.apache.subversion.javahl.types.CopySource;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.DirEntry;
import org.apache.subversion.javahl.types.Info;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Mergeinfo;
import org.apache.subversion.javahl.types.Mergeinfo.LogKind;
import org.apache.subversion.javahl.types.NodeKind;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.RevisionRange;
import org.apache.subversion.javahl.types.Status;
import org.apache.subversion.javahl.types.Version;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHConnector;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchHunkInfo;
import org.tmatesoft.svn.core.javahl.JavaHLCompositeLog;
import org.tmatesoft.svn.core.javahl.JavaHLDebugLog;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnAnnotate;
import org.tmatesoft.svn.core.wc2.SvnAnnotateItem;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnCleanup;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnDiffStatus;
import org.tmatesoft.svn.core.wc2.SvnDiffSummarize;
import org.tmatesoft.svn.core.wc2.SvnExport;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnGetMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnImport;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnLogMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRelocate;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnRemoteMkDir;
import org.tmatesoft.svn.core.wc2.SvnRemoteSetProperty;
import org.tmatesoft.svn.core.wc2.SvnResolve;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnSchedule;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnSetChangelist;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnSuggestMergeSources;
import org.tmatesoft.svn.core.wc2.SvnSwitch;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUnlock;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;
import org.tmatesoft.svn.core.wc2.SvnWorkingCopyInfo;
import org.tmatesoft.svn.core.wc2.hooks.ISvnCommitHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNClientImpl implements ISVNClient {

    public static SVNClientImpl newInstance() {
        return new SVNClientImpl();
    }

    private static int instanceCount;

    private SvnOperationFactory svnOperationFactory;
    private boolean shouldDisposeSvnOperationsFactory;
    private String username;
    private String password;
    private UserPasswordCallback prompt;
    private ClientNotifyCallback notifyCallback;
    private String configDir;

    private DefaultSVNOptions options;
    private ISVNAuthenticationManager authenticationManager;
    private ISVNConflictHandler conflictHandler;
    private ISVNEventHandler eventHandler;
    private boolean cancelOperation;

    private JavaHLCompositeLog debugLog;
    private JavaHLProgressLog progressListener;

    protected SVNClientImpl() {
        this(null);
    }

    protected SVNClientImpl(SvnOperationFactory svnOperationFactory) {
        this.shouldDisposeSvnOperationsFactory = svnOperationFactory == null;
        this.svnOperationFactory = svnOperationFactory == null ? new SvnOperationFactory() : svnOperationFactory;
        this.svnOperationFactory.setEventHandler(getEventHandler());

        synchronized (SVNClientImpl.class) {
            instanceCount++;
        }
    }

    public void dispose() {
        if (shouldDisposeSvnOperationsFactory && svnOperationFactory != null) {
            svnOperationFactory.dispose();
        }
        synchronized (SVNClientImpl.class) {
            instanceCount--;
            if (instanceCount <= 0) {
                instanceCount = 0;
                SVNSSHConnector.shutdown();
            }
        }

    }

    public Version getVersion() {
        return SVNClientImplVersion.getInstance();
    }

    public String getAdminDirectoryName() {
        return SVNFileUtil.getAdminDirectoryName();
    }

    public boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ?
                SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) :
                SVNFileUtil.getAdminDirectoryName().equals(name);
    }

    public void status(String path, Depth depth, boolean onServer,
            boolean getAll, boolean noIgnore, boolean ignoreExternals,
            Collection<String> changelists, final StatusCallback callback)
            throws ClientException {


        SvnGetStatus status = svnOperationFactory.createGetStatus();
        status.setDepth(getSVNDepth(depth));
        status.setRemote(onServer);
        status.setReportAll(getAll);
        status.setReportIgnored(noIgnore);
        status.setReportExternals(!ignoreExternals);
        status.setApplicalbeChangelists(changelists);
        status.setReceiver(getStatusReceiver(callback));

        status.addTarget(getTarget(path));
        try {
            status.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void list(String url, Revision revision, Revision pegRevision,
            Depth depth, int direntFields, boolean fetchLocks,
            ListCallback callback) throws ClientException {
        SvnList list = svnOperationFactory.createList();
        list.setSingleTarget(getTarget(url, pegRevision));
        list.setRevision(getSVNRevision(revision));
        list.setDepth(getSVNDepth(depth));
        list.setEntryFields(direntFields);
        list.setFetchLocks(fetchLocks);
        list.setReceiver(getDirEntryReceiver(callback));
        try {
            list.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void username(String username) {
        this.username = username;
        updateSvnOperationsFactory();
    }

    public void password(String password) {
        this.password = password;
        updateSvnOperationsFactory();
    }

    public void setPrompt(UserPasswordCallback prompt) {
        this.prompt = prompt;
        updateSvnOperationsFactory();
    }

    public void logMessages(String path, Revision pegRevision,
            List<RevisionRange> ranges, boolean stopOnCopy,
            boolean discoverPath, boolean includeMergedRevisions,
            Set<String> revProps, long limit, LogMessageCallback callback)
            throws ClientException {
        SvnLog log = svnOperationFactory.createLog();
        log.setRevisionRanges(getSvnRevisionRanges(ranges));
        log.setStopOnCopy(stopOnCopy);
        log.setDiscoverChangedPaths(discoverPath);
        log.setUseMergeHistory(includeMergedRevisions);
        log.setRevisionProperties(getRevisionPropertiesNames(revProps));
        log.setLimit(limit);
        log.setReceiver(getLogEntryReceiver(callback));

        log.addTarget(getTarget(path, pegRevision));

        try {
            log.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public long checkout(String moduleName, String destPath, Revision revision,
            Revision pegRevision, Depth depth, boolean ignoreExternals,
            boolean allowUnverObstructions) throws ClientException {

        SvnCheckout checkout = svnOperationFactory.createCheckout();
        checkout.setSource(getTarget(moduleName, pegRevision));
        checkout.setSingleTarget(getTarget(destPath));
        checkout.setRevision(getSVNRevision(revision));
        checkout.setDepth(getSVNDepth(depth));
        checkout.setIgnoreExternals(ignoreExternals);
        checkout.setAllowUnversionedObstructions(allowUnverObstructions);

        try {
            return checkout.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void notification2(ClientNotifyCallback notifyCallback) {
        this.notifyCallback = notifyCallback;
    }

    public ISVNEventHandler getEventHandler() {
        if (eventHandler == null) {
            eventHandler = new ISVNEventHandler() {

                public void handleEvent(SVNEvent event, double progress) {
                    if (event.getAction() == SVNEventAction.UPGRADE) {
                        return;
                    }
                    String path = null;
                    if (event.getFile() != null) {
                        path = event.getFile().getAbsolutePath();
                        if (path != null) {
                            path = path.replace(File.separatorChar, '/');
                        }
                    }
                    if (path == null) {
                        path = "";
                    }
//                    if (notifyCallback != null && event.getErrorMessage() == null) {
//                        notifyCallback.onNotify(
//                                path,
//                                JavaHLObjectFactory.getNotifyActionValue(event.getAction()),
//                                JavaHLObjectFactory.getNodeKind(event.getNodeKind()),
//                                event.getMimeType(),
//                                JavaHLObjectFactory.getStatusValue(event.getContentsStatus()),
//                                JavaHLObjectFactory.getStatusValue(event.getPropertiesStatus()),
//                                event.getRevision()
//                        );
//                    }
                    if (notifyCallback != null) {
                        notifyCallback.onNotify(getClientNotifyInformation(event, path));
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                    if (cancelOperation) {
                        cancelOperation = false;
                        SVNErrorManager.cancel("operation cancelled", SVNLogType.DEFAULT);
                    }
                }
            };
        }
        return eventHandler;
    }

    public void setConflictResolver(ConflictResolverCallback callback) {
        conflictHandler = getConflictHandler(callback);
        updateSvnOperationsFactory();
    }

    public void setProgressCallback(ProgressCallback listener) {
        getDebugLog();//make sure debugLog is constructed
        if (listener != null) {
            progressListener = new JavaHLProgressLog(listener);
            debugLog.addLogger(progressListener);
        } else if (progressListener != null) {
            debugLog.removeLogger(progressListener);
            progressListener = null;
        }
    }

    public ISVNDebugLog getDebugLog() {
        if (debugLog == null) {
            debugLog = new JavaHLCompositeLog();
            debugLog.addLogger(SVNDebugLog.getDefaultLog());
            debugLog.addLogger(JavaHLDebugLog.getInstance());
        }
        return debugLog;
    }

    public void remove(Set<String> path, boolean force, boolean keepLocal,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        Set<String> localPaths = new HashSet<String>();
        Set<String> remoteUrls = new HashSet<String>();

        fillLocalAndRemoteTargets(path, localPaths, remoteUrls);

        removeLocal(localPaths, force, keepLocal);
        removeRemote(remoteUrls, revpropTable, handler, callback);
    }

    public void revert(String path, Depth depth, Collection<String> changelists)
            throws ClientException {
        SvnRevert revert = svnOperationFactory.createRevert();
        revert.setDepth(getSVNDepth(depth));
        revert.setApplicalbeChangelists(changelists);

        revert.addTarget(getTarget(path));

        try {
            revert.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void add(String path, Depth depth, boolean force, boolean noIgnores,
            boolean addParents) throws ClientException {
        SvnScheduleForAddition add = svnOperationFactory.createScheduleForAddition();
        add.setDepth(getSVNDepth(depth));
        add.setForce(force);
        add.setIncludeIgnored(noIgnores);
        add.setAddParents(addParents);

        add.addTarget(getTarget(path));

        try {
            add.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public long[] update(Set<String> path, Revision revision, Depth depth,
            boolean depthIsSticky, boolean makeParents,
            boolean ignoreExternals, boolean allowUnverObstructions)
            throws ClientException {
        SvnUpdate update = svnOperationFactory.createUpdate();
        update.setRevision(getSVNRevision(revision));
        update.setDepth(getSVNDepth(depth));
        update.setDepthIsSticky(depthIsSticky);
        update.setMakeParents(makeParents);
        update.setIgnoreExternals(ignoreExternals);
        update.setAllowUnversionedObstructions(allowUnverObstructions);

        for (String targetPath : path) {
            update.addTarget(getTarget(targetPath));
        }
        try {
            return update.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void commit(Set<String> path, Depth depth, boolean noUnlock,
            boolean keepChangelist, Collection<String> changelists,
            Map<String, String> revpropTable, final CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        SvnCommit commit = svnOperationFactory.createCommit();
        commit.setDepth(getSVNDepth(depth));
        commit.setKeepLocks(!noUnlock);
        commit.setKeepChangelists(keepChangelist);
        commit.setApplicalbeChangelists(changelists);
        commit.setRevisionProperties(getSVNProperties(revpropTable));
        commit.setCommitHandler(getCommitHandler(handler));
        commit.setReceiver(getCommitInfoReceiver(callback));

        for (String targetPath : path) {
            commit.addTarget(getTarget(targetPath));
        }

        try {
            commit.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void copy(List<CopySource> sources, String destPath,
            boolean copyAsChild, boolean makeParents, boolean ignoreExternals,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        final Set<CopySource> localSources = new HashSet<CopySource>();
        final Set<CopySource> remoteSources = new HashSet<CopySource>();

        fillLocalAndRemoteSources(sources, localSources, remoteSources);

        copyLocal(localSources, destPath, copyAsChild, makeParents, ignoreExternals);
        copyRemote(remoteSources, destPath, copyAsChild, makeParents, revpropTable, handler, callback);
    }

    public void move(Set<String> srcPaths, String destPath, boolean force,
            boolean moveAsChild, boolean makeParents,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        final Set<String> localPaths = new HashSet<String>();
        final Set<String> remoteUrls = new HashSet<String>();

        fillLocalAndRemoteTargets(srcPaths, localPaths, remoteUrls);

        moveLocal(localPaths, destPath, force, moveAsChild, makeParents);
        moveRemote(remoteUrls, destPath, force, moveAsChild, makeParents, revpropTable, handler, callback);
    }

    public void mkdir(Set<String> path, boolean makeParents,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        final Set<String> localPaths = new HashSet<String>();
        final Set<String> remoteUrls = new HashSet<String>();

        fillLocalAndRemoteTargets(path, localPaths, remoteUrls);

        mkdirLocal(localPaths, makeParents);
        mkdirRemote(remoteUrls, makeParents, revpropTable, handler, callback);
    }

    public void cleanup(String path) throws ClientException {
        SvnCleanup cleanup = svnOperationFactory.createCleanup();

        cleanup.addTarget(getTarget(path));

        try {
            cleanup.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void resolve(String path, Depth depth, Choice conflictResult)
            throws SubversionException {
        SvnResolve resolve = svnOperationFactory.createResolve();
        resolve.setDepth(getSVNDepth(depth));
        resolve.setConflictChoice(getSVNConflictChoice(conflictResult));

        resolve.addTarget(getTarget(path));

        try {
            resolve.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public long doExport(String srcPath, String destPath, Revision revision,
            Revision pegRevision, boolean force, boolean ignoreExternals,
            Depth depth, String nativeEOL) throws ClientException {
        SvnExport export = svnOperationFactory.createExport();
        export.setSource(getTarget(srcPath, pegRevision));
        export.setSingleTarget(getTarget(destPath));
        export.setRevision(getSVNRevision(revision));
        export.setForce(force);
        export.setIgnoreExternals(ignoreExternals);
        export.setDepth(getSVNDepth(depth));
        export.setEolStyle(nativeEOL);
        try {
            return export.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public long doSwitch(String path, String url, Revision revision,
            Revision pegRevision, Depth depth, boolean depthIsSticky,
            boolean ignoreExternals, boolean allowUnverObstructions,
            boolean ignoreAncestry) throws ClientException {
        SvnSwitch svnSwitch = svnOperationFactory.createSwitch();
        svnSwitch.setSingleTarget(getTarget(path));
        svnSwitch.setSwitchTarget(getTarget(url, pegRevision));
        svnSwitch.setRevision(getSVNRevision(revision));
        svnSwitch.setDepth(getSVNDepth(depth));
        svnSwitch.setDepthIsSticky(depthIsSticky);
        svnSwitch.setIgnoreExternals(ignoreExternals);
        svnSwitch.setAllowUnversionedObstructions(allowUnverObstructions);
        svnSwitch.setIgnoreAncestry(ignoreAncestry);
        try {
            return svnSwitch.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void doImport(String path, String url, Depth depth,
            boolean noIgnore, boolean ignoreUnknownNodeTypes,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        SvnImport svnImport = svnOperationFactory.createImport();
        svnImport.setDepth(getSVNDepth(depth));
        svnImport.setUseGlobalIgnores(!noIgnore);
        //TODO: how to respect ignoreUnknownNodeTypes ?
        svnImport.setRevisionProperties(getSVNProperties(revpropTable));
        svnImport.setCommitHandler(getCommitHandler(handler));
        svnImport.setReceiver(getCommitInfoReceiver(callback));

        svnImport.setSource(new File(path));
        svnImport.addTarget(getTarget(url));

        try {
            svnImport.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public Set<String> suggestMergeSources(String path, Revision pegRevision)
            throws SubversionException {
        SvnSuggestMergeSources suggestMergeSources = svnOperationFactory.createSuggestMergeSources();
        suggestMergeSources.setSingleTarget(getTarget(path, pegRevision));

        Collection<SVNURL> mergeSources;
        try {
            mergeSources = suggestMergeSources.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }

        Set<String> mergeSourcesStrings = new HashSet<String>();
        for (SVNURL mergeSource : mergeSources) {
            mergeSourcesStrings.add(getUrlString(mergeSource));
        }
        return mergeSourcesStrings;
    }

    public void merge(String path1, Revision revision1, String path2,
            Revision revision2, String localPath, boolean force, Depth depth,
            boolean ignoreAncestry, boolean dryRun, boolean recordOnly)
            throws ClientException {
        SvnMerge merge = svnOperationFactory.createMerge();
        merge.setSources(getTarget(path1, revision1), getTarget(path2, revision2));
        merge.addTarget(getTarget(localPath));
        merge.setForce(force);
        merge.setDepth(getSVNDepth(depth));
        merge.setIgnoreAncestry(ignoreAncestry);
        merge.setDryRun(dryRun);
        merge.setRecordOnly(recordOnly);

        try {
            merge.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void merge(String path, Revision pegRevision,
            List<RevisionRange> revisions, String localPath, boolean force,
            Depth depth, boolean ignoreAncestry, boolean dryRun,
            boolean recordOnly) throws ClientException {
        SvnMerge merge = svnOperationFactory.createMerge();
        merge.setSource(getTarget(path, pegRevision), false/*reintegrate=false*/);
        for (RevisionRange revisionRange : revisions) {
            merge.addRevisionRange(getSvnRevisionRange(revisionRange));
        }
        merge.addTarget(getTarget(localPath));
        merge.setForce(force);
        merge.setDepth(getSVNDepth(depth));
        merge.setIgnoreAncestry(ignoreAncestry);
        merge.setDryRun(dryRun);
        merge.setRecordOnly(recordOnly);

        try {
            merge.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void mergeReintegrate(String path, Revision pegRevision,
            String localPath, boolean dryRun) throws ClientException {
        SvnMerge merge = svnOperationFactory.createMerge();
        merge.setSource(getTarget(path, pegRevision), true/*reintegrate=true*/);
        merge.addTarget(getTarget(localPath));
        merge.setDryRun(dryRun);

        try {
            merge.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public Mergeinfo getMergeinfo(String path, Revision pegRevision)
            throws SubversionException {

        SvnGetMergeInfo getMergeInfo = svnOperationFactory.createGetMergeInfo();
        getMergeInfo.setSingleTarget(getTarget(path, pegRevision));
        try {
            return getMergeinfo(getMergeInfo.run());
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void getMergeinfoLog(LogKind kind, String pathOrUrl,
            Revision pegRevision, String mergeSourceUrl,
            Revision srcPegRevision, boolean discoverChangedPaths, Depth depth,
            Set<String> revProps, LogMessageCallback callback)
            throws ClientException {
        SvnLogMergeInfo logMergeInfo = svnOperationFactory.createLogMergeInfo();
        logMergeInfo.setFindMerged(kind == LogKind.merged);
        logMergeInfo.setSingleTarget(getTarget(pathOrUrl, pegRevision));
        logMergeInfo.setSource(getTarget(mergeSourceUrl, srcPegRevision));
        logMergeInfo.setDiscoverChangedPaths(discoverChangedPaths);
        logMergeInfo.setDepth(getSVNDepth(depth));
        logMergeInfo.setRevisionProperties(getRevisionPropertiesNames(revProps));
        logMergeInfo.setReceiver(getLogEntryReceiver(callback));
        try {
            logMergeInfo.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void diff(String target1, Revision revision1, String target2,
            Revision revision2, String relativeToDir, String outFileName,
            Depth depth, Collection<String> changelists,
            boolean ignoreAncestry, boolean noDiffDeleted, boolean force,
            boolean copiesAsAdds) throws ClientException {
        FileOutputStream fileOutputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(outFileName);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

            SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(getTarget(target1, revision1), getTarget(target2, revision2));
            diff.setSingleTarget(getTarget(relativeToDir));
            diff.setOutput(bufferedOutputStream);
            diff.setDepth(getSVNDepth(depth));
            diff.setApplicalbeChangelists(changelists);
            diff.setIgnoreAncestry(ignoreAncestry);
            diff.setNoDiffDeleted(noDiffDeleted);
            diff.setIgnoreContentType(force);
            diff.setShowCopiesAsAdds(copiesAsAdds);
            diff.run();
        } catch (FileNotFoundException e) {
            throw ClientException.fromException(e);
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        } finally {
            SVNFileUtil.closeFile(fileOutputStream);
            SVNFileUtil.closeFile(bufferedOutputStream);
        }
    }

    public void diff(String target, Revision pegRevision,
            Revision startRevision, Revision endRevision, String relativeToDir,
            String outFileName, Depth depth, Collection<String> changelists,
            boolean ignoreAncestry, boolean noDiffDeleted, boolean force,
            boolean copiesAsAdds) throws ClientException {
        FileOutputStream fileOutputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(outFileName);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

            SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(getTarget(target, pegRevision), getSVNRevision(startRevision), getSVNRevision(endRevision));
            diff.setSingleTarget(getTarget(relativeToDir));
            diff.setOutput(bufferedOutputStream);
            diff.setDepth(getSVNDepth(depth));
            diff.setApplicalbeChangelists(changelists);
            diff.setIgnoreAncestry(ignoreAncestry);
            diff.setNoDiffDeleted(noDiffDeleted);
            diff.setIgnoreContentType(force);
            diff.setShowCopiesAsAdds(copiesAsAdds);
            diff.run();
        } catch (FileNotFoundException e) {
            throw ClientException.fromException(e);
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        } finally {
            SVNFileUtil.closeFile(fileOutputStream);
            SVNFileUtil.closeFile(bufferedOutputStream);
        }
    }

    public void diffSummarize(String target1, Revision revision1,
            String target2, Revision revision2, Depth depth,
            Collection<String> changelists, boolean ignoreAncestry,
            DiffSummaryCallback receiver) throws ClientException {
        SvnDiffSummarize diffSummarize = svnOperationFactory.createDiffSummarize();
        diffSummarize.setSources(getTarget(target1, revision1), getTarget(target2, revision2));
        diffSummarize.setDepth(getSVNDepth(depth));
        diffSummarize.setApplicalbeChangelists(changelists);
        diffSummarize.setIgnoreAncestry(ignoreAncestry);
        diffSummarize.setReceiver(getDiffStatusReceiver(receiver));

        try {
            diffSummarize.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void diffSummarize(String target, Revision pegRevision,
            Revision startRevision, Revision endRevision, Depth depth,
            Collection<String> changelists, boolean ignoreAncestry,
            DiffSummaryCallback receiver) throws ClientException {
        SvnDiffSummarize diffSummarize = svnOperationFactory.createDiffSummarize();
        diffSummarize.setSource(getTarget(target, pegRevision), getSVNRevision(startRevision), getSVNRevision(endRevision));
        diffSummarize.setDepth(getSVNDepth(depth));
        diffSummarize.setApplicalbeChangelists(changelists);
        diffSummarize.setIgnoreAncestry(ignoreAncestry);
        diffSummarize.setReceiver(getDiffStatusReceiver(receiver));
        try {
            diffSummarize.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void properties(String path, Revision revision,
            Revision pegRevision, Depth depth, Collection<String> changelists,
            ProplistCallback callback) throws ClientException {
        SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
        getProperties.setRevision(getSVNRevision(revision));
        getProperties.setDepth(getSVNDepth(depth));
        getProperties.setApplicalbeChangelists(changelists);
        getProperties.setReceiver(getSVNPropertiesReceiver(callback));

        getProperties.addTarget(getTarget(path, pegRevision));

        try {
            getProperties.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void propertySetLocal(Set<String> paths, String name, byte[] value,
            Depth depth, Collection<String> changelists, boolean force)
            throws ClientException {
        SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
        setProperty.setPropertyName(name);
        setProperty.setPropertyValue(SVNPropertyValue.create(name, value));
        setProperty.setDepth(getSVNDepth(depth));
        setProperty.setApplicalbeChangelists(changelists);
        setProperty.setForce(force);

        for (String path : paths) {
            setProperty.addTarget(getTarget(path));
        }

        try {
            setProperty.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void propertySetRemote(String path, long baseRev, String name,
            byte[] value, CommitMessageCallback handler, boolean force,
            Map<String, String> revpropTable, CommitCallback callback)
            throws ClientException {
        SvnRemoteSetProperty remoteSetProperty = svnOperationFactory.createRemoteSetProperty();
        remoteSetProperty.setSingleTarget(getTarget(path));
        remoteSetProperty.setRevision(SVNRevision.create(baseRev));
        remoteSetProperty.setPropertyName(name);
        remoteSetProperty.setPropertyValue(SVNPropertyValue.create(name, value));
        remoteSetProperty.setCommitHandler(getCommitHandler(handler));
        remoteSetProperty.setForce(force);
        remoteSetProperty.setRevisionProperties(getSVNProperties(revpropTable));
        remoteSetProperty.setReceiver(getCommitInfoReceiver(callback));
        try {
            remoteSetProperty.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public byte[] revProperty(String path, String name, Revision rev)
            throws ClientException {
        return getProperty(path, name, rev, null, true);
    }

    public Map<String, byte[]> revProperties(String path, Revision rev)
            throws ClientException {
        SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
        getProperties.setSingleTarget(getTarget(path));
        getProperties.setRevision(getSVNRevision(rev));
        getProperties.setRevisionProperties(true);

        final SVNProperties[] svnProperties = new SVNProperties[1];

        getProperties.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties properties) throws SVNException {
                svnProperties[0] = properties;
            }
        });

        try {
            getProperties.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }

        return getProperties(svnProperties[0]);
    }

    public void setRevProperty(String path, String name, Revision rev,
            String value, String originalValue, boolean force)
            throws ClientException {
        SvnRemoteSetProperty remoteSetProperty = svnOperationFactory.createRemoteSetProperty();
        remoteSetProperty.setSingleTarget(getTarget(path));
        remoteSetProperty.setPropertyName(name);
        remoteSetProperty.setRevision(getSVNRevision(rev));
        remoteSetProperty.setPropertyValue(SVNPropertyValue.create(value));
        remoteSetProperty.setOriginalPropertyValue(SVNPropertyValue.create(originalValue));
        remoteSetProperty.setForce(force);

        try {
            remoteSetProperty.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public byte[] propertyGet(String path, String name, Revision revision,
            Revision pegRevision) throws ClientException {
        return getProperty(path, name, revision, pegRevision, true);
    }

    public byte[] fileContent(String path, Revision revision,
            Revision pegRevision) throws ClientException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        streamFileContent(path, revision, pegRevision, byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    public void streamFileContent(String path, Revision revision,
            Revision pegRevision, OutputStream stream) throws ClientException {

        if (pegRevision == null) {
            pegRevision = SVNPathUtil.isURL(path) ? Revision.HEAD : Revision.WORKING;
            if (revision == null) {
                revision = SVNPathUtil.isURL(path) ? Revision.HEAD : Revision.BASE;
            }
        } else {
            if (revision == null) {
                revision = pegRevision;
            }
        }

        SvnCat cat = svnOperationFactory.createCat();
        cat.setSingleTarget(getTarget(path, pegRevision));
        cat.setRevision(getSVNRevision(revision));
        cat.setExpandKeywords(false);
        cat.setOutput(stream);

        try {
            cat.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void relocate(String from, String to, String path,
            boolean ignoreExternals) throws ClientException {
        try {
            SvnRelocate relocate = svnOperationFactory.createRelocate();
            relocate.setFromUrl(SVNURL.parseURIEncoded(from));
            relocate.setToUrl(SVNURL.parseURIEncoded(to));
            relocate.setSingleTarget(getTarget(path));
            relocate.setIgnoreExternals(ignoreExternals);
            relocate.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void blame(String path, Revision pegRevision,
            Revision revisionStart, Revision revisionEnd,
            boolean ignoreMimeType, boolean includeMergedRevisions,
            final BlameCallback callback) throws ClientException {
        final SvnAnnotate annotate = svnOperationFactory.createAnnotate();
        annotate.setSingleTarget(getTarget(path, pegRevision));
        annotate.setStartRevision(getSVNRevision(revisionStart));
        annotate.setEndRevision(getSVNRevision(revisionEnd));
        annotate.setIgnoreMimeType(ignoreMimeType);
        annotate.setUseMergeHistory(includeMergedRevisions);
        annotate.setReceiver(getAnnotateItemReceiver(callback));

        try {
            annotate.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void setConfigDirectory(String configDir) throws ClientException {
        this.configDir = configDir;
        updateSvnOperationsFactory();
    }

    public String getConfigDirectory() throws ClientException {
        return configDir;
    }

    public void cancelOperation() throws ClientException {
        cancelOperation = true;
    }

    public void addToChangelist(Set<String> paths, String changelist,
            Depth depth, Collection<String> changelists) throws ClientException {

        SvnSetChangelist setChangeList = svnOperationFactory.createSetChangelist();
        setChangeList.setChangelistName(changelist);
        setChangeList.setDepth(getSVNDepth(depth));
        setChangeList.setApplicalbeChangelists(changelists);
        setChangeList.setRemove(false);

        for (String path : paths) {
            setChangeList.addTarget(getTarget(path));
        }

        try {
            setChangeList.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void removeFromChangelists(Set<String> paths, Depth depth,
            Collection<String> changelists) throws ClientException {

        SvnSetChangelist setChangelist = svnOperationFactory.createSetChangelist();
        setChangelist.setDepth(getSVNDepth(depth));
        setChangelist.setApplicalbeChangelists(changelists);
        setChangelist.setRemove(true);

        for (String path : paths) {
            setChangelist.addTarget(getTarget(path));
        }

        try {
            setChangelist.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void getChangelists(String rootPath, Collection<String> changelists,
            Depth depth, final ChangelistCallback callback) throws ClientException {

        SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
        getInfo.setSingleTarget(getTarget(rootPath));
        getInfo.setApplicalbeChangelists(changelists);
        getInfo.setDepth(getSVNDepth(depth));
        if (callback != null) {
            getInfo.setReceiver(new ISvnObjectReceiver<SvnInfo>() {
                public void receive(SvnTarget target, SvnInfo svnInfo) throws SVNException {
                    SvnWorkingCopyInfo wcInfo = svnInfo.getWcInfo();
                    if (wcInfo != null) {
                        String path = getFilePath(wcInfo.getPath());
                        String changelist = wcInfo.getChangelist();
                        callback.doChangelist(path, changelist);
                    }
                }
            });
        }
        try {
            getInfo.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void lock(Set<String> path, String comment, boolean force)
            throws ClientException {
        SvnSetLock lock = svnOperationFactory.createSetLock();
        lock.setLockMessage(comment);
        lock.setStealLock(force);

        for (String targetPath : path) {
            lock.addTarget(getTarget(targetPath));
        }

        try {
            lock.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void unlock(Set<String> path, boolean force) throws ClientException {
        SvnUnlock unlock = svnOperationFactory.createUnlock();
        unlock.setBreakLock(force);

        for (String targetPath : path) {
            unlock.addTarget(getTarget(targetPath));
        }

        try {
            unlock.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void info2(String pathOrUrl, Revision revision,
            Revision pegRevision, Depth depth, Collection<String> changelists,
            InfoCallback callback) throws ClientException {
        SvnGetInfo info = svnOperationFactory.createGetInfo();
        info.setSingleTarget(getTarget(pathOrUrl, pegRevision));
        info.setRevision(getSVNRevision(revision));
        info.setDepth(getSVNDepth(depth));
        info.setApplicalbeChangelists(changelists);
        info.setReceiver(getInfoReceiver(callback));

        try {
            info.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public String getVersionInfo(String path, String trailUrl,
            boolean lastChanged) throws ClientException {

        // TODO Auto-generated method stub
        return null;
    }

    public void upgrade(String path) throws ClientException {
        SvnUpgrade upgrade = svnOperationFactory.createUpgrade();
        upgrade.setSingleTarget(getTarget(path));
        try {
            upgrade.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public void patch(String patchPath, String targetPath, boolean dryRun,
            int stripCount, boolean reverse, boolean ignoreWhitespace,
            boolean removeTempfiles, PatchCallback callback)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    private SVNDepth getSVNDepth(Depth depth) {
        switch (depth) {
            case empty:
                return SVNDepth.EMPTY;
            case exclude:
                return SVNDepth.EXCLUDE;
            case files:
                return SVNDepth.FILES;
            case immediates:
                return SVNDepth.IMMEDIATES;
            case infinity:
                return SVNDepth.INFINITY;
            default:
                return SVNDepth.UNKNOWN;
        }
    }

    private Depth getDepth(SVNDepth depth) {
        if (depth == SVNDepth.EMPTY) {
            return Depth.empty;
        } else if (depth == SVNDepth.EXCLUDE) {
            return Depth.exclude;
        } else if (depth == SVNDepth.FILES) {
            return Depth.files;
        } else if (depth == SVNDepth.IMMEDIATES) {
            return Depth.immediates;
        } else if (depth == SVNDepth.INFINITY) {
            return Depth.infinity;
        } else {
            return Depth.unknown;
        }
    }

    private static SVNStatusType combineStatus(SvnStatus status) {
        if (status.getNodeStatus() == SVNStatusType.STATUS_CONFLICTED) {
            if (!status.isVersioned() && status.isConflicted()) {
                return SVNStatusType.STATUS_MISSING;
            }
            return status.getTextStatus();
        } else if (status.getNodeStatus() == SVNStatusType.STATUS_MODIFIED) {
            return status.getTextStatus();
        }
        return status.getNodeStatus();
    }

    private Status getStatus(SvnStatus status) throws SVNException {
        String repositoryRelativePath = status.getRepositoryRelativePath() == null ? "" : status.getRepositoryRelativePath();
        SVNURL repositoryRootUrl = status.getRepositoryRootUrl();

        //TODO: repositoryRootUrl is currently null whatever 'remote' ('onServer') option is
        String itemUrl = repositoryRootUrl == null ? null : getUrlString(repositoryRootUrl.appendPath(repositoryRelativePath, false));

        return new Status(
                getFilePath(status.getPath()),
                itemUrl,
                getNodeKind(status.getKind()),
                status.getRevision(),
                status.getChangedRevision(),
                getLongDate(status.getChangedDate()),
                status.getChangedAuthor(),
                getStatusKind(combineStatus(status)),
                getStatusKind(status.getPropertiesStatus()),
                getStatusKind(status.getRepositoryTextStatus()),
                getStatusKind(status.getRepositoryPropertiesStatus()),
                status.isWcLocked(),
                status.isCopied(),
                status.isConflicted(),
                status.isSwitched(),
                status.isFileExternal(),
                getLock(status.getLock()),
                getLock(status.getRepositoryLock()),
                status.getRepositoryChangedRevision(),
                getLongDate(status.getRepositoryChangedDate()),
                getNodeKind(status.getRepositoryKind()),
                status.getRepositoryChangedAuthor(),
                status.getChangelist()
        );
    }

    private Lock getLock(SVNLock lock) {
        if (lock == null) {
            return null;
        }
        return new Lock(lock.getOwner(), lock.getPath(), lock.getID(), lock.getComment(), getLongDate(lock.getCreationDate()), getLongDate(lock.getExpirationDate()));
    }


    private long getLongDate(Date date) {
        SVNDate svnDate = SVNDate.fromDate(date);
        return svnDate.getTimeInMicros();
    }

    private SvnTarget getTarget(String path, Revision revision) {
        SVNRevision svnRevision = getSVNRevision(revision);

        if (SVNPathUtil.isURL(path)) {
            try {
                return SvnTarget.fromURL(SVNURL.parseURIEncoded(path), svnRevision);
            } catch (SVNException e) {
                //never happens if SVNPathUtil#isURL works correctly
                throw new IllegalArgumentException(e);
            }
        }
        return SvnTarget.fromFile(new File(path), svnRevision);
    }

    private SvnTarget getTarget(String path) {
        return getTarget(path, null);
    }

    private Status.Kind getStatusKind(SVNStatusType statusType) {
        if (statusType == SVNStatusType.STATUS_ADDED) {
            return Status.Kind.added;
        } else if (statusType == SVNStatusType.STATUS_CONFLICTED) {
            return Status.Kind.conflicted;
        } else if (statusType == SVNStatusType.STATUS_DELETED) {
            return Status.Kind.deleted;
        } else if (statusType == SVNStatusType.STATUS_EXTERNAL) {
            return Status.Kind.external;
        } else if (statusType == SVNStatusType.STATUS_IGNORED) {
            return Status.Kind.ignored;
        } else if (statusType == SVNStatusType.STATUS_INCOMPLETE) {
            return Status.Kind.incomplete;
        } else if (statusType == SVNStatusType.STATUS_MERGED) {
            return Status.Kind.merged;
        } else if (statusType == SVNStatusType.STATUS_MISSING) {
            return Status.Kind.missing;
        } else if (statusType == SVNStatusType.STATUS_MODIFIED) {
            return Status.Kind.modified;
        } else if (statusType == SVNStatusType.STATUS_NAME_CONFLICT) {
            //TODO: no analog in Status.Kind
            return null;
        } else if (statusType == SVNStatusType.STATUS_NONE) {
            return Status.Kind.none;
        } else if (statusType == SVNStatusType.STATUS_NORMAL) {
            return Status.Kind.normal;
        } else if (statusType == SVNStatusType.STATUS_OBSTRUCTED) {
            return Status.Kind.obstructed;
        } else if (statusType == SVNStatusType.STATUS_REPLACED) {
            return Status.Kind.replaced;
        } else if (statusType == SVNStatusType.STATUS_UNVERSIONED) {
            return Status.Kind.unversioned;
        } else {
            throw new IllegalArgumentException("Unknown status type: " + statusType);
        }
    }

    private NodeKind getNodeKind(SVNNodeKind kind) {
        if (kind == SVNNodeKind.DIR) {
            return NodeKind.dir;
        } else if (kind == SVNNodeKind.FILE) {
            return NodeKind.file;
        } else if (kind == SVNNodeKind.NONE) {
            return NodeKind.none;
        } else {
            return NodeKind.unknown;
        }
    }

    static SVNRevision getSVNRevision(Revision revision) {
        if (revision == null) {
            return SVNRevision.UNDEFINED;
        }
        switch (revision.getKind()) {
            case base:
                return SVNRevision.BASE;
            case committed:
                return SVNRevision.COMMITTED;
            case date:
                Revision.DateSpec dateSpec = (Revision.DateSpec) revision;
                return SVNRevision.create(dateSpec.getDate());
            case head:
                return SVNRevision.HEAD;
            case number:
                Revision.Number number = (Revision.Number) revision;
                return SVNRevision.create(number.getNumber());
            case previous:
                return SVNRevision.PREVIOUS;
            case working:
                return SVNRevision.WORKING;
            case unspecified:
            default:
                return SVNRevision.UNDEFINED;
        }
    }

    private SVNProperties getSVNProperties(Map<String, String> revpropTable) {
        return SVNProperties.wrap(revpropTable);
    }

    private CommitItem getSvnCommitItem(SvnCommitItem commitable) {
        if (commitable == null) {
            return null;
        }
        return new CommitItem(getFilePath(commitable.getPath()), getNodeKind(commitable.getKind()), commitable.getFlags(),
                getUrlString(commitable.getUrl()), getUrlString(commitable.getCopyFromUrl()),
                commitable.getCopyFromRevision());
    }

    private String getFilePath(File path) {
        if (path == null) {
            return null;
        }
        return path.getPath();
    }

    private String getUrlString(SVNURL url) {
        if (url == null) {
            return null;
        }
        return url.toString();
    }

    private ISvnObjectReceiver<SvnStatus> getStatusReceiver(final StatusCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SvnStatus>() {
            public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                callback.doStatus(getFilePath(target.getFile()), getStatus(status));
            }
        };
    }

    private ISvnCommitHandler getCommitHandler(final CommitMessageCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnCommitHandler() {
            public String getCommitMessage(String message, SvnCommitItem[] commitables) throws SVNException {
                Set<CommitItem> commitItems = new HashSet<CommitItem>();
                for (SvnCommitItem commitable : commitables) {
                    commitItems.add(getSvnCommitItem(commitable));
                }
                return callback.getLogMessage(commitItems);
            }

            public SVNProperties getRevisionProperties(String message, SvnCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
                return revisionProperties;
            }
        };
    }

    private Collection<SvnRevisionRange> getSvnRevisionRanges(List<RevisionRange> ranges) {
        if (ranges == null) {
            return null;
        }
        Collection<SvnRevisionRange> svnRevisionRanges = new ArrayList<SvnRevisionRange>(ranges.size());
        for (RevisionRange range : ranges) {
            svnRevisionRanges.add(getSvnRevisionRange(range));
        }
        return svnRevisionRanges;
    }

    private SvnRevisionRange getSvnRevisionRange(RevisionRange range) {
        return SvnRevisionRange.create(getSVNRevision(range.getFromRevision()), getSVNRevision(range.getToRevision()));
    }

    private String[] getRevisionPropertiesNames(Set<String> revProps) {
        if (revProps == null) {
            return null;
        }
        String[] revisionPropertiesNames = new String[revProps.size()];
        int i = 0;

        for (String revProp : revProps) {
            revisionPropertiesNames[i] = revProp;
            i++;
        }

        return revisionPropertiesNames;
    }

    private ISvnObjectReceiver<SVNLogEntry> getLogEntryReceiver(final LogMessageCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SVNLogEntry>() {
            public void receive(SvnTarget target, SVNLogEntry svnLogEntry) throws SVNException {
                callback.singleMessage(svnLogEntry.getChangedPaths().keySet(), svnLogEntry.getRevision(), getProperties(svnLogEntry.getRevisionProperties()), svnLogEntry.hasChildren());
            }
        };
    }

    private Map<String, byte[]> getProperties(SVNProperties svnProperties) {
        if (svnProperties == null) {
            return new HashMap<String, byte[]>();
        }
        HashMap<String, byte[]> properties = new HashMap<String, byte[]>();
        Set<String> svnPropertiesNames = svnProperties.nameSet();
        for (String svnPropertyName : svnPropertiesNames) {
            SVNPropertyValue svnPropertyValue = svnProperties.getSVNPropertyValue(svnPropertyName);
            properties.put(svnPropertyName, SVNPropertyValue.getPropertyAsBytes(svnPropertyValue));
        }
        return properties;
    }

    private ISvnObjectReceiver<SVNProperties> getSVNPropertiesReceiver(final ProplistCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties svnProperties) throws SVNException {
                callback.singlePath(getFilePath(target.getFile()), getProperties(svnProperties));
            }
        };
    }

    private SVNConflictChoice getSVNConflictChoice(Choice choice) {
        switch (choice) {
            case chooseBase:
                return SVNConflictChoice.BASE;
            case chooseMerged:
                return SVNConflictChoice.MERGED;
            case chooseMineConflict:
                return SVNConflictChoice.MINE_CONFLICT;
            case chooseMineFull:
                return SVNConflictChoice.MINE_FULL;
            case chooseTheirsConflict:
                return SVNConflictChoice.THEIRS_CONFLICT;
            case chooseTheirsFull:
                return SVNConflictChoice.THEIRS_FULL;
            case postpone:
                return SVNConflictChoice.POSTPONE;
            default:
                throw new IllegalArgumentException("Unknown choice kind: " + choice);
        }
    }

    private byte[] getProperty(String path, final String name, Revision rev, Revision pegRevision, boolean revisionProperties) throws ClientException {
        SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
        getProperties.setSingleTarget(getTarget(path, pegRevision));
        getProperties.setRevision(getSVNRevision(rev));
        getProperties.setRevisionProperties(revisionProperties);

        final SVNPropertyValue[] propertyValue = new SVNPropertyValue[1];
        getProperties.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties svnProperties) throws SVNException {
                propertyValue[0] = svnProperties.getSVNPropertyValue(name);
            }
        });

        try {
            getProperties.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }

        return SVNPropertyValue.getPropertyAsBytes(propertyValue[0]);
    }

    private DiffSummary getDiffSummary(SvnDiffStatus diffStatus) {
        return new DiffSummary(diffStatus.getPath(), getDiffKind(diffStatus.getModificationType()), diffStatus.isPropertiesModified(), getNodeKind(diffStatus.getKind()));
    }

    private DiffSummary.DiffKind getDiffKind(SVNStatusType type) {
        if (type == SVNStatusType.STATUS_ADDED) {
            return DiffSummary.DiffKind.added;
        } else if (type == SVNStatusType.STATUS_DELETED) {
            return DiffSummary.DiffKind.deleted;
        } else if (type == SVNStatusType.STATUS_MODIFIED) {
            return DiffSummary.DiffKind.modified;
        } else if (type == SVNStatusType.STATUS_NORMAL) {
            return DiffSummary.DiffKind.normal;
        } else {
            throw new IllegalArgumentException("Unknown status type: " + type);
        }
    }

    private ISvnObjectReceiver<SvnDiffStatus> getDiffStatusReceiver(final DiffSummaryCallback receiver) {
        if (receiver == null) {
            return null;
        }
        return new ISvnObjectReceiver<SvnDiffStatus>() {
            public void receive(SvnTarget target, SvnDiffStatus diffStatus) throws SVNException {
                receiver.onSummary(getDiffSummary(diffStatus));
            }
        };
    }

    private ISvnObjectReceiver<SVNCommitInfo> getCommitInfoReceiver(final CommitCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SVNCommitInfo>() {
            public void receive(SvnTarget target, SVNCommitInfo commitInfo) throws SVNException {
                try {
                    SVNURL repositoryRoot = target.getURL();

                    callback.commitInfo(getCommitInfo(commitInfo, repositoryRoot));
                } catch (ParseException e) {
                    throwSvnException(e);
                }
            }
        };
    }

    private void fillLocalAndRemoteTargets(Set<String> path, Set<String> localPaths, Set<String> remoteUrls) {
        for (String targetPath : path) {
            if (SVNPathUtil.isURL(targetPath)) {
                remoteUrls.add(targetPath);
            } else {
                localPaths.add(targetPath);
            }
        }
    }

    private void fillLocalAndRemoteSources(List<CopySource> sources, Set<CopySource> localSources, Set<CopySource> remoteSources) {
        for (CopySource source : sources) {
            if (SVNPathUtil.isURL(source.getPath())) {
                remoteSources.add(source);
            } else {
                localSources.add(source);
            }
        }
    }

    private CommitInfo getCommitInfo(SVNCommitInfo commitInfo, SVNURL repositoryRoot) throws ParseException {
        return new CommitInfo(commitInfo.getNewRevision(), SVNDate.formatDate(commitInfo.getDate()),
                commitInfo.getAuthor(), getErrorMessageString(commitInfo.getErrorMessage()), getUrlString(repositoryRoot));
    }

    private String getErrorMessageString(SVNErrorMessage errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.getMessage();
    }

    private SvnCopySource getSvnCopySource(CopySource localSource) {
        return SvnCopySource.create(getTarget(localSource.getPath(), localSource.getPegRevision()), getSVNRevision(localSource.getRevision()));
    }

    private void mkdirLocal(Set<String> localPaths, boolean makeParents) throws ClientException {
        if (localPaths == null || localPaths.size() == 0) {
            return;
        }
        SvnScheduleForAddition add = svnOperationFactory.createScheduleForAddition();
        add.setAddParents(makeParents);
        add.setMkDir(true);

        for (String localPath : localPaths) {
            add.addTarget(getTarget(localPath));
        }

        try {
            add.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void mkdirRemote(Set<String> remoteUrls, boolean makeParents,
                             Map<String, String> revpropTable, CommitMessageCallback handler,
                             final CommitCallback callback) throws ClientException {
        if (remoteUrls == null || remoteUrls.size() == 0) {
            return;
        }
        SvnRemoteMkDir mkdir = svnOperationFactory.createMkDir();
        mkdir.setMakeParents(makeParents);
        mkdir.setRevisionProperties(getSVNProperties(revpropTable));
        mkdir.setCommitHandler(getCommitHandler(handler));
        mkdir.setReceiver(getCommitInfoReceiver(callback));

        for (String remoteUrl : remoteUrls) {
            mkdir.addTarget(getTarget(remoteUrl));
        }

        try {
            mkdir.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void removeLocal(Set<String> localPaths, boolean force, boolean keepLocal) throws ClientException {
        if (localPaths == null || localPaths.size() == 0) {
            return;
        }
        SvnScheduleForRemoval remove = svnOperationFactory.createScheduleForRemoval();
        remove.setForce(force);
        remove.setDeleteFiles(!keepLocal);

        for (String localPath : localPaths) {
            remove.addTarget(getTarget(localPath));
        }

        try {
            remove.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void removeRemote(Set<String> remoteUrls,
                              Map<String, String> revpropTable, CommitMessageCallback handler,
                              CommitCallback callback) throws ClientException {
        if (remoteUrls == null || remoteUrls.size() == 0) {
            return;
        }
        SvnRemoteDelete remoteDelete = svnOperationFactory.createRemoteDelete();
        remoteDelete.setRevisionProperties(getSVNProperties(revpropTable));
        remoteDelete.setCommitHandler(getCommitHandler(handler));
        remoteDelete.setReceiver(getCommitInfoReceiver(callback));

        for (String remoteUrl : remoteUrls) {
            remoteDelete.addTarget(getTarget(remoteUrl));
        }

        try {
            remoteDelete.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void moveLocal(Set<String> localPaths, String destPath, boolean force,
                           boolean moveAsChild, boolean makeParents) throws ClientException {
        if (localPaths == null || localPaths.size() == 0) {
            return;
        }
        for (String localPath : localPaths) {
            moveLocalPath(localPath, destPath, force, moveAsChild, makeParents);
        }
    }

    private void moveLocalPath(String localPath, String destPath, boolean force, boolean moveAsChild, boolean makeParents) throws ClientException {
        if (moveAsChild) {
            destPath = SVNPathUtil.append(destPath, SVNPathUtil.tail(localPath));
        }

        SvnCopy copy = svnOperationFactory.createCopy();
        copy.setSingleTarget(getTarget(destPath));
        copy.setFailWhenDstExists(!force);//TODO: recheck
        copy.setMakeParents(makeParents);

        //TODO: copy should support 1) force

        copy.setMove(true);

        copy.addCopySource(SvnCopySource.create(getTarget(localPath), SVNRevision.UNDEFINED)); //TODO: recheck revision

        try {
            copy.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void moveRemote(Set<String> remoteUrls, String destPath, boolean force,
                            boolean moveAsChild, boolean makeParents,
                            Map<String, String> revpropTable, CommitMessageCallback handler,
                            CommitCallback callback) throws ClientException {
        if (remoteUrls == null || remoteUrls.size() == 0) {
            return;
        }
        SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
        remoteCopy.setSingleTarget(getTarget(destPath));
        remoteCopy.setFailWhenDstExists(!force);//TODO: recheck
        remoteCopy.setMakeParents(makeParents);
        remoteCopy.setRevisionProperties(getSVNProperties(revpropTable));
        remoteCopy.setCommitHandler(getCommitHandler(handler));
        remoteCopy.setReceiver(getCommitInfoReceiver(callback));
        remoteCopy.setMove(true);

        //TODO: remoteCopy lacks moveAsChild

        for (String remoteUrl : remoteUrls) {
            remoteCopy.addCopySource(SvnCopySource.create(getTarget(remoteUrl), SVNRevision.HEAD)); //TODO: check revision
        }

        try {
            remoteCopy.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void copyLocal(Set<CopySource> localSources, String destPath, boolean copyAsChild,
                           boolean makeParents, boolean ignoreExternals) throws ClientException {
        if (localSources == null || localSources.size() == 0) {
            return;
        }

        SvnCopy copy = svnOperationFactory.createCopy();
        copy.setSingleTarget(getTarget(destPath));
        copy.setMakeParents(makeParents);
        copy.setIgnoreExternals(ignoreExternals);
        copy.setMove(false);

        //TODO: copy lacks copyAsChild parameter

        for (CopySource localSource : localSources) {
            copy.addCopySource(getSvnCopySource(localSource));
        }

        try {
            copy.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private void copyRemote(Set<CopySource> remoteSources, String destPath, boolean copyAsChild,
                            boolean makeParents,
                            Map<String, String> revpropTable, CommitMessageCallback handler,
                            CommitCallback callback) throws ClientException {
        if (remoteSources == null || remoteSources.size() == 0) {
            return;
        }
        SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
        remoteCopy.setSingleTarget(getTarget(destPath));
        remoteCopy.setMakeParents(makeParents);
        remoteCopy.setRevisionProperties(getSVNProperties(revpropTable));
        remoteCopy.setCommitHandler(getCommitHandler(handler));
        remoteCopy.setReceiver(getCommitInfoReceiver(callback));
        remoteCopy.setMove(false);

        //TODO: remoteCopy lacks copyAsChild

        for (CopySource remoteSource : remoteSources) {
            remoteCopy.addCopySource(getSvnCopySource(remoteSource));
        }

        try {
            remoteCopy.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private ISvnObjectReceiver<SvnInfo> getInfoReceiver(final InfoCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SvnInfo>() {
            public void receive(SvnTarget target, SvnInfo info) throws SVNException {
                try {
                    callback.singleInfo(getInfo(info));
                } catch (ClientException e) {
                    throwSvnException(e);
                }
            }
        };
    }

    private Info getInfo(SvnInfo info) throws ClientException {
        String url = getUrlString(info.getUrl());
        String repositoryRoot = getUrlString(info.getRepositoryRootUrl());
        String path = SVNPathUtil.getRelativePath(repositoryRoot, url);
        boolean hasWcInfo = info.getWcInfo() != null;

        return new Info(path,
                hasWcInfo ? getFilePath(info.getWcInfo().getWcRoot()) : null,
                url,
                info.getRevision(),
                getNodeKind(info.getKind()),
                repositoryRoot,
                info.getRepositoryUuid(),
                info.getLastChangedRevision(),
                getLongDate(info.getLastChangedDate()),
                info.getLastChangedAuthor(),
                getLock(info.getLock()),
                hasWcInfo,
                hasWcInfo ? getScheduleKind(info.getWcInfo().getSchedule()) : null,
                hasWcInfo ? getUrlString(info.getWcInfo().getCopyFromUrl()) : null,
                hasWcInfo ? info.getWcInfo().getCopyFromRevision() : null,
                hasWcInfo ? info.getWcInfo().getRecordedTime() : null,
                hasWcInfo ? getChecksum(info.getWcInfo().getChecksum()) : null,
                hasWcInfo ? info.getWcInfo().getChangelist() : null,
                hasWcInfo ? info.getWcInfo().getRecordedSize() : null,
                info.getSize(),
                hasWcInfo ? getDepth(info.getWcInfo().getDepth()) : null,
                hasWcInfo ? getConflictDescriptors(info.getWcInfo().getConflicts()) : null
                );
    }

    private Set<ConflictDescriptor> getConflictDescriptors(Collection<SVNConflictDescription> conflicts) throws ClientException {
        HashSet<ConflictDescriptor> conflictDescriptors = new HashSet<ConflictDescriptor>();
        for (SVNConflictDescription conflict : conflicts) {
            conflictDescriptors.add(getConflictDescription(conflict));
        }
        return conflictDescriptors;
    }

    private Checksum getChecksum(SvnChecksum checksum) {
        if (checksum == null) {
            return null;
        }
        return new Checksum(checksum.getDigest().getBytes(), getChecksumKind(checksum.getKind()));
    }

    private Checksum.Kind getChecksumKind(SvnChecksum.Kind kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case md5:
                return Checksum.Kind.MD5;
            case sha1:
                return Checksum.Kind.SHA1;
            default:
                throw new IllegalArgumentException("Unsupported checksum kind: " + kind);
        }
    }

    private Info.ScheduleKind getScheduleKind(SvnSchedule schedule) {
        if (schedule == SvnSchedule.ADD) {
            return Info.ScheduleKind.add;
        } else if (schedule == SvnSchedule.DELETE) {
            return Info.ScheduleKind.delete;
        } else if (schedule == SvnSchedule.NORMAL) {
            return Info.ScheduleKind.normal;
        } else if (schedule == SvnSchedule.REPLACE) {
            return Info.ScheduleKind.replace;
        } else {
            throw new IllegalArgumentException("Unknown schedule kind: " + schedule);
        }
    }

    private Mergeinfo getMergeinfo(Map<SVNURL, SVNMergeRangeList> mergeInfoMap) {
        Mergeinfo mergeinfo = new Mergeinfo();
        for (Map.Entry<SVNURL, SVNMergeRangeList> entry : mergeInfoMap.entrySet()) {
            SVNURL url = entry.getKey();
            SVNMergeRangeList mergeRangeList = entry.getValue();

            if (mergeRangeList == null) {
                continue;
            }

            String urlString = getUrlString(url);

            SVNMergeRange[] ranges = mergeRangeList.getRanges();
            for (SVNMergeRange range : ranges) {
                if (range == null) {
                    continue;
                }
                mergeinfo.addRevisionRange(urlString, getRevisionRange(range));
            }
        }
        return mergeinfo;
    }

    private RevisionRange getRevisionRange(SVNMergeRange revisionRange) {
        if (revisionRange == null) {
            return null;
        }
        long startRevision = revisionRange.getStartRevision();
        long endRevision = revisionRange.getEndRevision();

        return new RevisionRange(Revision.getInstance(startRevision), Revision.getInstance(endRevision));
    }

    private ISvnObjectReceiver<SvnAnnotateItem> getAnnotateItemReceiver(final BlameCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SvnAnnotateItem>() {
            public void receive(SvnTarget target, SvnAnnotateItem annotateItem) throws SVNException {
                try {
                    if (annotateItem.isLine()) {
                        callback.singleLine(annotateItem.getLineNumber(),
                                annotateItem.getRevision(),
                                getRevisionProperties(annotateItem.getAuthor(), annotateItem.getDate()),
                                annotateItem.getMergedRevision(),
                                getRevisionProperties(annotateItem.getMergedAuthor(), annotateItem.getMergedDate()),
                                annotateItem.getMergedPath(),
                                annotateItem.getLine(),
                                !SVNRevision.isValidRevisionNumber(annotateItem.getRevision()));
                    }

                } catch (ClientException e) {
                    throwSvnException(e);
                }
            }
        };
    }

    private Map<String, byte[]> getRevisionProperties(String author, Date date) {
        if (author == null && date == null) {
            return null;
        }
        SVNPropertyValue authorPropertyValue = SVNPropertyValue.create(author);
        SVNPropertyValue datePropertyValue = SVNPropertyValue.create(SVNDate.formatDate(date));

        HashMap<String, byte[]> revisionProperties = new HashMap<String, byte[]>();
        revisionProperties.put(SVNRevisionProperty.AUTHOR, SVNPropertyValue.getPropertyAsBytes(authorPropertyValue));
        revisionProperties.put(SVNRevisionProperty.DATE, SVNPropertyValue.getPropertyAsBytes(datePropertyValue));
        return revisionProperties;
    }

    private ISVNConflictHandler getConflictHandler(final ConflictResolverCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISVNConflictHandler() {
            public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
                try {
                    return getSVNConflictResult(callback.resolve(getConflictDescription(conflictDescription)));
                } catch (SubversionException e) {
                    throwSvnException(e);
                    return null;
                }
            }
        };
    }

    private ConflictDescriptor getConflictDescription(SVNConflictDescription conflictDescription) throws ClientException {
        ConflictVersion srcLeft = null;
        ConflictVersion srcRight = null;
        ConflictDescriptor.Operation operation = null;

        if (conflictDescription instanceof SVNTreeConflictDescription) {
            SVNTreeConflictDescription treeConflictDescription = (SVNTreeConflictDescription) conflictDescription;

            srcLeft = getConflictVersion(treeConflictDescription.getSourceLeftVersion());
            srcRight = getConflictVersion(treeConflictDescription.getSourceRightVersion());

            operation = getConflictDescriptorOperation(treeConflictDescription.getOperation());
        }

        try {
            return new ConflictDescriptor(
                    getFilePath(conflictDescription.getPath()),
                    getConflictDescriptorKind(conflictDescription),
                    getNodeKind(conflictDescription.getNodeKind()),
                    conflictDescription.getPropertyName(),
                    conflictDescription.getMergeFiles().isBinary(),
                    conflictDescription.getMergeFiles().getMimeType(),
                    getConflictDescriptorAction(conflictDescription.getConflictAction()),
                    getConflictDescriptorReason(conflictDescription.getConflictReason()),
                    operation,
                    conflictDescription.getMergeFiles().getBasePath(),
                    conflictDescription.getMergeFiles().getRepositoryPath(),
                    conflictDescription.getMergeFiles().getLocalPath(),
                    conflictDescription.getMergeFiles().getResultPath(),
                    srcLeft,
                    srcRight
                    );
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    private ConflictDescriptor.Operation getConflictDescriptorOperation(SVNOperation operation) {
        if (operation == null) {
            return null;
        }
        if (operation == SVNOperation.MERGE) {
            return ConflictDescriptor.Operation.merge;
        } else if (operation == SVNOperation.UPDATE) {
            return ConflictDescriptor.Operation.update;
        } else if (operation == SVNOperation.SWITCH) {
            return ConflictDescriptor.Operation.switched;
        } else if (operation == SVNOperation.NONE) {
            return ConflictDescriptor.Operation.none;
        } else {
            throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private ConflictVersion getConflictVersion(SVNConflictVersion conflictVersion) {
        if (conflictVersion == null) {
            return null;
        }
        return new ConflictVersion(getUrlString(conflictVersion.getRepositoryRoot()),
                conflictVersion.getPegRevision(),
                conflictVersion.getPath(),
                getNodeKind(conflictVersion.getKind()));
    }

    private SVNConflictResult getSVNConflictResult(ConflictResult conflictResult) {
        return new SVNConflictResult(getSVNConflictChoice(conflictResult.getChoice()), getFile(conflictResult.getMergedPath()));
    }

    private ConflictDescriptor.Action getConflictDescriptorAction(SVNConflictAction conflictAction) {
        if (conflictAction == null) {
            return null;
        }
        if (conflictAction == SVNConflictAction.ADD) {
            return ConflictDescriptor.Action.add;
        } else if (conflictAction == SVNConflictAction.DELETE) {
            return ConflictDescriptor.Action.delete;
        } else if (conflictAction == SVNConflictAction.EDIT) {
            return ConflictDescriptor.Action.edit;
        } else if (conflictAction == SVNConflictAction.REPLACE) {
            //TODO: unsupported
            throw new IllegalArgumentException("Unknown conflict action: " + conflictAction);
        } else {
            throw new IllegalArgumentException("Unknown conflict action: " + conflictAction);
        }
    }

    private ConflictDescriptor.Reason getConflictDescriptorReason(SVNConflictReason conflictReason) {
        if (conflictReason == null) {
            return null;
        }
        if (conflictReason == SVNConflictReason.ADDED) {
            return ConflictDescriptor.Reason.added;
        } else if (conflictReason == SVNConflictReason.DELETED) {
            return ConflictDescriptor.Reason.deleted;
        } else if (conflictReason == SVNConflictReason.EDITED) {
            return ConflictDescriptor.Reason.edited;
        } else if (conflictReason == SVNConflictReason.MISSING) {
            return ConflictDescriptor.Reason.missing;
        } else if (conflictReason == SVNConflictReason.OBSTRUCTED) {
            return ConflictDescriptor.Reason.obstructed;
        } else if (conflictReason == SVNConflictReason.REPLACED) {
            throw new IllegalArgumentException("Unknown conflict reason: " + conflictReason);
        } else if (conflictReason == SVNConflictReason.UNVERSIONED) {
            return ConflictDescriptor.Reason.unversioned;
        } else {
            throw new IllegalArgumentException("Unknown conflict reason: " + conflictReason);
        }
    }

    private ClientNotifyInformation.Action getClientNotifyInformationAction(SVNEventAction action) {
        if (action == null) {
            return null;
        }
        if (action == SVNEventAction.ADD) {
            return ClientNotifyInformation.Action.add;
        } else if (action == SVNEventAction.ANNOTATE) {
            return ClientNotifyInformation.Action.blame_revision;
        } else if (action == SVNEventAction.CHANGELIST_CLEAR) {
            return ClientNotifyInformation.Action.changelist_clear;
        } else if (action == SVNEventAction.CHANGELIST_MOVED) {
            return ClientNotifyInformation.Action.changelist_moved;
        } else if (action == SVNEventAction.CHANGELIST_SET) {
            return ClientNotifyInformation.Action.changelist_set;
        } else if (action == SVNEventAction.COMMIT_ADDED) {
            return ClientNotifyInformation.Action.commit_added;
        } else if (action == SVNEventAction.COMMIT_COMPLETED) {
            //TODO: no analog
            return null;
        } else if (action == SVNEventAction.COMMIT_DELETED) {
            return ClientNotifyInformation.Action.commit_deleted;
        } else if (action == SVNEventAction.COMMIT_DELTA_SENT) {
            //TODO: check
            return ClientNotifyInformation.Action.commit_postfix_txdelta;
        } else if (action == SVNEventAction.COMMIT_REPLACED) {
            return ClientNotifyInformation.Action.commit_replaced;
        } else if (action == SVNEventAction.COMMIT_MODIFIED) {
            return ClientNotifyInformation.Action.commit_modified;
        } else if (action == SVNEventAction.COPY) {
            return ClientNotifyInformation.Action.copy;
        } else if (action == SVNEventAction.DELETE) {
            return ClientNotifyInformation.Action.delete;
        } else if (action == SVNEventAction.FAILED_EXTERNAL) {
            return ClientNotifyInformation.Action.failed_external;
        } else if (action == SVNEventAction.FAILED_REVERT) {
            return ClientNotifyInformation.Action.failed_revert;
        } else if (action == SVNEventAction.FOREIGN_MERGE_BEGIN) {
            return ClientNotifyInformation.Action.foreign_merge_begin;
        } else if (action == SVNEventAction.LOCK_FAILED) {
            return ClientNotifyInformation.Action.failed_lock;
        } else if (action == SVNEventAction.LOCKED) {
            return ClientNotifyInformation.Action.locked;
        } else if (action == SVNEventAction.MERGE_BEGIN) {
            return ClientNotifyInformation.Action.merge_begin;
        } else if (action == SVNEventAction.MERGE_COMPLETE) {
            return ClientNotifyInformation.Action.merge_completed;
        } else if (action == SVNEventAction.PATCH) {
            return ClientNotifyInformation.Action.patch;
        } else if (action == SVNEventAction.PATCH_APPLIED_HUNK) {
            return ClientNotifyInformation.Action.patch_applied_hunk;
        } else if (action == SVNEventAction.PATCH_REJECTED_HUNK) {
            return ClientNotifyInformation.Action.patch_rejected_hunk;
        } else if (action == SVNEventAction.PROGRESS) {
            //TODO: no analog
            return null;
        } else if (action == SVNEventAction.PROPERTY_ADD) {
            return ClientNotifyInformation.Action.property_added;
        } else if (action == SVNEventAction.PROPERTY_DELETE) {
            return ClientNotifyInformation.Action.property_deleted;
        } else if (action == SVNEventAction.PROPERTY_DELETE_NONEXISTENT) {
            return ClientNotifyInformation.Action.property_deleted_nonexistent;
        } else if (action == SVNEventAction.PROPERTY_MODIFY) {
            return ClientNotifyInformation.Action.property_modified;
        } else if (action == SVNEventAction.RESOLVED) {
            return ClientNotifyInformation.Action.resolved;
        } else if (action == SVNEventAction.RESTORE) {
            return ClientNotifyInformation.Action.restore;
        } else if (action == SVNEventAction.REVERT) {
            return ClientNotifyInformation.Action.revert;
        } else if (action == SVNEventAction.REVPROP_DELETE) {
            return ClientNotifyInformation.Action.revprop_deleted;
        } else if (action == SVNEventAction.REVPROPER_SET) {
            return ClientNotifyInformation.Action.revprop_set;
        } else if (action == SVNEventAction.SKIP) {
            return ClientNotifyInformation.Action.skip;
        } else if (action == SVNEventAction.SKIP_CONFLICTED) {
            return ClientNotifyInformation.Action.skip_conflicted;
        } else if (action == SVNEventAction.STATUS_COMPLETED) {
            return ClientNotifyInformation.Action.status_completed;
        } else if (action == SVNEventAction.STATUS_EXTERNAL) {
            return ClientNotifyInformation.Action.status_external;
        } else if (action == SVNEventAction.TREE_CONFLICT) {
            return ClientNotifyInformation.Action.tree_conflict;
        } else if (action == SVNEventAction.UNLOCK_FAILED) {
            return ClientNotifyInformation.Action.failed_unlock;
        } else if (action == SVNEventAction.UNLOCKED) {
            return ClientNotifyInformation.Action.unlocked;
        } else if (action == SVNEventAction.UPDATE_ADD) {
            return ClientNotifyInformation.Action.update_add;
        } else if (action == SVNEventAction.UPDATE_COMPLETED) {
            return ClientNotifyInformation.Action.update_completed;
        } else if (action == SVNEventAction.UPDATE_DELETE) {
            return ClientNotifyInformation.Action.update_delete;
        } else if (action == SVNEventAction.UPDATE_EXISTS) {
            return ClientNotifyInformation.Action.exists;
        } else if (action == SVNEventAction.UPDATE_EXTERNAL) {
            return ClientNotifyInformation.Action.update_external;
        } else if (action == SVNEventAction.UPDATE_EXTERNAL_REMOVED) {
            return ClientNotifyInformation.Action.update_external_removed;
        } else if (action == SVNEventAction.UPDATE_NONE) {
            //TODO: no analog
            return null;
        } else if (action == SVNEventAction.UPDATE_REPLACE) {
            return ClientNotifyInformation.Action.update_replaced;
        } else if (action == SVNEventAction.UPDATE_SHADOWED_ADD) {
            return ClientNotifyInformation.Action.update_shadowed_add;
        } else if (action == SVNEventAction.UPDATE_SHADOWED_DELETE) {
            return ClientNotifyInformation.Action.update_shadowed_delete;
        } else if (action == SVNEventAction.UPDATE_SHADOWED_UPDATE) {
            return ClientNotifyInformation.Action.update_shadowed_update;
        } else if (action == SVNEventAction.UPDATE_SKIP_ACCESS_DENINED) {
            return ClientNotifyInformation.Action.update_skip_access_denied;
        } else if (action == SVNEventAction.UPDATE_SKIP_OBSTRUCTION) {
            return ClientNotifyInformation.Action.update_skip_obstruction;
        } else if (action == SVNEventAction.UPDATE_SKIP_WORKING_ONLY) {
            return ClientNotifyInformation.Action.update_skip_working_only;
        } else if (action == SVNEventAction.UPDATE_STARTED) {
            return ClientNotifyInformation.Action.update_started;
        } else if (action == SVNEventAction.UPDATE_UPDATE) {
            return ClientNotifyInformation.Action.update_update;
        } else if (action == SVNEventAction.UPGRADE) {
            //TODO: check
            return ClientNotifyInformation.Action.upgraded_path;
        } else if (action == SVNEventAction.WC_PATH_NONEXISTENT) {
            return ClientNotifyInformation.Action.path_nonexistent;
        } else {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    private ConflictDescriptor.Kind getConflictDescriptorKind(SVNConflictDescription conflictDescription) {
        if (conflictDescription == null) {
            return null;
        }
        if (conflictDescription.isTextConflict()) {
            return ConflictDescriptor.Kind.text;
        } else if (conflictDescription.isPropertyConflict()) {
            return ConflictDescriptor.Kind.property;
        } else if (conflictDescription.isTreeConflict()) {
            return ConflictDescriptor.Kind.tree;
        } else {
            throw new IllegalArgumentException("Unknown conflict kind: " + conflictDescription);
        }
    }

    private File getFile(String path) {
        if (path == null) {
            return null;
        }
        return new File(path);
    }

    private ISvnObjectReceiver<SVNDirEntry> getDirEntryReceiver(final ListCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SVNDirEntry>() {
            public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                callback.doEntry(getDirEntry(dirEntry), getLock(dirEntry.getLock()));
            }
        };
    }

    private DirEntry getDirEntry(SVNDirEntry dirEntry) {
        if (dirEntry == null) {
            return null;
        }
        String repositoryRootString = getUrlString(dirEntry.getRepositoryRoot());
        String urlString = getUrlString(dirEntry.getURL());
        String absolutePath = SVNPathUtil.getRelativePath(repositoryRootString, urlString);

        return new DirEntry(dirEntry.getRelativePath(), absolutePath, getNodeKind(dirEntry.getKind()), dirEntry.getSize(),
                dirEntry.hasProperties(), dirEntry.getRevision(), getLongDate(dirEntry.getDate()), dirEntry.getAuthor());
    }

    private ClientNotifyInformation getClientNotifyInformation(SVNEvent event, String path) {
        //TODO: initialize these variables:
        String pathPrefix = null;
        String propName = null;
        Map<String, String> revProps = null;


        long hunkOriginalStart = -1;
        long hunkOriginalLength = -1;
        long hunkModifiedStart = -1;
        long hunkModifiedLength = -1;
        long hunkMatchedLine = -1;
        int hunkFuzz = -1;

        Object info = event.getInfo();
        if (info != null && info instanceof SVNPatchHunkInfo) {
            SVNPatchHunkInfo hunkInfo = (SVNPatchHunkInfo) info;
            hunkOriginalStart = hunkInfo.getHunk().getOriginal().getStart();
            hunkOriginalLength = hunkInfo.getHunk().getOriginal().getLength();
            hunkModifiedStart = hunkInfo.getHunk().getModified().getStart();
            hunkModifiedLength = hunkInfo.getHunk().getModified().getLength();
            hunkFuzz = hunkInfo.getFuzz();
        }

        return new ClientNotifyInformation(path,
                getClientNotifyInformationAction(event.getAction()),
                getNodeKind(event.getNodeKind()),
                event.getMimeType(),
                getLock(event.getLock()),
                getErrorMessageString(event.getErrorMessage()),
                getClientNotifyInformationStatus(event.getContentsStatus()),
                getClientNotifyInformationStatus(event.getPropertiesStatus()),
                getClientNotifyInformationLockStatus(event.getLockStatus()),
                event.getRevision(),
                event.getChangelistName(),
                getRevisionRange(event.getMergeRange()),
                pathPrefix,
                propName,
                revProps,
                event.getPreviousRevision(),
                hunkOriginalStart,
                hunkOriginalLength,
                hunkModifiedStart,
                hunkModifiedLength,
                hunkMatchedLine,
                hunkFuzz
        );
    }

    private ClientNotifyInformation.LockStatus getClientNotifyInformationLockStatus(SVNStatusType lockStatus) {
        if (lockStatus == null) {
            return null;
        }
        if (lockStatus == SVNStatusType.LOCK_LOCKED) {
            return ClientNotifyInformation.LockStatus.locked;
        } else if (lockStatus == SVNStatusType.LOCK_INAPPLICABLE) {
            return ClientNotifyInformation.LockStatus.inapplicable;
        } else if (lockStatus == SVNStatusType.LOCK_UNCHANGED) {
            return ClientNotifyInformation.LockStatus.unchanged;
        } else if (lockStatus == SVNStatusType.LOCK_UNKNOWN) {
            return ClientNotifyInformation.LockStatus.unknown;
        } else if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
            return ClientNotifyInformation.LockStatus.unlocked;
        } else if (lockStatus == SVNStatusType.INAPPLICABLE) {
            //TODO: should this ever happen? but it happens actually
            return ClientNotifyInformation.LockStatus.inapplicable;
        } else {
            throw new IllegalArgumentException("Unknown lock status: " + lockStatus);
        }
    }

    private ClientNotifyInformation.Status getClientNotifyInformationStatus(SVNStatusType status) {
        if (status == null) {
            return null;
        }
        if (status == SVNStatusType.CHANGED) {
            return ClientNotifyInformation.Status.changed;
        } else if (status == SVNStatusType.CONFLICTED) {
            return ClientNotifyInformation.Status.conflicted;
        } else if (status == SVNStatusType.CONFLICTED_UNRESOLVED) {
            //TODO: no explicit analog
            return ClientNotifyInformation.Status.conflicted;
        } else if (status == SVNStatusType.INAPPLICABLE) {
            return ClientNotifyInformation.Status.inapplicable;
        } else if (status == SVNStatusType.MERGED) {
            return ClientNotifyInformation.Status.merged;
        } else if (status == SVNStatusType.MISSING) {
            return ClientNotifyInformation.Status.missing;
        } else if (status == SVNStatusType.OBSTRUCTED) {
            return ClientNotifyInformation.Status.obstructed;
        } else if (status == SVNStatusType.UNCHANGED) {
            return ClientNotifyInformation.Status.unchanged;
        } else if (status == SVNStatusType.UNKNOWN) {
            return ClientNotifyInformation.Status.unknown;
        } else {
            throw new IllegalArgumentException("Unknown status type: " + status);
        }
    }

    private void updateSvnOperationsFactory() {
        File configDir = this.configDir == null ? null : new File(this.configDir);
        options = SVNWCUtil.createDefaultOptions(configDir, true);
        options.setConflictHandler(conflictHandler);
        authenticationManager = SVNWCUtil.createDefaultAuthenticationManager(configDir, username, password, options.isAuthStorageEnabled());
        if (prompt != null) {
            authenticationManager.setAuthenticationProvider(new JavaHLAuthenticationProvider(prompt));
        } else {
            authenticationManager.setAuthenticationProvider(null);
        }
        if (authenticationManager instanceof DefaultSVNAuthenticationManager) {
            //TODO: implement
//            ((DefaultSVNAuthenticationManager) authenticationManager).setRuntimeStorage(getClientCredentialsStorage());
        }
        if (svnOperationFactory != null) {
            svnOperationFactory.setAuthenticationManager(authenticationManager);
            svnOperationFactory.setOptions(options);
            svnOperationFactory.setEventHandler(getEventHandler());
        }
    }

    static String versionString() {
        return org.tmatesoft.svn.util.Version.getVersionString();
    }

    static int versionMajor() {
        return org.tmatesoft.svn.util.Version.getMajorVersion();
    }

    static int versionMinor() {
        return org.tmatesoft.svn.util.Version.getMinorVersion();
    }

    static int versionMicro() {
        return org.tmatesoft.svn.util.Version.getMicroVersion();
    }

    static long versionRevisionNumber() {
        return org.tmatesoft.svn.util.Version.getRevisionNumber();
    }

    private void throwSvnException(Exception e) throws SVNException {
        //TODO: review
        SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
        SVNErrorManager.error(errorMessage, e, SVNLogType.CLIENT);
    }
}
