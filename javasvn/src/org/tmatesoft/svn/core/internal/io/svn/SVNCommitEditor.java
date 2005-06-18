/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class SVNCommitEditor implements ISVNEditor {
    
    private ISVNWorkspaceMediator myMediator;
    private SVNConnection myConnection;
    private SVNRepositoryImpl myRepository;
    
    private String myCurrentPath;
    private Runnable myCloseCallback;
    
    public SVNCommitEditor(SVNRepositoryImpl location, SVNConnection connection, ISVNWorkspaceMediator mediator,
            Runnable closeCallback) {
        myRepository = location;
        myConnection = connection;
        myMediator = mediator;
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
        myCurrentPath = "/";
        myConnection.write("(w((n)s))", new Object[] {"open-root", getRevisionObject(revision), "/"});
    }
    public void deleteEntry(String path, long revision) throws SVNException {
        myConnection.write("(w(s(n)s))", new Object[] {"delete-entry", path, getRevisionObject(revision), myCurrentPath});
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            SVNRepositoryLocation location = myRepository.getLocation();
            String host = myRepository.getFullRoot();
            if (host == null) {
            	host = location.getProtocol() + "://" + location.getHost() + ":" + location.getPort();
                host = PathUtil.append(host, PathUtil.encode(myRepository.getRepositoryRoot()));
            }
            copyFromPath = PathUtil.append(host, PathUtil.encode(myRepository.getRepositoryPath(copyFromPath)));
            myConnection.write("(w(sss(sn)))", new Object[] {"add-dir", path, myCurrentPath, path, copyFromPath, getRevisionObject(copyFromRevision)});
        } else {
            myConnection.write("(w(sss()))", new Object[] {"add-dir", path, myCurrentPath, path});
        }
        myCurrentPath = path;
    }
    public void openDir(String path, long revision) throws SVNException {
        myCurrentPath = path;
        myConnection.write("(w(sss(n)))", new Object[] {"open-dir", path, computeParentPath(path), path, getRevisionObject(revision)});
    }
    public void changeDirProperty(String name, String value)  throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] {"change-dir-prop", myCurrentPath, name, value});
    }
    public void closeDir() throws SVNException {
        myConnection.write("(w(s))", new Object[] {"close-dir", myCurrentPath});
        myCurrentPath = computeParentPath(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            SVNRepositoryLocation location = myRepository.getLocation();
            String host = myRepository.getFullRoot();
            if (host == null) {
            	host = location.getProtocol() + "://" + location.getHost() + ":" + location.getPort();
                host = PathUtil.append(host, PathUtil.encode(myRepository.getRepositoryRoot()));
            }
            copyFromPath = PathUtil.append(host, PathUtil.encode(myRepository.getRepositoryPath(copyFromPath)));
            myConnection.write("(w(sss(sn)))", new Object[] {"add-file", path, myCurrentPath, path, copyFromPath, getRevisionObject(copyFromRevision)});
        } else {
            myConnection.write("(w(sss()))", new Object[] {"add-file", path, myCurrentPath, path});
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        myConnection.write("(w(sss(n)))", new Object[] {"open-file", path, computeParentPath(path), path, getRevisionObject(revision)});
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myConnection.write("(w(s(s)))", new Object[] {"apply-textdelta", path, baseChecksum});
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        myConnection.write("(w(s", new Object[] {"textdelta-chunk", path});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            SVNDiffWindowBuilder.save(diffWindow, bos);
            myConnection.write("b))", new Object[] {bos.toByteArray()});
            return myMediator.createTemporaryLocation(path, path);
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }
    public void textDeltaEnd(String path) throws SVNException {
        InputStream is;
        try {
            long length = myMediator.getLength(path);
            if (myMediator.getLength(path) > 0) {
                is = myMediator.getTemporaryLocation(path);
                // create
                SVNDataSource source = new SVNDataSource(is, length);
                myConnection.write("(w(si))", new Object[] {"textdelta-chunk", path, source});
                is.close();
            }
            DebugLog.log("new data sent" + length);
        } catch (IOException e) {
            throw new SVNException();
        } finally {
            myMediator.deleteTemporaryLocation(path);
        }
        myConnection.write("(w(s))", new Object[] {"textdelta-end", path});
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] {"change-file-prop", path, name, value});
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myConnection.write("(w(s(s)))", new Object[] {"close-file", path, textChecksum});
//        myCurrentPath = computeParentPath(myCurrentPath);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myConnection.write("(w())", new Object[] {"close-edit"});

        myConnection.read("[()]", null);
        myRepository.authenticate();
        
        Object[] items = myConnection.read("(N(?S)(?S))", new Object[3]);
        long revision = SVNReader.getLong(items, 0);
        Date date = SVNReader.getDate(items, 1);

        myCloseCallback.run();
        return new SVNCommitInfo(revision, (String) items[2], date);
    }
    
    public void abortEdit() throws SVNException {
        myConnection.write("(w())", new Object[] {"abort-edit"});
        myCloseCallback.run();
    }
    
    private static String computeParentPath(String path) {
        return PathUtil.removeTail(path); 
    }
    
    private static Long getRevisionObject(long rev) {
        return rev >= 0 ? new Long(rev) : null;
    }
}
