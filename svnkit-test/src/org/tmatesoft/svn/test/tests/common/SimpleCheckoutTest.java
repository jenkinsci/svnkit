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
package org.tmatesoft.svn.test.tests.common;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.ISVNTestOptions;
import org.tmatesoft.svn.test.tests.AbstractSVNTest;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SimpleCheckoutTest extends AbstractSVNTest {

    public SVNWCDescriptor getInitialFS() {
        return null;
    }

    public String getDumpFile() {
        return "default.dump";
    }

    public ISVNTestOptions getOptions() {
        return ISVNTestOptions.EMPTY;
    }

    public void run() throws SVNException {
        fill();
        getEnvironment().checkout(getTrunk(), getWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
    }
}
