/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;

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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNMerger {
    
    /**
     * Generates deltas given the two text source files to be compared, applies
     * the deltas against a local file and writes the merge result to the 
     * given {@link java.io.OutputStream}.
     * 
     * @param  baseFile     the earliest file of the two to be compared to
     *                      generate delta  
     * @param  localFile    a local WC file against which the delta
     *                      should be applied
     * @param  latestFile   the latest file of the two to be compared to
     *                      generate delta
     * @param  dryRun       if <span class="javakeyword">true</span> - only 
     *                      try to merge (to find out if an operation can succeed) 
     *                      without actual merging
     * @param  options      diff options to apply
     * @param  out          an output stream where the result file contents
     *                      should be written to
     * @return              a result status of the operation; if success - 
     *                      returns {@link SVNStatusType#MERGED}
     * @throws SVNException
     */
    public SVNStatusType mergeText(File baseFile, File localFile, File latestFile, boolean dryRun, SVNDiffOptions options, OutputStream out) throws SVNException;

    /**
     * Generates deltas given two binary files, applies
     * the deltas against a local file and writes the merge result to the 
     * given {@link java.io.OutputStream}.
     * 
     * @param  baseFile     the earliest file of the two to generate deltas  
     * @param  localFile    a local WC file against which the deltas
     *                      should be applied
     * @param  latestFile   the latest file of the two to generate deltas
     * @param  dryRun       if <span class="javakeyword">true</span> - only 
     *                      try to merge (to find out if an operation can succeed) 
     *                      without actual merging
     * @param  out          an output stream where the result file contents
     *                      should be written to
     * @return              a result status of the operation; if success - 
     *                      returns {@link SVNStatusType#MERGED}
     * @throws SVNException
     */
    public SVNStatusType mergeBinary(File baseFile, File localFile, File latestFile, boolean dryRun, OutputStream out) throws SVNException;
}
