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
package org.tmatesoft.svn.test.sandboxes;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNSandbox {

    private static final int WC_COUNT = 10;
    private static final String DEFAULT_BRANCH_NAME = "branch";    

    private File myDumpFilesDirectory;

    private File myTMP;
    private File myRepoPath;
    private File[] myWCs;

    private SVNURL myRepo;
    private SVNURL myTrunk;
    private SVNURL myBranches;
    private SVNURL myTags;

    protected AbstractSVNSandbox(File tmp, File dumpFilesDirectory) throws SVNException {
        myTMP = tmp;
        myDumpFilesDirectory = dumpFilesDirectory;
        myRepoPath = new File(getTMP(), "repo");
        myWCs = new File[WC_COUNT];

        for (int i = 0; i < myWCs.length; i++) {
            myWCs[i] = new File(tmp, "wc" + (i > 0 ? String.valueOf(i) : ""));
        }

        initURLs(SVNURL.fromFile(getRepoPath()));
    }

    protected void initURLs(SVNURL repo) throws SVNException {
        myRepo = repo;
        myTrunk = myRepo.appendPath("trunk", false);
        myBranches = myRepo.appendPath("branches", false);
        myTags = myRepo.appendPath("tags", false);
    }

    public File getDumpFile(String dumpName) {
        return new File(myDumpFilesDirectory, dumpName);
    }

    public File getTMP() {
        return myTMP;
    }

    public File getRepoPath() {
        return myRepoPath;
    }

    public File getWC() {
        return myWCs[0];
    }

    public File getWC(int i) {
        if (i >= WC_COUNT) {
            return null;
        }
        return myWCs[i];
    }

    public SVNURL getRepo() {
        return myRepo;
    }

    public SVNURL getTrunk() {
        return myTrunk;
    }

    public SVNURL getBranches() {
        return myBranches;
    }

    public SVNURL getBranch(String name) throws SVNException {
        return getBranches().appendPath(name, false);
    }

    public SVNURL getBranch() throws SVNException {
        return getBranch(DEFAULT_BRANCH_NAME);
    }

    public String getBranchName() {
        return DEFAULT_BRANCH_NAME;
    }

    public SVNURL getTags() {
        return myTags;
    }

    public File getFile(String name) {
        return new File(getWC(), name);
    }

    public File getFile(String name, int i) {
        return new File(getWC(i), name);
    }

    public abstract void init(AbstractSVNTestEnvironment environment) throws SVNException;

    public void fill(SVNWCDescriptor description, AbstractSVNTestEnvironment environment) throws SVNException {
        if (description == null) {
            return;
        }
        
        File tmpWC = new File(getTMP(), "initial");
        File trunk = new File(tmpWC, "trunk");
        File branches = new File(tmpWC, "branches");
        File tags = new File(tmpWC, "tags");

        tmpWC.mkdir();
        trunk.mkdir();
        branches.mkdir();
        tags.mkdir();

        description.dump(trunk);

        environment.importDirectory(tmpWC, getRepo(), "import initial structure");
        SVNFileUtil.deleteAll(tmpWC, true);
    }

    public void fill(String dumpFileName, AbstractSVNTestEnvironment environment) throws SVNException {
        File dumpFile = getDumpFile(dumpFileName);
        InputStream dumpStream = SVNFileUtil.openFileForReading(dumpFile);
        try {
            environment.load(getRepoPath(), dumpStream);
        } finally {
            SVNFileUtil.closeFile(dumpStream);
        }
    }

    public abstract void dispose() throws SVNException;

    protected void deleteTMP() throws SVNException {
        SVNFileUtil.deleteAll(getTMP(), true);
    }



    protected Process execCommand(String[] command, boolean wait) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
        if (process != null) {
            try {
                SVNTestDebugLog.createReader(process.getInputStream()).start();
                SVNTestDebugLog.createReader(process.getErrorStream()).start();
                if (wait) {
                    int code = process.waitFor();
                    if (code != 0) {
                        StringBuffer commandLine = new StringBuffer();
                        for (int i = 0; i < command.length; i++) {
                            commandLine.append(command[i]);
                            if (i + 1 != command.length) {
                                commandLine.append(' ');
                            }
                        }
                        throw new IOException("process '"  +  commandLine + "' exit code is not 0 : " + code);
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
        }
        return process;
    }
}
