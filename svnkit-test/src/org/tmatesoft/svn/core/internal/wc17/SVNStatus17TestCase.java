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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNStatus17TestCase extends TestCase {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public SVNStatus17TestCase() {
        super("SVNStatus17");
    }

    public void testLocalStatus17() throws SVNException {
        LOGGER.info("testLocalStatus17");
        final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
        long revision = client.doStatus(new File(""), SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, new StatusHandler(false), null);
    }

    public void testLocalStatus17Added() throws SVNException, IOException {
        LOGGER.info("testLocalStatus17Added");
        File added = new File("added.txt");
        added.createNewFile();
        try {
            final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
            long revision = client.doStatus(new File(""), SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, new StatusHandler(false), null);
        } finally {
            SVNFileUtil.deleteFile(added);
        }
    }

    public void testLocalStatus17Modified() throws SVNException, IOException {
        LOGGER.info("testLocalStatus17Modified");
        File modify = new File("file1.txt");
        PrintWriter output = new PrintWriter(modify);
        try {
            output.print("\nmodified\n");
        } finally {
            output.close();
        }
        final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
        long revision = client.doStatus(new File(""), SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, new StatusHandler(false), null);
    }

    public void testLocalStatus17Deleted() throws SVNException {
        LOGGER.info("testLocalStatus17Deleted");
        System.gc();
        File delete = new File("file1.txt");
        if (SVNFileUtil.deleteFile(delete)) {
            final SVNStatusClient17 client = new SVNStatusClient17(new BasicAuthenticationManager("test", "test"), new DefaultSVNOptions(null, true));
            long revision = client.doStatus(new File(""), SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, new StatusHandler(false), null);
        }
    }

}
