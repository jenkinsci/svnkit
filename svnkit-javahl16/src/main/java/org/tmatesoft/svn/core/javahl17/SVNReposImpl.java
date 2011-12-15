package org.tmatesoft.svn.core.javahl17;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.ISVNRepos;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.ReposNotifyCallback;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.Version;

public class SVNReposImpl {

    public SVNReposImpl() {
    }

    public void dispose() {
    }

    public Version getVersion() {
        return null;
    }

    public void create(File path, boolean disableFsyncCommit, boolean keepLog, File configPath, String fstype) throws ClientException {
    }

    public void deltify(File path, Revision start, Revision end) throws ClientException {
    }

    public void dump(File path, OutputStream dataOut, Revision start, Revision end, boolean incremental, boolean useDeltas, ReposNotifyCallback callback) throws ClientException {
    }

    public void hotcopy(File path, File targetPath, boolean cleanLogs) throws ClientException {
    }

    public void listDBLogs(File path, ISVNRepos.MessageReceiver receiver) throws ClientException {
    }

    public void listUnusedDBLogs(File path, ISVNRepos.MessageReceiver receiver) throws ClientException {
    }

    public void load(File path, InputStream dataInput, boolean ignoreUUID, boolean forceUUID, boolean usePreCommitHook, boolean usePostCommitHook, String relativePath, ReposNotifyCallback callback) throws ClientException {
    }

    public void lstxns(File path, ISVNRepos.MessageReceiver receiver) throws ClientException {
    }

    public long recover(File path, ReposNotifyCallback callback) throws ClientException {
        return 0;
    }

    public void rmtxns(File path, String[] transactions) throws ClientException {
    }

    public void setRevProp(File path, Revision rev, String propName, String propValue, boolean usePreRevPropChangeHook, boolean usePostRevPropChangeHook) throws SubversionException {
    }

    public void verify(File path, Revision start, Revision end, ReposNotifyCallback callback) throws ClientException {
    }

    public Set<Lock> lslocks(File path, Depth depth) throws ClientException {
        return null;
    }

    public void rmlocks(File path, String[] locks) throws ClientException {
    }

    public void upgrade(File path, ReposNotifyCallback callback) throws ClientException {
    }

    public void pack(File path, ReposNotifyCallback callback) throws ClientException {
    }

    public void cancelOperation() throws ClientException {
    }

}
