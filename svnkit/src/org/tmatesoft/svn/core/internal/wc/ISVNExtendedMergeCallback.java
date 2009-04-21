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
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;

/**
 * The <b>ISVNExtendedMergeCallback</b> interface defines methods allowing {@link org.tmatesoft.svn.core.wc.SVNDiffClient} to handle copied or moved
 * files while merge operation
 *
 * @author TMate Software Ltd.
 * @version 1.2
 * @since 1.2
 */
public interface ISVNExtendedMergeCallback {

    public SVNURL[] getTrueMergeTargets(SVNURL sourceURL, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetURL, long targetRevision, SVNEditorAction action) throws SVNException;

    public SVNCopyTask getTargetCopySource(SVNURL sourceURL, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetURL, long targetRevision) throws SVNException ;

    public SVNURL transformLocation(SVNURL sourceURL, long sourceRevision, long targetRevision) throws SVNException;
}
