package org.tmatesoft.svn.core.javahl17;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.CommitItem;
import org.apache.subversion.javahl.ConflictResult.Choice;
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
import org.apache.subversion.javahl.types.CopySource;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Mergeinfo;
import org.apache.subversion.javahl.types.Mergeinfo.LogKind;
import org.apache.subversion.javahl.types.NodeKind;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.RevisionRange;
import org.apache.subversion.javahl.types.Status;
import org.apache.subversion.javahl.types.Version;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import org.tmatesoft.svn.core.wc2.hooks.ISvnCommitHandler;

public class SVNClientImpl implements ISVNClient {

    private SvnOperationFactory svnOperationFactory;

    protected SVNClientImpl() {
        this(null);
    }

    protected SVNClientImpl(SvnOperationFactory svnOperationFactory) {
        this.svnOperationFactory = svnOperationFactory == null ? new SvnOperationFactory() : svnOperationFactory;
    }

    public void dispose() {
    }

    public Version getVersion() {
        return null;
    }

    public String getAdminDirectoryName() {
        return null;
    }

    public boolean isAdminDirectory(String name) {
        return false;
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
        // TODO Auto-generated method stub

    }

    public void username(String username) {
        // TODO Auto-generated method stub

    }

    public void password(String password) {
        // TODO Auto-generated method stub

    }

    public void setPrompt(UserPasswordCallback prompt) {
        // TODO Auto-generated method stub

    }

    public void logMessages(String path, Revision pegRevision,
            List<RevisionRange> ranges, boolean stopOnCopy,
            boolean discoverPath, boolean includeMergedRevisions,
            Set<String> revProps, long limit, LogMessageCallback callback)
            throws ClientException {
        SvnLog log = svnOperationFactory.createLog();
        log.setRevision(getSVNRevision(pegRevision));
        log.setRevisionRanges(getSvnRevisionRanges(ranges));
        log.setStopOnCopy(stopOnCopy);
        log.setDiscoverChangedPaths(discoverPath);
        log.setUseMergeHistory(includeMergedRevisions);
        log.setRevisionProperties(getRevisionPropertiesNames(revProps));
        log.setLimit(limit);
        log.setReceiver(getLogEntryReceiver(callback));

        log.addTarget(getTarget(path));

        try {
            log.run();
        } catch (SVNException e) {
            throw ClientException.fromException(e);
        }
    }

    public long checkout(String moduleName, String destPath, Revision revision,
            Revision pegRevision, Depth depth, boolean ignoreExternals,
            boolean allowUnverObstructions) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public void notification2(ClientNotifyCallback notify) {
        // TODO Auto-generated method stub

    }

    public void setConflictResolver(ConflictResolverCallback listener) {
        // TODO Auto-generated method stub

    }

    public void setProgressCallback(ProgressCallback listener) {
        // TODO Auto-generated method stub

    }

    public void remove(Set<String> path, boolean force, boolean keepLocal,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void revert(String path, Depth depth, Collection<String> changelists)
            throws ClientException {
        // TODO Auto-generated method stub
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
        // TODO Auto-generated method stub

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
        // TODO Auto-generated method stub
        SvnCommit commit = svnOperationFactory.createCommit();
        commit.setDepth(getSVNDepth(depth));
        commit.setKeepLocks(!noUnlock);
        commit.setKeepChangelists(keepChangelist);
        commit.setApplicalbeChangelists(changelists);
        commit.setRevisionProperties(getSVNProperties(revpropTable));
        commit.setCommitHandler(getCommitHandler(handler));

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
        // TODO Auto-generated method stub

    }

    public void move(Set<String> srcPaths, String destPath, boolean force,
            boolean moveAsChild, boolean makeParents,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void mkdir(Set<String> path, boolean makeParents,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void cleanup(String path) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void resolve(String path, Depth depth, Choice conflictResult)
            throws SubversionException {
        // TODO Auto-generated method stub

    }

    public long doExport(String srcPath, String destPath, Revision revision,
            Revision pegRevision, boolean force, boolean ignoreExternals,
            Depth depth, String nativeEOL) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long doSwitch(String path, String url, Revision revision,
            Revision pegRevision, Depth depth, boolean depthIsSticky,
            boolean ignoreExternals, boolean allowUnverObstructions,
            boolean ignoreAncestry) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public void doImport(String path, String url, Depth depth,
            boolean noIgnore, boolean ignoreUnknownNodeTypes,
            Map<String, String> revpropTable, CommitMessageCallback handler,
            CommitCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public Set<String> suggestMergeSources(String path, Revision pegRevision)
            throws SubversionException {
        // TODO Auto-generated method stub
        return null;
    }

    public void merge(String path1, Revision revision1, String path2,
            Revision revision2, String localPath, boolean force, Depth depth,
            boolean ignoreAncestry, boolean dryRun, boolean recordOnly)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public void merge(String path, Revision pegRevision,
            List<RevisionRange> revisions, String localPath, boolean force,
            Depth depth, boolean ignoreAncestry, boolean dryRun,
            boolean recordOnly) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void mergeReintegrate(String path, Revision pegRevision,
            String localPath, boolean dryRun) throws ClientException {
        // TODO Auto-generated method stub

    }

    public Mergeinfo getMergeinfo(String path, Revision pegRevision)
            throws SubversionException {
        // TODO Auto-generated method stub
        return null;
    }

    public void getMergeinfoLog(LogKind kind, String pathOrUrl,
            Revision pegRevision, String mergeSourceUrl,
            Revision srcPegRevision, boolean discoverChangedPaths, Depth depth,
            Set<String> revProps, LogMessageCallback callback)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public void diff(String target1, Revision revision1, String target2,
            Revision revision2, String relativeToDir, String outFileName,
            Depth depth, Collection<String> changelists,
            boolean ignoreAncestry, boolean noDiffDeleted, boolean force,
            boolean copiesAsAdds) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void diff(String target, Revision pegRevision,
            Revision startRevision, Revision endRevision, String relativeToDir,
            String outFileName, Depth depth, Collection<String> changelists,
            boolean ignoreAncestry, boolean noDiffDeleted, boolean force,
            boolean copiesAsAdds) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void diffSummarize(String target1, Revision revision1,
            String target2, Revision revision2, Depth depth,
            Collection<String> changelists, boolean ignoreAncestry,
            DiffSummaryCallback receiver) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void diffSummarize(String target, Revision pegRevision,
            Revision startRevision, Revision endRevision, Depth depth,
            Collection<String> changelists, boolean ignoreAncestry,
            DiffSummaryCallback receiver) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void properties(String path, Revision revision,
            Revision pegRevision, Depth depth, Collection<String> changelists,
            ProplistCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void propertySetLocal(Set<String> paths, String name, byte[] value,
            Depth depth, Collection<String> changelists, boolean force)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public void propertySetRemote(String path, long baseRev, String name,
            byte[] value, CommitMessageCallback handler, boolean force,
            Map<String, String> revpropTable, CommitCallback callback)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public byte[] revProperty(String path, String name, Revision rev)
            throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public Map<String, byte[]> revProperties(String path, Revision rev)
            throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void setRevProperty(String path, String name, Revision rev,
            String value, String originalValue, boolean force)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public byte[] propertyGet(String path, String name, Revision revision,
            Revision pegRevision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] fileContent(String path, Revision revision,
            Revision pegRevision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void streamFileContent(String path, Revision revision,
            Revision pegRevision, OutputStream stream) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void relocate(String from, String to, String path,
            boolean ignoreExternals) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void blame(String path, Revision pegRevision,
            Revision revisionStart, Revision revisionEnd,
            boolean ignoreMimeType, boolean includeMergedRevisions,
            BlameCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void setConfigDirectory(String configDir) throws ClientException {
        // TODO Auto-generated method stub

    }

    public String getConfigDirectory() throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void cancelOperation() throws ClientException {
        // TODO Auto-generated method stub

    }

    public void addToChangelist(Set<String> paths, String changelist,
            Depth depth, Collection<String> changelists) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void removeFromChangelists(Set<String> paths, Depth depth,
            Collection<String> changelists) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void getChangelists(String rootPath, Collection<String> changelists,
            Depth depth, ChangelistCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void lock(Set<String> path, String comment, boolean force)
            throws ClientException {
        // TODO Auto-generated method stub

    }

    public void unlock(Set<String> path, boolean force) throws ClientException {
        // TODO Auto-generated method stub

    }

    public void info2(String pathOrUrl, Revision revision,
            Revision pegRevision, Depth depth, Collection<String> changelists,
            InfoCallback callback) throws ClientException {
        // TODO Auto-generated method stub

    }

    public String getVersionInfo(String path, String trailUrl,
            boolean lastChanged) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void upgrade(String path) throws ClientException {
        // TODO Auto-generated method stub

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

    private Status getStatus(SvnStatus status) throws SVNException {
        String repositoryRelativePath = status.getRepositoryRelativePath() == null ? "" : status.getRepositoryRelativePath();
        SVNURL repositoryRootUrl = status.getRepositoryRootUrl();

        //TODO: repositoryRootUrl is currently null whatever 'remote' ('onServer') option is
        String itemUrl = repositoryRootUrl == null ? null : repositoryRootUrl.appendPath(repositoryRelativePath, false).toString();

        return new Status(
                status.getPath().getPath(),
                itemUrl,
                getNodeKind(status.getKind()),
                status.getRevision(),
                status.getChangedRevision(),
                getLongDate(status.getChangedDate()),
                status.getChangedAuthor(),
                getStatusKind(status.getTextStatus()),
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
        return date.getTime();
    }

    private SvnTarget getTarget(String path) {
        return SvnTarget.fromFile(new File(path));
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
            //TODO: is it a good default value or should an exception be thrown
            return Status.Kind.none;
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

    private SVNRevision getSVNRevision(Revision revision) {
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
        return new CommitItem(commitable.getPath().getPath(), getNodeKind(commitable.getKind()), commitable.getFlags(),
                commitable.getUrl().toString(), commitable.getCopyFromUrl().toString(), commitable.getCopyFromRevision());
    }

    private ISvnObjectReceiver<SvnStatus> getStatusReceiver(final StatusCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnObjectReceiver<SvnStatus>() {
            @Override
            public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                callback.doStatus(null, getStatus(status));
            }
        };
    }

    private ISvnCommitHandler getCommitHandler(final CommitMessageCallback callback) {
        if (callback == null) {
            return null;
        }
        return new ISvnCommitHandler() {
            @Override
            public String getCommitMessage(String message, SvnCommitItem[] commitables) throws SVNException {
                Set<CommitItem> commitItems = new HashSet<CommitItem>();
                for (SvnCommitItem commitable : commitables) {
                    commitItems.add(getSvnCommitItem(commitable));
                }
                return callback.getLogMessage(commitItems);
            }

            @Override
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
            @Override
            public void receive(SvnTarget target, SVNLogEntry svnLogEntry) throws SVNException {
                callback.singleMessage(svnLogEntry.getChangedPaths().keySet(), svnLogEntry.getRevision(), getRevisionProperties(svnLogEntry.getRevisionProperties()), svnLogEntry.hasChildren());
            }
        };
    }

    private Map<String, byte[]> getRevisionProperties(SVNProperties svnProperties) {
        if (svnProperties == null) {
            return null;
        }
        HashMap<String, byte[]> revisionProperties = new HashMap<String, byte[]>();
        Set<String> svnPropertiesNames = svnProperties.nameSet();
        for (String svnPropertyName : svnPropertiesNames) {
            revisionProperties.put(svnPropertyName, svnProperties.getBinaryValue(svnPropertyName));
        }
        return revisionProperties;
    }
}
