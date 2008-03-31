/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * args: reposRootURL importDir1 importDir2 ...
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNConcurrentCommitTest {

    /**
     * @param args
     * @throws SVNException 
     */
    
    private static long ourTestStartTime = 0;
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("args: URL dir1 dir2 ...");
            System.exit(1);
        }
        FSRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();

        try {

            SVNURL reposURL = SVNURL.parseURIDecoded(args[0]);
            SVNImportThread[] threads = new SVNImportThread[args.length - 1];

            for (int i = 1; i < args.length; i++) {
                File dir = new File(args[i]);
                String message = "import #" + i + ": '" + dir + "'";
                SVNURL destination = reposURL.appendPath(dir.getName(), false);
                threads[i - 1] = new SVNImportThread(dir, destination, message);
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].start();
            }
        } catch (SVNException svne) {
            System.out.println(svne.getMessage());
            System.exit(1);
        }
    }
    
    public static void updateTest() throws SVNException {
        FSRepositoryFactory.setup();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.err.println("time: " + (System.currentTimeMillis() - ourTestStartTime));
            }
        }));

        SVNURL url = SVNURL.parseURIEncoded("file:///e:/test/svn/repos/svn/trunk");
        Thread[] users = new Thread[2000];
        for (int i = 0; i < users.length; i++) {
            users[i] = new Thread(new UpdateRunnable(SVNRepositoryFactory.create(url)));
            users[i].setName(Integer.toString(i));
        }
        ourTestStartTime = System.currentTimeMillis();
        for (int i = 0; i < users.length; i++) {
            users[i].start();
        }
    }
    
    private static class UpdateRunnable implements Runnable, ISVNEditor {
        
        private SVNRepository myRepository;

        public UpdateRunnable(SVNRepository repos) {
            myRepository = repos;
        }

        public void run() {
            System.out.println("Started: " + Thread.currentThread().getName());
            long time = System.currentTimeMillis();
            try {
                myRepository.getDir("", -1, null, new ISVNDirEntryHandler() {
                    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                    }
                });
            } catch (SVNException e) {
                e.printStackTrace();
            }
            System.out.println("OK: " + Thread.currentThread().getName() + "[" +(System.currentTimeMillis() - time) +" ms]");
        }

        public void abortEdit() throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void changeDirProperty(String name, String value) throws SVNException {
        }

        public void changeFileProperty(String path, String name, String value) throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void closeFile(String path, String textChecksum) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void openDir(String path, long revision) throws SVNException {
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void openRoot(long revision) throws SVNException {
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            return null;
        }

        public void textDeltaEnd(String path) throws SVNException {
        }
        
    }

}
