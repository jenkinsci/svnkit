/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNStatus17TestCase extends TestCase {

    public SVNStatus17TestCase() {
        super("SVNStatus17");
    }

    public void testLocalStatus17() throws SVNException {
        final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
        final SVNStatus status = client.doStatus(new File(""), false);
        assert (status != null);
    }

    public void testLocalStatus17Recursive() throws SVNException {
        final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
        long revision = client.doStatus(new File(""), SVNRevision.WORKING, SVNDepth.INFINITY, false, false, false, false, new StatusHandler(false), null);
    }

}
