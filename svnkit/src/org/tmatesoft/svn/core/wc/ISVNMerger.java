/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;


/**
 * <b>ISVNMerger</b> is the merger driver interface used by SVNKit in 
 * merging operations. 
 * 
 * <p>
 * Merger drivers are created by a merger factory implementing the 
 * {@link ISVNMergerFactory} interface. Read more about that interface to
 * find out how to get a default implementation of <b>ISVNMerger</b>.
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public interface ISVNMerger {
    
    public SVNMergeResult merge(SVNMergeFileSet files, boolean dryRun, SVNDiffOptions options) throws SVNException;
    
    public SVNMergeAction getMergeAction(SVNMergeFileSet files) throws SVNException;
    
    public SVNMergeResult processMergedFiles(SVNMergeFileSet files) throws SVNException;
}
