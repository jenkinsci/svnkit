/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
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
import java.util.Collection;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.io.ISVNEditor;


/**
 * @version 1.4
 * @author  TMate Software Ltd.
 */
public class SVNCommitter17 {

    public static SVNCommitInfo commit(Collection tmpFiles, Map committables, String repositoryRoot, ISVNEditor commitEditor, Map<File, SVNChecksum> md5Checksums, Map<File, SVNChecksum> sha1Checksums) {
        // TODO
        throw new UnsupportedOperationException();
    }

}
