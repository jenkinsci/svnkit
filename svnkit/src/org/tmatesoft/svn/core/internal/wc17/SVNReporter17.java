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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.util.ISVNDebugLog;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNReporter17 implements ISVNReporterBaton {


    public SVNReporter17(File path, boolean restoreFiles,
            boolean useDepthCompatibilityTrick, SVNDepth depth, boolean lockOnDemand,
            boolean isStatus, boolean isHonorDepthExclude, ISVNDebugLog log) {
    }

    public void report(ISVNReporter reporter) throws SVNException {
    }

    public int getReportedFilesCount() {
        return 0;
    }

    public int getTotalFilesCount() {
        return 0;
    }

}
