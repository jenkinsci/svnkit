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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNSandbox extends AbstractSVNSandbox {

    private String mySVNServePath;

    protected SVNSandbox(File tmp, File dumpsDir, String servePath) throws SVNException {
        super(tmp, dumpsDir);
        mySVNServePath = servePath;
    }

    public String getServePath() {
        return mySVNServePath;
    }

    public void init(AbstractSVNTestEnvironment environment) throws SVNException {
    }

    public void dispose() {
    }
}
