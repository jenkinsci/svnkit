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
 * The <b>ISVNExtendedMergeCallback</b> interface defines methods allowing {@link org.tmatesoft.svn.core.wc.SVNDiffClient} to handle 
 * copied or moved files during a merge operation.
 *
 * @author  TMate Software Ltd.
 * @version 1.3
 * @since   1.3
 */
public interface ISVNExtendedMergeCallback {

    /**
     * 
     * @param  sourceURL
     * @param  sourceRevision
     * @param  sourceMergeFromRevision
     * @param  sourceMergeToRevision
     * @param  targetURL
     * @param  targetRevision
     * @param  action
     * @return urls
     * @throws SVNException
     * @since  1.3
     */
    public SVNURL[] getTrueMergeTargets(SVNURL sourceURL, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, 
            SVNURL targetURL, long targetRevision, SVNEditorAction action) throws SVNException;

    /**
     * 
     * @param  sourceURL
     * @param  sourceRevision
     * @param  sourceMergeFromRevision
     * @param  sourceMergeToRevision
     * @param  targetURL
     * @param  targetRevision
     * @return copy task
     * @throws SVNException
     * @since  1.3
     */
    public SVNCopyTask getTargetCopySource(SVNURL sourceURL, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, 
            SVNURL targetURL, long targetRevision) throws SVNException ;

    /**
     * 
     * @param  sourceURL
     * @param  sourceRevision
     * @param  targetRevision
     * @return url
     * @throws SVNException
     * @since  1.3
     */
    public SVNURL transformLocation(SVNURL sourceURL, long sourceRevision, long targetRevision) throws SVNException;
}
