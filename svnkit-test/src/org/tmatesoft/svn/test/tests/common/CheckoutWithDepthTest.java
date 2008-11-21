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
package org.tmatesoft.svn.test.tests.common;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.ext.DefaultSVNStatusScannerOptions;
import org.tmatesoft.svn.core.wc.ext.DefaultSVNStatusScannerQueue;
import org.tmatesoft.svn.core.wc.ext.ISVNPausable;
import org.tmatesoft.svn.core.wc.ext.ISVNPropertiesHandler;
import org.tmatesoft.svn.core.wc.ext.ISVNRemoteStatusHandler;
import org.tmatesoft.svn.core.wc.ext.ISVNStatusScannerOptions;
import org.tmatesoft.svn.core.wc.ext.ISVNStatusScannerQueue;
import org.tmatesoft.svn.core.wc.ext.SVNEntryReportProvider;
import org.tmatesoft.svn.core.wc.ext.SVNRemoteStatus;
import org.tmatesoft.svn.core.wc.ext.SVNRemoteStatusScanner;
import org.tmatesoft.svn.core.wc.ext.SVNStatusScanner;
import org.tmatesoft.svn.internal.wc.ext.SVNStatusEx;
import org.tmatesoft.svn.test.ISVNTestOptions;
import org.tmatesoft.svn.test.tests.AbstractSVNTest;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class CheckoutWithDepthTest extends AbstractSVNTest {

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor wc = new SVNWCDescriptor();
        wc.addFile("A");
        wc.addFile("A/a");
        wc.addFile("A/a/file", "this is A/a/file");
        wc.addFile("B");
        wc.addFile("B/b");
        wc.addFile("B/b/file", "this is B/b/file");
        return wc;
    }

    public String getDumpFile() {
        return null;
    }

    public ISVNTestOptions getOptions() {
        return null;
    }

    public void run() throws SVNException {
        fill();
        getEnvironment().checkout(getTrunk(), getSecondaryWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        getEnvironment().checkout(getTrunk(), getWC(), SVNRevision.HEAD, SVNDepth.EMPTY);
        getEnvironment().update(getFile("A"), SVNRevision.HEAD, SVNDepth.INFINITY);

        ISVNOptions opts = new DefaultSVNOptions();
        ISVNStatusScannerQueue scannerQueue = new DefaultSVNStatusScannerQueue();
        ISVNStatusScannerOptions scannerOptions = new DefaultSVNStatusScannerOptions(true, true, true, true, true, true, true);
        SVNStatusScanner localScanner = new SVNStatusScanner(opts, scannerQueue);
        final SVNEntryReportProvider entryProvider = new SVNEntryReportProvider(getWC());

        ISVNStatusHandler statusHandler = new ISVNStatusHandler() {

            public void handleStatus(SVNStatus status) throws SVNException {
                SVNStatusEx statuxExt = null;
                if (status instanceof SVNStatusEx) {
                    statuxExt = (SVNStatusEx) status;
                }
                entryProvider.addEntries(statuxExt);
            }
        };

        ISVNPropertiesHandler propHandler = new ISVNPropertiesHandler() {
            public void handleProperties(File file, SVNProperties baseProperties, SVNProperties versionedProperties) throws SVNException {
            }
        };

        ISVNPausable pausable = new ISVNPausable() {
            public void pause() throws SVNException {
            }
        };

        localScanner.doStatusDirectory(getWC(), SVNDepth.INFINITY, scannerOptions, statusHandler, propHandler, pausable);

        ISVNAuthenticationManager authManager = new BasicAuthenticationManager("user", "pass");
        ISVNRepositoryPool repositoryPool = new DefaultSVNRepositoryPool(authManager, null);
        SVNRemoteStatusScanner remoteScanner = new SVNRemoteStatusScanner(repositoryPool, opts);

        ISVNRemoteStatusHandler remoteStatusHandler = new ISVNRemoteStatusHandler() {
            public void handleStatus(SVNRemoteStatus status) throws SVNException {
                StringBuffer buffer = new StringBuffer();
                buffer.append("[Remote Status]");
                buffer.append("\n\tpath: ");
                buffer.append((status.getFile()));
                buffer.append("\n\tkind: ");
                buffer.append(status.getRemoteKind());
                buffer.append("\n\tremote revision: ");
                buffer.append(status.getRemoteRevision());
                buffer.append("\n\t: remote content");
                buffer.append(status.getRemoteContentsStatus());
                SVNTestDebugLog.log(buffer.toString());
            }
        };

        changeRemoteState();
        remoteScanner.doRemoteStatus(entryProvider, -1, SVNDepth.INFINITY, true, false, remoteStatusHandler);
    }

    private void changeRemoteState() throws SVNException {
        getEnvironment().setProperty(getSecondaryWC(), "propname", SVNPropertyValue.create("new prop balue"), SVNDepth.INFINITY);
        getEnvironment().commit(getSecondaryWC(), "property is set", SVNDepth.INFINITY);
    }
}
