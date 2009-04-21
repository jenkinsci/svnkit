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
package org.tmatesoft.svn.test.sandboxes;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class FSSandbox extends AbstractSVNSandbox {

    protected FSSandbox(File tmp, File dumpsDir) throws SVNException {
        super(tmp, dumpsDir);
    }

    public void init(AbstractSVNTestEnvironment environment) throws SVNException {
        getTMP().mkdirs();
        environment.createRepository(getRepoPath());
        initURLs(SVNURL.fromFile(getRepoPath()));
    }

    public void dispose() throws SVNException {
        deleteTMP();
    }
}
