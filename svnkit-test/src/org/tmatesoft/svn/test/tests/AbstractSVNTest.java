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
package org.tmatesoft.svn.test.tests;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.test.ISVNTestOptions;
import org.tmatesoft.svn.test.SVNTestException;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.sandboxes.AbstractSVNSandbox;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.wc.SVNWorkingCopyValidator;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractSVNTest {

    private static final String DEFAULT_DUMP_FILE = "default.dump";

    private AbstractSVNTestEnvironment myEnvironment;
    private AbstractSVNSandbox mySandbox;
    private SVNWorkingCopyValidator myWCValidator;

    protected AbstractSVNTestEnvironment getEnvironment() {
        return myEnvironment;
    }

    protected AbstractSVNSandbox getSandbox() {
        return mySandbox;
    }

    protected SVNWorkingCopyValidator createWCValidator(File wcRoot, SVNWCDescriptor descriptor, boolean checkExpectedAttributesOnly) throws SVNTestException {
        if (myWCValidator == null) {
            myWCValidator = new SVNWorkingCopyValidator(wcRoot, descriptor, checkExpectedAttributesOnly);
        } else {
            myWCValidator.reset(wcRoot, descriptor, checkExpectedAttributesOnly);
        }
        return myWCValidator;
    }

    public void init(AbstractSVNSandbox sandbox, AbstractSVNTestEnvironment environment) throws SVNException {
        mySandbox = sandbox;
        myEnvironment = environment;        
    }

    public abstract SVNWCDescriptor getInitialFS();

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
        } else {
            getSandbox().fill(getDefaultFS(), getEnvironment());
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

    protected SVNWCDescriptor getDefaultFS() {
        SVNWCDescriptor structure = new SVNWCDescriptor();
        structure.addFile(new SVNTestFileDescriptor("iota", "This is the file 'iota'."));
        structure.addFile(new SVNTestFileDescriptor("A"));
        structure.addFile(new SVNTestFileDescriptor("A/mu", "This is the file 'A/mu'."));
        structure.addFile(new SVNTestFileDescriptor("A/B"));
        structure.addFile(new SVNTestFileDescriptor("A/B/lambda", "This is the file 'A/B/lambda'."));
        structure.addFile(new SVNTestFileDescriptor("A/D"));
        structure.addFile(new SVNTestFileDescriptor("A/D/gamma", "This is the file 'A/D/gamma'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/G"));
        structure.addFile(new SVNTestFileDescriptor("A/D/G/pi", "This is the file 'A/D/G/pi'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/G/rho", "This is the file 'A/D/G/rho'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/G/tau", "This is the file 'A/D/G/tau'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/H"));
        structure.addFile(new SVNTestFileDescriptor("A/D/H/chi", "This is the file 'A/D/H/chi'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/H/omega", "This is the file 'A/D/H/omega'."));
        structure.addFile(new SVNTestFileDescriptor("A/D/H/psi", "This is the file 'A/D/H/psi'."));
        structure.addFile(new SVNTestFileDescriptor("A/B/E"));
        return structure;
    }

    protected String getDefaultDumpFile() {
        return DEFAULT_DUMP_FILE;
    }
}
