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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.wc.SVNCommitItem;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNCommitter17 implements ISVNCommitPathHandler {

    private Map<String, SVNCommitItem> committables;
    private String repositoryRoot;
    private Collection tmpFiles;
    private Map<File, SVNChecksum> md5Checksums;
    private Map<File, SVNChecksum> sha1Checksums;

    public SVNCommitter17(Map<String, SVNCommitItem> committables, String repositoryRoot, Collection tmpFiles, Map<File, SVNChecksum> md5Checksums, Map<File, SVNChecksum> sha1Checksums) {
        this.committables = committables;
        this.repositoryRoot = repositoryRoot;
        this.tmpFiles = tmpFiles;
        this.md5Checksums = md5Checksums;
        this.sha1Checksums = sha1Checksums;
    }

    public static SVNCommitInfo commit(Collection tmpFiles, Map<String, SVNCommitItem> committables, String repositoryRoot, ISVNEditor commitEditor, Map<File, SVNChecksum> md5Checksums,
            Map<File, SVNChecksum> sha1Checksums) throws SVNException {
        SVNCommitter17 committer = new SVNCommitter17(committables, repositoryRoot, tmpFiles, md5Checksums, sha1Checksums);
        SVNCommitUtil.driveCommitEditor(committer, committables.keySet(), commitEditor, -1);
        committer.sendTextDeltas(commitEditor);
        return commitEditor.closeEdit();
    }

    public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    private void sendTextDeltas(ISVNEditor commitEditor) {
        // TODO
        throw new UnsupportedOperationException();
    }

}
