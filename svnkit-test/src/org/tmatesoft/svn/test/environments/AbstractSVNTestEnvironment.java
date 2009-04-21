/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.environments;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNTestEnvironment {

    private static final String DEFAULT_USER = "username";
    private static final String DEFAULT_PASSWORD = "password";

    protected String getDefaultUser() {
        return DEFAULT_USER;
    }

    protected String getDefaultPassword() {
        return DEFAULT_PASSWORD;
    }

    public void init() throws SVNException {
        DAVRepositoryFactory.setup();
        FSRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
    }

    public abstract void dispose() throws SVNException;

    public abstract void setEventHandler(ISVNEventHandler eventHandler);

    public abstract void setExtendedMergeCallback(ISVNExtendedMergeCallback mergeCallback);

    public abstract void createRepository(File path, String uuid) throws SVNException;

    public void createRepository(File path) throws SVNException {
        createRepository(path, null);
    }

    public abstract void load(File path, InputStream dumpStream) throws SVNException;

    public abstract void checkout(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth) throws SVNException;

    public void checkout(SVNURL url, File path, SVNRevision revision, SVNDepth depth) throws SVNException {
        checkout(url, path, SVNRevision.UNDEFINED, revision, depth);
    }

    public abstract void switchPath(File wc, SVNURL url, SVNRevision revision, SVNDepth depth) throws SVNException;

    public abstract void update(File wc, SVNRevision revision, SVNDepth depth, boolean depthIsSticky) throws SVNException;

    public void update(File wc, SVNRevision revision, SVNDepth depth) throws SVNException {
        update(wc, revision, depth, false);
    }

    public abstract void importDirectory(File path, SVNURL url, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException;

    public void importDirectory(File path, SVNURL url, String commitMessage) throws SVNException {
        importDirectory(path, url, commitMessage, null, SVNDepth.INFINITY);
    }

    public abstract long commit(File wc, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException;

    public long commit(File wc, String commitMessage, SVNDepth depth) throws SVNException {
        return commit(wc, commitMessage, null, depth);
    }

    public abstract void merge(SVNURL url, File wc, SVNRevision pegRevision, Collection mergeRanges, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException;

    public void merge(SVNURL url, File wc, Collection mergeRanges, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
        merge(url, wc, SVNRevision.UNDEFINED, mergeRanges, depth, dryRun, recordOnly);
    }

    public abstract void merge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File wc, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException;

    public abstract void status(File wc, SVNRevision revision, SVNDepth depth, boolean remote, boolean reportAll, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException;

    public abstract void copy(File src, SVNRevision pegRevision, SVNRevision revision, File dst, boolean isMove, boolean makeParents, boolean failWhenDstExists) throws SVNException;

    public void copy(File src, SVNRevision revision, File dst, boolean isMove, boolean makeParents, boolean failWhenDstExists) throws SVNException {
        copy(src, SVNRevision.UNDEFINED, revision, dst, isMove, makeParents, failWhenDstExists);
    }

    public abstract void copy(SVNURL src, SVNRevision pegRevision, SVNRevision revision, SVNURL dst, boolean isMove, boolean makeParents, boolean failWhenDstExists, String commitMessage) throws SVNException;

    public void copy(SVNURL src, SVNRevision revision, SVNURL dst, boolean isMove, boolean makeParents, boolean failWhenDstExists, String commitMessage) throws SVNException {
        copy(src, SVNRevision.UNDEFINED, revision, dst, isMove, makeParents, failWhenDstExists, commitMessage);
    }

    public abstract void add(File path, boolean mkdir, SVNDepth depth, boolean makeParents) throws SVNException;

    public abstract void delete(File path) throws SVNException;

    public abstract void setProperty(File path, String propName, SVNPropertyValue propValue, SVNDepth depth) throws SVNException;

    public abstract SVNPropertyValue getProperty(File path, String propName, SVNRevision revision) throws SVNException;

    public void createFile(File file, String content) throws SVNException {
        SVNFileUtil.createFile(file, content, "UTF-8");
    }

    public void write(File file, String text, boolean append) throws SVNException {
        OutputStream os = SVNFileUtil.openFileForWriting(file, append);
        byte[] data;
        try {
            data = text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            data = text.getBytes();
        }
        try {
            os.write(data);
        } catch (IOException e) {
            SVNTestDebugLog.log(e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    public void addLine(File file, String line) throws SVNException {
        write(file, "\n" + line, true);
    }

    public long modifyAndCommit(File file) throws SVNException {
        SVNWCClient wcClient = SVNClientManager.newInstance().getWCClient();
        SVNInfo info = wcClient.doInfo(file, SVNRevision.HEAD);
        long rev = getLatestRevision(info.getURL()) + 1;
        String line = "this line added to file " + info.getPath() + " at r" + rev + ".";
        addLine(file, line);
        String message = "new line added to file " + info.getPath();
        return commit(file, message, SVNDepth.INFINITY);
    }

    private long getLatestRevision(SVNURL url) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(url);
        return repository.getLatestRevision();
    }

    public void getFileContents(File file, OutputStream outputStream) throws SVNException {
        InputStream is = SVNFileUtil.openFileForReading(file);
        byte[] buffer = new byte[512];
        try {
            while (true) {
                int read = is.read(buffer);
                if (read < 0) {
                    break;
                }
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }
}
