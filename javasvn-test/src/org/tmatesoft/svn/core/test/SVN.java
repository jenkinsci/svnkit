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

package org.tmatesoft.svn.core.test;

import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVN {

    public static void main(String[] args) {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSEntryFactory.setup();
        if (args == null || args.length < 3) {
            System.err.println("USAGE SVN EXPORT | CHECKOUT URL PATH PATH2");
            System.exit(1);
        }
        try {
            SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(args[1].trim()));
            repos.log(new String[] {"bin"}, 50000, -1, false, false, new ISVNLogEntryHandler() {
               public void handleLogEntry(SVNLogEntry logEntry) {
                   System.err.println(logEntry.getAuthor() + ":" + logEntry.getRevision() + "\n" + logEntry.getMessage());
               } 
            });
        } catch (SVNException e) {
            e.printStackTrace();
        }
        System.exit(0);
        
        long start = System.currentTimeMillis();
        boolean export = "export".equalsIgnoreCase(args[0].trim());
        File path = new File(args[2]);
        ISVNWorkspace ws = null;
        try {
            FSUtil.deleteAll(path);
            path.mkdirs();
            start = System.currentTimeMillis();
            SVNRepositoryLocation url = SVNRepositoryLocation.parseURL(args[1]);
            ws = SVNWorkspaceManager.createWorkspace("file", path.getAbsolutePath());
            ws.checkout(url, ISVNWorkspace.HEAD, export);
        } catch (SVNException e) {
            System.err.println("error: " + e.getMessage());
        }
        
        long time = (System.currentTimeMillis() - start);
        DebugLog.benchmark("checkout took:  " + time + " ms.");
        if (args.length == 4) {
            path = new File(args[3]);
            AllTests.deleteAll(path);
            path.mkdirs();
            long time2 = System.currentTimeMillis();
            try {
                runSVNAnonCommand("co", new String[] {args[1], path.getAbsolutePath()});
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            time2 = System.currentTimeMillis() - time2;
            DebugLog.benchmark("NATIVE CHECKOUT: " + time2 + " ms.");
            DebugLog.benchmark("RATIO: " + ((double) time)/((double) time2));
        }
        try {
            ws.status("", false, new ISVNStatusHandler() {
                public void handleStatus(String p, SVNStatus status) {
                    DebugLog.benchmark(status.getContentsStatus() + ": " + p);
                }
            }, true, false, false);
        } catch (SVNException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public static void runSVNAnonCommand(String command, String[] params) throws IOException {
        String[] realCommand = new String[params.length + 2];
        realCommand[0] = "svn";
        realCommand[1] = command;        
        for(int i = 0; i < params.length; i++) {
            realCommand[i + 2] = params[i];
        }
        execCommand(realCommand, true);            
    }

    private static Process execCommand(String[] command, boolean wait) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                new AllTests.ReaderThread(process.getInputStream()).start();
                new AllTests.ReaderThread(process.getErrorStream()).start();
                if (wait) {
                    int code = process.waitFor();
                    if (code != 0) {
                        throw new IOException("process '"  +  command[0] + "' exit code is not 0 : " + code);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
        }
        return process;
    }
}
