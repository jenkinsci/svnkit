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

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusEditor17 {

    public SVNStatusEditor17(ISVNOptions options, SVNWCContext wcContext, SVNAdminAreaInfo17 info, boolean includeIgnored, boolean reportAll, SVNDepth depth, ISVNStatusHandler realHandler) {
    }

    public SVNCommitInfo closeEdit() {
        return null;
    }

    public long getTargetRevision() {
        return 0;
    }

    public void setFileProvider(ISVNStatusFileProvider filesProvider) {
    }

}
