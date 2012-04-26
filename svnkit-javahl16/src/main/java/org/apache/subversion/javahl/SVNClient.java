package org.apache.subversion.javahl;

import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.subversion.javahl.types.Mergeinfo;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.RevisionRange;
import org.apache.subversion.javahl.types.Version;
import org.tmatesoft.svn.core.javahl17.SVNClientImpl;

public class SVNClient implements ISVNClient {

    private SVNClientImpl delegate;

    public SVNClient() {
        delegate = SVNClientImpl.newInstance();
    }

    public void dispose() {
        delegate.dispose();
    }

    public Version getVersion() {
        return delegate.getVersion();
    }

    public String getAdminDirectoryName() {
        return delegate.getAdminDirectoryName();
    }

    public boolean isAdminDirectory(String name) {
        return delegate.isAdminDirectory(name);
    }

    public void status(String path, Depth depth, boolean onServer, boolean getAll, boolean noIgnore, boolean ignoreExternals, Collection<String> changelists, StatusCallback callback) throws ClientException {
        delegate.status(path, depth, onServer, getAll, noIgnore, ignoreExternals, changelists, callback);
    }

    public void list(String url, Revision revision, Revision pegRevision, Depth depth, int direntFields, boolean fetchLocks, ListCallback callback) throws ClientException {
        delegate.list(url, revision, pegRevision, depth, direntFields, fetchLocks, callback);
    }

    public void username(String username) {
        delegate.username(username);
    }

    public void password(String password) {
        delegate.password(password);
    }

    public void setPrompt(UserPasswordCallback prompt) {
        delegate.setPrompt(prompt);
    }

    public void logMessages(String path, Revision pegRevision, List<RevisionRange> ranges, boolean stopOnCopy, boolean discoverPath, boolean includeMergedRevisions, Set<String> revProps, long limit, LogMessageCallback callback) throws ClientException {
        delegate.logMessages(path, pegRevision, ranges, stopOnCopy, discoverPath, includeMergedRevisions, revProps, limit, callback);
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, Depth depth, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        return delegate.checkout(moduleName, destPath, revision, pegRevision, depth, ignoreExternals, allowUnverObstructions);
    }

    public void notification2(ClientNotifyCallback notify) {
        delegate.notification2(notify);
    }

    public void setConflictResolver(ConflictResolverCallback listener) {
        delegate.setConflictResolver(listener);
    }

    public void setProgressCallback(ProgressCallback listener) {
        delegate.setProgressCallback(listener);
    }

    public void remove(Set<String> path, boolean force, boolean keepLocal, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.remove(path, force, keepLocal, revpropTable, handler, callback);
    }

    public void revert(String path, Depth depth, Collection<String> changelists) throws ClientException {
        delegate.revert(path, depth, changelists);
    }

    public void add(String path, Depth depth, boolean force, boolean noIgnores, boolean addParents) throws ClientException {
        delegate.add(path, depth, force, noIgnores, addParents);
    }

    public long[] update(Set<String> path, Revision revision, Depth depth, boolean depthIsSticky, boolean makeParents, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        return delegate.update(path, revision, depth, depthIsSticky, makeParents, ignoreExternals, allowUnverObstructions);
    }

    public void commit(Set<String> path, Depth depth, boolean noUnlock, boolean keepChangelist, Collection<String> changelists, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.commit(path, depth, noUnlock, keepChangelist, changelists, revpropTable, handler, callback);
    }

    public void copy(List<CopySource> sources, String destPath, boolean copyAsChild, boolean makeParents, boolean ignoreExternals, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.copy(sources, destPath, copyAsChild, makeParents, ignoreExternals, revpropTable, handler, callback);
    }

    public void move(Set<String> srcPaths, String destPath, boolean force, boolean moveAsChild, boolean makeParents, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.move(srcPaths, destPath, force, moveAsChild, makeParents, revpropTable, handler, callback);
    }

    public void mkdir(Set<String> path, boolean makeParents, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.mkdir(path, makeParents, revpropTable, handler, callback);
    }

    public void cleanup(String path) throws ClientException {
        delegate.cleanup(path);
    }

    public void resolve(String path, Depth depth, ConflictResult.Choice conflictResult) throws SubversionException {
        delegate.resolve(path, depth, conflictResult);
    }

    public long doExport(String srcPath, String destPath, Revision revision, Revision pegRevision, boolean force, boolean ignoreExternals, Depth depth, String nativeEOL) throws ClientException {
        return delegate.doExport(srcPath, destPath, revision, pegRevision, force, ignoreExternals, depth, nativeEOL);
    }

    public long doSwitch(String path, String url, Revision revision, Revision pegRevision, Depth depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnverObstructions, boolean ignoreAncestry) throws ClientException {
        return delegate.doSwitch(path, url, revision, pegRevision, depth, depthIsSticky, ignoreExternals, allowUnverObstructions, ignoreAncestry);
    }

    public void doImport(String path, String url, Depth depth, boolean noIgnore, boolean ignoreUnknownNodeTypes, Map<String, String> revpropTable, CommitMessageCallback handler, CommitCallback callback) throws ClientException {
        delegate.doImport(path, url, depth, noIgnore, ignoreUnknownNodeTypes, revpropTable, handler, callback);
    }

    public Set<String> suggestMergeSources(String path, Revision pegRevision) throws SubversionException {
        return delegate.suggestMergeSources(path, pegRevision);
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, Depth depth, boolean ignoreAncestry, boolean dryRun, boolean recordOnly) throws ClientException {
        delegate.merge(path1, revision1, path2, revision2, localPath, force, depth, ignoreAncestry, dryRun, recordOnly);
    }

    public void merge(String path, Revision pegRevision, List<RevisionRange> revisions, String localPath, boolean force, Depth depth, boolean ignoreAncestry, boolean dryRun, boolean recordOnly) throws ClientException {
        delegate.merge(path, pegRevision, revisions, localPath, force, depth, ignoreAncestry, dryRun, recordOnly);
    }

    public void mergeReintegrate(String path, Revision pegRevision, String localPath, boolean dryRun) throws ClientException {
        delegate.mergeReintegrate(path, pegRevision, localPath, dryRun);
    }

    public Mergeinfo getMergeinfo(String path, Revision pegRevision) throws SubversionException {
        return delegate.getMergeinfo(path, pegRevision);
    }

    public void getMergeinfoLog(Mergeinfo.LogKind kind, String pathOrUrl, Revision pegRevision, String mergeSourceUrl, Revision srcPegRevision, boolean discoverChangedPaths, Depth depth, Set<String> revProps, LogMessageCallback callback) throws ClientException {
        delegate.getMergeinfoLog(kind, pathOrUrl, pegRevision, mergeSourceUrl, srcPegRevision, discoverChangedPaths, depth, revProps, callback);
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String relativeToDir, String outFileName, Depth depth, Collection<String> changelists, boolean ignoreAncestry, boolean noDiffDeleted, boolean force, boolean copiesAsAdds) throws ClientException {
        delegate.diff(target1, revision1, target2, revision2, relativeToDir, outFileName, depth, changelists, ignoreAncestry, noDiffDeleted, force, copiesAsAdds);
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision, String relativeToDir, String outFileName, Depth depth, Collection<String> changelists, boolean ignoreAncestry, boolean noDiffDeleted, boolean force, boolean copiesAsAdds) throws ClientException {
        delegate.diff(target, pegRevision, startRevision, endRevision, relativeToDir, outFileName, depth, changelists, ignoreAncestry, noDiffDeleted, force, copiesAsAdds);
    }

    public void diffSummarize(String target1, Revision revision1, String target2, Revision revision2, Depth depth, Collection<String> changelists, boolean ignoreAncestry, DiffSummaryCallback receiver) throws ClientException {
        delegate.diffSummarize(target1, revision1, target2, revision2, depth, changelists, ignoreAncestry, receiver);
    }

    public void diffSummarize(String target, Revision pegRevision, Revision startRevision, Revision endRevision, Depth depth, Collection<String> changelists, boolean ignoreAncestry, DiffSummaryCallback receiver) throws ClientException {
        delegate.diffSummarize(target, pegRevision, startRevision, endRevision, depth, changelists, ignoreAncestry, receiver);
    }

    public void properties(String path, Revision revision, Revision pegRevision, Depth depth, Collection<String> changelists, ProplistCallback callback) throws ClientException {
        delegate.properties(path, revision, pegRevision, depth, changelists, callback);
    }

    public void propertySetLocal(Set<String> paths, String name, byte[] value, Depth depth, Collection<String> changelists, boolean force) throws ClientException {
        delegate.propertySetLocal(paths, name, value, depth, changelists, force);
    }

    public void propertySetRemote(String path, long baseRev, String name, byte[] value, CommitMessageCallback handler, boolean force, Map<String, String> revpropTable, CommitCallback callback) throws ClientException {
        delegate.propertySetRemote(path, baseRev, name, value, handler, force, revpropTable, callback);
    }

    public byte[] revProperty(String path, String name, Revision rev) throws ClientException {
        return delegate.revProperty(path, name, rev);
    }

    public Map<String, byte[]> revProperties(String path, Revision rev) throws ClientException {
        return delegate.revProperties(path, rev);
    }

    public void setRevProperty(String path, String name, Revision rev, String value, String originalValue, boolean force) throws ClientException {
        delegate.setRevProperty(path, name, rev, value, originalValue, force);
    }

    public byte[] propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
        return delegate.propertyGet(path, name, revision, pegRevision);
    }

    public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
        return delegate.fileContent(path, revision, pegRevision);
    }

    public void streamFileContent(String path, Revision revision, Revision pegRevision, OutputStream stream) throws ClientException {
        delegate.streamFileContent(path, revision, pegRevision, stream);
    }

    public void relocate(String from, String to, String path, boolean ignoreExternals) throws ClientException {
        delegate.relocate(from, to, path, ignoreExternals);
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, boolean ignoreMimeType, boolean includeMergedRevisions, BlameCallback callback) throws ClientException {
        delegate.blame(path, pegRevision, revisionStart, revisionEnd, ignoreMimeType, includeMergedRevisions, callback);
    }

    public void setConfigDirectory(String configDir) throws ClientException {
        delegate.setConfigDirectory(configDir);
    }

    public String getConfigDirectory() throws ClientException {
        return delegate.getConfigDirectory();
    }

    public void cancelOperation() throws ClientException {
        delegate.cancelOperation();
    }

    public void addToChangelist(Set<String> paths, String changelist, Depth depth, Collection<String> changelists) throws ClientException {
        delegate.addToChangelist(paths, changelist, depth, changelists);
    }

    public void removeFromChangelists(Set<String> paths, Depth depth, Collection<String> changelists) throws ClientException {
        delegate.removeFromChangelists(paths, depth, changelists);
    }

    public void getChangelists(String rootPath, Collection<String> changelists, Depth depth, ChangelistCallback callback) throws ClientException {
        delegate.getChangelists(rootPath, changelists, depth, callback);
    }

    public void lock(Set<String> path, String comment, boolean force) throws ClientException {
        delegate.lock(path, comment, force);
    }

    public void unlock(Set<String> path, boolean force) throws ClientException {
        delegate.unlock(path, force);
    }

    public void info2(String pathOrUrl, Revision revision, Revision pegRevision, Depth depth, Collection<String> changelists, InfoCallback callback) throws ClientException {
        delegate.info2(pathOrUrl, revision, pegRevision, depth, changelists, callback);
    }

    public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
        return delegate.getVersionInfo(path, trailUrl, lastChanged);
    }

    public void upgrade(String path) throws ClientException {
        delegate.upgrade(path);
    }

    public void patch(String patchPath, String targetPath, boolean dryRun, int stripCount, boolean reverse, boolean ignoreWhitespace, boolean removeTempfiles, PatchCallback callback) throws ClientException {
        delegate.patch(patchPath, targetPath, dryRun, stripCount, reverse, ignoreWhitespace, removeTempfiles, callback);
    }
}
