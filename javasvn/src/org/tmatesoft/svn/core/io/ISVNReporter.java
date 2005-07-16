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

import org.tmatesoft.svn.core.SVNException;

/**
 * This is a basic interface for implementors to describe a working copy state.
 * 
 * <p>
 * <code>ISVNReporter</code> implementations are handled by 
 * <code>ISVNReporterBaton</code> to describe a subset
 * (or possibly all) of the working copy (WC) to the Repository Access Layer for the
 * purposes of an update, switch, status, or diff operation.
 * 
 * <p>
 * Paths for report calls are relative to the target of the operation (that is the 
 * directory where the command was run). Report calls must be made in depth-first 
 * order: parents before children, all children of a parent before any
 * siblings of the parent.  The first report call must be a 
 * {@link #setPath(String, String, long, boolean) setPath()} with a path argument of
 * "" and a valid revision.  (If the target of the operation is locally deleted or 
 * missing, use the WC root's revision.)  If the target of the operation is 
 * deleted or switched relative to the WC root, follow up the initial 
 * {@link #setPath(String, String, long, boolean) setPath()} call with a
 * {@link #linkPath(SVNRepositoryLocation, String, String, long, boolean) linkPath()}
 * or {@link #deletePath(String) deletePath()} call with a path argument of "" to
 * indicate that.  In no other case may there be two report
 * descriptions for the same path.  If the target of the operation is
 * a locally added file or directory (which previously did not exist),
 * it may be reported as having revision 0 or as having the parent
 * directory's revision.
 *
 * <p>
 * <b>NOTE:</b> the WC root is the root directory of the entire working copy that was
 * checked out from the repository.
 *  
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNReporterBaton
 * @see 	SVNRepository
 */
public interface ISVNReporter {

	/**
	 *<p>
	 * Describes a working copy <code>path</code> as being at a particular
	 * <code>revision</code>.  
	 *
	 * If <code>startEmpty</code> is set and the <code>path</code> is a directory,
	 * the implementor should assume the directory has no entries or properties.
	 *
	 * This will "override" any previous <code>setPath()</code> calls made on parent
	 * paths. The <code>path</code> is relative to the <code>URL</code> specified for 
	 * <code>SVNRepository</code>.
	 * 
     * @param  path				a path within the working copy 
     * @param  lockToken		if not <code>null</code>, it is the lock token (lock id
     * 							in other words) for the <code>path</code>
     * @param  revision 		a working copy revision number
     * @param  startEmpty 		<code>true</code> and if the <code>path</code> is a 
     * 							directory - an implementer should assume that the 
     * 							directory has no entries or properties
     * @throws SVNException		if the <code>revision</code> is invalid (<0) or there's
     * 							no such revision at all; also if a connection failure
     *  						occured
     * 
     */
	public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException;

	/**
     * 
     * Describes a working copy <code>path</cdoe> as missing (deleted from the WC).
     * 
     * @param  path 			a path within the working copy
     * @throws SVNException		if a failure in connection occured.
     */
    public void deletePath(String path) throws SVNException;

    /**
     * Like {@link #setPath(String, String, long, boolean)}, but differs in 
     * that the <code>path</code> in the working copy (relative to the root
     * of the report driver) isn't a reflection of the path in the repository 
     * (relative to the <code>URL</code> specified for  
     * {@link SVNRepository}), but is instead a reflection of a different
     * repository <code>URL</code> at a <code>revision</code>.
     * 
     * <p>
     * If <code>startEmpty</code> is set and the <code>path</code> is a directory,
     * the implementor should assume the directory has no entries or properties.
     * 
     * @param  repository 	a working copy revision number
     * @param  path 		a path within the working copy
     * @param  revison 		a working copy revision number
     * @param  lockToken	if not <code>null</code>, it is the lock token (lock id
     * 						in other words) for the <code>path</code>
     * @param  startEmtpy 	<code>true</code> and if the <code>path</code> is a 
     * 						directory - an implementer should assume that the 
     * 						directory has no entries or properties
     * @throws SVNException if the <code>revision</code> is invalid (<0) or there's
     * 						no such revision at all; also if a connection failure
     *  					occured
     */

    public void linkPath(SVNRepositoryLocation repository, String path, String lockToken, long revison, boolean startEmtpy) throws SVNException;
    
    /**
     * Finishes describing a working copy.
     * 
     * <p>
     * Called when the state report is finished; any directories
     * or files not explicitly set (see {@link #setPath(String, String, long, boolean)})
     * are assumed to be at the baseline revision originally passed into 
     * SVNRepository.update() (switch(), status(), diff()).
     * 
     * @throws SVNException 	if a failure in connection occured
     * @see	   SVNRepository
     */
    public void finishReport() throws SVNException;
    
    /**
     * Aborts the current running report due to errors occured.
     * 
     * <p>
     * If an error occurs during a report, this routine should cause the
     * filesystem transaction to be aborted & cleaned up.
     * 
     * @throws SVNException		if a failure in connection occured
     */
    public void abortReport() throws SVNException;
}
