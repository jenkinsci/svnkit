package org.apache.subversion.javahl;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import org.apache.subversion.javahl.callback.ReposNotifyCallback;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.Version;
import org.tmatesoft.svn.core.javahl17.SVNReposImpl;

public class SVNRepos implements ISVNRepos {

    private SVNReposImpl delegate;

    public SVNRepos() {
        this.delegate = new SVNReposImpl();
    }

    public void dispose() {
        delegate.dispose();
    }

    public Version getVersion() {
        return delegate.getVersion();
    }

    public void create(File path, boolean disableFsyncCommit, boolean keepLog, File configPath, String fstype) throws ClientException {
        delegate.create(path, disableFsyncCommit, keepLog, configPath, fstype);
    }

    public void deltify(File path, Revision start, Revision end) throws ClientException {
        delegate.deltify(path, start, end);
    }

    public void dump(File path, OutputStream dataOut, Revision start, Revision end, boolean incremental, boolean useDeltas, ReposNotifyCallback callback) throws ClientException {
        delegate.dump(path, dataOut, start, end, incremental, useDeltas, callback);
    }

    public void hotcopy(File path, File targetPath, boolean cleanLogs) throws ClientException {
        delegate.hotcopy(path, targetPath, cleanLogs);
    }

    public void listDBLogs(File path, MessageReceiver receiver) throws ClientException {
        delegate.listDBLogs(path, receiver);
    }

    public void listUnusedDBLogs(File path, MessageReceiver receiver) throws ClientException {
        delegate.listUnusedDBLogs(path, receiver);
    }

    public void load(File path, InputStream dataInput, boolean ignoreUUID, boolean forceUUID, boolean usePreCommitHook, boolean usePostCommitHook, String relativePath, ReposNotifyCallback callback) throws ClientException {
        delegate.load(path, dataInput, ignoreUUID, forceUUID, usePreCommitHook, usePostCommitHook, relativePath, callback);
    }

    public void lstxns(File path, MessageReceiver receiver) throws ClientException {
        delegate.lstxns(path, receiver);
    }

    public long recover(File path, ReposNotifyCallback callback) throws ClientException {
        return delegate.recover(path, callback);
    }

    public void rmtxns(File path, String[] transactions) throws ClientException {
        delegate.rmtxns(path, transactions);
    }

    public void setRevProp(File path, Revision rev, String propName, String propValue, boolean usePreRevPropChangeHook, boolean usePostRevPropChangeHook) throws SubversionException {
        delegate.setRevProp(path, rev, propName, propValue, usePreRevPropChangeHook, usePostRevPropChangeHook);
    }

    public void verify(File path, Revision start, Revision end, ReposNotifyCallback callback) throws ClientException {
        delegate.verify(path, start, end, callback);
    }

    public Set<Lock> lslocks(File path, Depth depth) throws ClientException {
        return delegate.lslocks(path, depth);
    }

    public void rmlocks(File path, String[] locks) throws ClientException {
        delegate.rmlocks(path, locks);
    }

    public void upgrade(File path, ReposNotifyCallback callback) throws ClientException {
        delegate.upgrade(path, callback);
    }

    public void pack(File path, ReposNotifyCallback callback) throws ClientException {
        delegate.pack(path, callback);
    }

    public void cancelOperation() throws ClientException {
        delegate.cancelOperation();
    }
}
