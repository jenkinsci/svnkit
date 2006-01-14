/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
class SVNCommitEditor implements ISVNEditor {

    private SVNConnection myConnection;
    private SVNRepositoryImpl myRepository;
    private String myCurrentPath;
    private Runnable myCloseCallback;

    public SVNCommitEditor(SVNRepositoryImpl location, SVNConnection connection, Runnable closeCallback) {
        myRepository = location;
        myConnection = connection;
        myCloseCallback = closeCallback;
    }

    /* do nothing */
    public void targetRevision(long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentPath = "";
        myConnection.write("(w((n)s))", new Object[] { "open-root",
                getRevisionObject(revision), "" });
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myConnection.write("(w(s(n)s))", new Object[] { "delete-entry", path,
                getRevisionObject(revision), myCurrentPath });
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        if (copyFromPath != null) {
            String rootURL = myRepository.getRepositoryRoot(false).toString();
            copyFromPath = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(myRepository.getRepositoryPath(copyFromPath)));
            myConnection.write("(w(sss(sn)))", new Object[] { "add-dir", path,
                    myCurrentPath, path, copyFromPath,
                    getRevisionObject(copyFromRevision) });
        } else {
            myConnection.write("(w(sss()))", new Object[] { "add-dir", path,
                    myCurrentPath, path });
        }
        myCurrentPath = path;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentPath = path;
        myConnection.write("(w(sss(n)))", new Object[] { "open-dir", path,
                computeParentPath(path), path, getRevisionObject(revision) });
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] { "change-dir-prop",
                myCurrentPath, name, value });
    }

    public void closeDir() throws SVNException {
        myConnection.write("(w(s))",
                new Object[] { "close-dir", myCurrentPath });
        myCurrentPath = computeParentPath(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        if (copyFromPath != null) {
            String host = myRepository.getRepositoryRoot(false).toString();
            copyFromPath = SVNPathUtil.append(host, SVNEncodingUtil.uriEncode(myRepository.getRepositoryPath(copyFromPath)));
            myConnection.write("(w(sss(sn)))", new Object[] { "add-file", path,
                    myCurrentPath, path, copyFromPath,
                    getRevisionObject(copyFromRevision) });
        } else {
            myConnection.write("(w(sss()))", new Object[] { "add-file", path,
                    myCurrentPath, path });
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        myConnection.write("(w(sss(n)))", new Object[] { "open-file", path,
                computeParentPath(path), path, getRevisionObject(revision) });
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myDiffWindowCount = 0;
        myConnection.write("(w(s(s)))", new Object[] { "apply-textdelta", path, baseChecksum });
    }

    private int myDiffWindowCount = 0;
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        myConnection.write("(w(s", new Object[] { "textdelta-chunk", path });
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            SVNDiffWindowBuilder.save(diffWindow, myDiffWindowCount == 0, bos);
            byte[] header = bos.toByteArray();
            myDiffWindowCount++;
            myConnection.write("b))", new Object[] { header });
            myConnection.write("(w(s", new Object[] { "textdelta-chunk", path });
            String length = diffWindow.getNewDataLength() + ":";
            myConnection.getOutputStream().write(length.getBytes("UTF-8"));
            return new ChunkOutputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
        }
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDiffWindowCount = 0;
        myConnection.write("(w(s))", new Object[] { "textdelta-end", path });
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] { "change-file-prop", path, name, value });
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myDiffWindowCount = 0;
        myConnection.write("(w(s(s)))", new Object[] { "close-file", path, textChecksum });
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myConnection.write("(w())", new Object[] { "close-edit" });

        myConnection.read("[()]", null);
        myRepository.authenticate();

        Object[] items = myConnection.read("(N(?S)(?S))", new Object[3]);
        long revision = SVNReader.getLong(items, 0);
        Date date = SVNReader.getDate(items, 1);

        myCloseCallback.run();
        return new SVNCommitInfo(revision, (String) items[2], date);
    }

    public void abortEdit() throws SVNException {
        myConnection.write("(w())", new Object[] { "abort-edit" });
        myCloseCallback.run();
    }

    private static String computeParentPath(String path) {
        return SVNPathUtil.removeTail(path);
    }

    private static Long getRevisionObject(long rev) {
        return rev >= 0 ? new Long(rev) : null;
    }

    private final class ChunkOutputStream extends OutputStream {
        
        private boolean myIsClosed = false;
        
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                myConnection.getOutputStream().write(b, off, len);
            } catch (SVNException e) {
                throw new IOException(e.getMessage());
            }
        }

        public void write(int b) throws IOException {
            try {
                myConnection.getOutputStream().write(b);
            } catch (SVNException e) {
                throw new IOException(e.getMessage());
            }
        }

        public void close() throws IOException {
            if (myIsClosed) {
                return;
            }
            try {
                myConnection.getOutputStream().write(' ');
                myConnection.write("))", null);
            } catch (SVNException e) {
                throw new IOException(e.getMessage());
            } finally {
                myIsClosed = true;
            }
        }
    }
}
