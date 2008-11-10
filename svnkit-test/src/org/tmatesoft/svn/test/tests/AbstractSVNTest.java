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
package org.tmatesoft.svn.test.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.sandboxes.AbstractSVNSandbox;
import org.tmatesoft.svn.test.sandboxes.SVNSandboxFile;
import org.tmatesoft.svn.test.ISVNTestOptions;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNTest {

    private static final String DEFAULT_DUMP_FILE = "default.dump";

    private AbstractSVNTestEnvironment myEnvironment;
    private AbstractSVNSandbox mySandbox;

    protected AbstractSVNTestEnvironment getEnvironment() {
        return myEnvironment;
    }

    protected AbstractSVNSandbox getSandbox() {
        return mySandbox;
    }

    public void init(AbstractSVNSandbox sandbox, AbstractSVNTestEnvironment environment) throws SVNException {
        mySandbox = sandbox;
        myEnvironment = environment;
    }

    public abstract Collection getInitialFS();

    public abstract String getDumpFile();

    public abstract ISVNTestOptions getOptions();

    public abstract void run() throws SVNException;

    public void dispose() throws SVNException {
        mySandbox = null;
        myEnvironment = null;
    }

    protected void fill() throws SVNException {
        if (getInitialFS() != null) {
            getSandbox().fill(getInitialFS(), getEnvironment());
        } else if (getDumpFile() != null) {
            getSandbox().fill(getDumpFile(), getEnvironment());
        }
    }

    public File getTMP() {
        return getSandbox().getTMP();
    }

    public File getRepoPath() {
        return getSandbox().getRepoPath();
    }

    public File getWC() {
        return getSandbox().getWC();
    }

    public File getSecondaryWC() {
        return getSandbox().getWC(2);
    }

    public File getWC(int i) {
        return getSandbox().getWC(i);
    }

    public SVNURL getRepo() {
        return getSandbox().getRepo();
    }

    public SVNURL getTrunk() {
        return getSandbox().getTrunk();
    }

    public SVNURL getBranches() {
        return getSandbox().getBranches();
    }

    public SVNURL getBranch(String name) throws SVNException {
        return getBranches().appendPath(name, false);
    }

    public SVNURL getBranch() throws SVNException {
        return getSandbox().getBranch();
    }

    public SVNURL getTags() {
        return getSandbox().getTags();
    }

    public File getFile(String name) {
        return getSandbox().getFile(name);
    }

    public File getSecondaryFile(String name) {
        return getSandbox().getFile(name, 2);
    }

    public File getFile(String name, int i) {
        return getSandbox().getFile(name, i);
    }

    protected Collection getDefaultFS() {
        Collection structure = new ArrayList();
        structure.add(new SVNSandboxFile("iota", "This is the file 'iota'.", false));
        structure.add(new SVNSandboxFile("A"));
        structure.add(new SVNSandboxFile("A/mu", "This is the file 'A/mu'.", false));
        structure.add(new SVNSandboxFile("A/B"));
        structure.add(new SVNSandboxFile("A/B/lambda", "This is the file 'A/B/lambda'.", false));
        structure.add(new SVNSandboxFile("A/D"));
        structure.add(new SVNSandboxFile("A/D/gamma", "This is the file 'A/D/gamma'.", false));
        structure.add(new SVNSandboxFile("A/D/G"));
        structure.add(new SVNSandboxFile("A/D/G/pi", "This is the file 'A/D/G/pi'.", false));
        structure.add(new SVNSandboxFile("A/D/G/rho", "This is the file 'A/D/G/rho'.", false));
        structure.add(new SVNSandboxFile("A/D/G/tau", "This is the file 'A/D/G/tau'.", false));
        structure.add(new SVNSandboxFile("A/D/H"));
        structure.add(new SVNSandboxFile("A/D/H/chi", "This is the file 'A/D/H/chi'.", false));
        structure.add(new SVNSandboxFile("A/D/H/omega", "This is the file 'A/D/H/omega'.", false));
        structure.add(new SVNSandboxFile("A/D/H/psi", "This is the file 'A/D/H/psi'.", false));
        structure.add(new SVNSandboxFile("A/B/E"));
        return structure;
    }

    protected String getDefaultDumpFile() {
        return DEFAULT_DUMP_FILE;
    }
}
