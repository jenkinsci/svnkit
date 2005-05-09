/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 * @see ISVNReporter
 * @see SVNRepository
 */
public interface ISVNReporter {
    
    /**
	 *<p>
	 * Describe a working copy <code>path</code> as being at a particular
	 * <code>revision</code>.  
	 *
	 * If <code>startEmpty</code> is set and the <code>path</code> is a directory,
	 * the implementor should assume the directory has no entries or properties.
	 *
	 * This will "override" any previous <code>setPath()</code> calls made on parent
	 * paths. The <code>path</code> is relative to the URL specified for 
	 * {@link SVNRepository}.
	 * </p>
     * @param path a path in the working copy 
     * @param revision the working copy revision number
     * @param startEmpty set if there's no entries or properties
     * @throws SVNException
     * @see SVNRepository
     */
	public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException;
    /**
     * <p>
     * Describing a working copy <code>path</cdoe> as missing.
     * </p>
     * @param path a path in the working copy
     * @throws SVNException
     */
    public void deletePath(String path) throws SVNException;
    /**
     * <p>
     * Like {@link #setPath(String, long, boolean)}, but differs in that the 
     * <code>path</code> in the working copy (relative to the root of the report
     * driver) isn't a reflection of the path in the repository 
     * (relative to the URL specified when opening the RA layer - see 
     * {@link SVNRepository}), but is instead a reflection of a different
     * repository url at <code>revision</code>.
     *
     * If <code>startEmpty</code> is set and <code>path</code> is a directory,
     * the implementor should assume the directory has no entries or properties.
     * </p>
     * 
     * @param repository a working copy revision number
     * @param path a path in the working copy
     * @param revison the working copy revision number
     * @param startEmtpy set if there's no entries or properties
     * @throws SVNException
     * @see SVNRepository
     */
    public void linkPath(SVNRepositoryLocation repository, String path, String lockToken, long revison, boolean startEmtpy) throws SVNException;
    /**
     * <p>
     * Called when the state report is finished; any directories
     * or files not explicitly set (see {@link #setPath(String, long, boolean)})
     * are assumed to be at the baseline revision originally passed into
     * {@link SVNRepository#update(long, String, boolean, ISVNReporterBaton, ISVNEditor)
     * SVNRepository.update()}
     * </p>
     * @throws SVNException
     * @see SVNRepository
     */
    public void finishReport() throws SVNException;
    /**
     * <p>
     * If an error occurs during a report, this routine should cause the
     * filesystem transaction to be aborted & cleaned up.
     * </p>
     * @throws SVNException
     */
    public void abortReport() throws SVNException;
}
