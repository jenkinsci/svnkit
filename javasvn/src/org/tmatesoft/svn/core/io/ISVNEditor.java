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

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * The <code>ISVNEditor</code> interface provides methods that should be used
 * to follow  repository server's instructions on how the client's command (update,
 * switch, checkout, status, diff, commit) should be handled depending on possible 
 * changes that have been applied to the client's working copy and/or its origin in 
 * the repository . 
 * 
 * <p>
 * <b>In update-like operations:</b>
 * <p>
 * When having the working copy revisions described to the server (by making reports
 * with the help of <code>ISVNReporter</code> from within 
 * {@link ISVNReporterBaton#report(ISVNReporter) ISVNReporterBaton.report()}) the
 * server now knows of the working copy state (revisions of all its entries) and 
 * starts performing the client's requested operation. This means the server sends
 * commands to the client's Repository Access Layer which parses them and translates
 * to appropriate calls of <code>ISVNEditor</code>'s methods.
 * 
 * <p>
 * <b>In a commit:</b>
 * <p>
 * A commit editor is used to describe a repository server all changes done against
 * the BASE-revision of the working copy.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see		SVNRepository
 */
public interface ISVNEditor {
    /**
     * Specifies the target revision number a working copy (WC) to be updated to
     * (or the revision number the WC would have if it is updated).
     * 
     * <p>
     * This routine is not needed for a commit editor as the WC will be driven 
     * to a new revision at the end of the commit (after {@link #closeEdit() 
     * closeEdit()} returns).
     * 
     * @param  revision			the revision number the WC is to have 
     * 							(may be potentially) as a result of the client's
     * 							command
     * @throws SVNException
     */
    public void targetRevision(long revision) throws SVNException;
    
    /**
     * Starts processing from the parent directory the command was run for.
     * The <code>revision</code> is a revision number for this directory. All
     * property changes as well as entries adding/deletion will be applied to this
     * root directory.
     * 
     * @param  revision			a revision number for the command parent directory 			
     * @throws SVNException
     */
    public void openRoot(long revision) throws SVNException;
    
    /**
     * Performs actions on deleting (marked as deleted) an entry at a <code>path</code>
     * at a <code>revision</code>. 
     * 
     * <p>
     * If the command is <i>commit</i> this method describes a repository
     * server that the entry at the <code>path</code> and at the <code>revision</code>
     * was deleted by the client. If it's a kind of an <i><code>update</code></i>
     * the routine performs the fact of deletion of this entry in the repository itself. 
     * 
     * @param  path			a relative pathway within a working copy directory where the 
     * 						command was	initiated by the client		
     * @param  revision		the revision number of <code>path</code>
     * @throws SVNException 
     */
    public void deleteEntry(String path, long revision) throws SVNException;
    
    /**
     * Marks the direcory at a <code>path</code> as absent (being still versioned but
     * currently missing in a working copy).
     * 
     * @param  path				a relative path within a working copy
     * @throws SVNException
     */
    public void absentDir(String path) throws SVNException;
    
    /**
     * Marks the file at a <code>path</code> as absent (being still versioned but
     * currently missing in a working copy).
     * 
     * @param  path				a relative path within a working copy
     * @throws SVNException
     */
    public void absentFile(String path) throws SVNException;
    
    /**
     * "Adds" a directory to a working copy.
     * 
     * <p>
     * Performs adding a directory to a working copy (WC) (in <i>update</i>-like 
     * commands) or describes adding a directory to a repository during a <i>commit</i>;
     * in a <code>status</code> command (only when the WC is being compared with the 
     * repository) it says of the fact of adding a directory to the repository origin
     * of the WC and how it was exactly added (just added/replaced/branched).
     * 
     * <p>
     * If the current running command this editor is used for is a <i>commit</i> and 
     * the directory is branched off from another directory in the repository then 
     * <code>copyFromPath</code> and <code>copyFromRevision</code> are the path 
     * (relative to the repository root) and the revision respectively for the 
     * original directory being copied.
     *   
     * @param  path					a directory path within a working copy relative
     * 								to the currently "opened" directory  
     * @param  copyFromPath			a path where the directory is to be copied from
     * 								(when branching); relative to the repository root
     * @param  copyFromRevision		the revision of the original directory located at
     * 								the <code>copyFromPath</code> (when branching)
     * @throws SVNException
     */
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException;
    
    /**
     * Performs going downwards to an inner directory entry of a working copy for
     * further modifications of its properties and/or entries.
     * 
     * <p>
     * If the current running command this editor is used for is a <i>commit</i> then
     * the <code>revision</code> is used for describing the directory's revision to
     * a repository server.
     * <p>
     * If the <code>path</code> is <code>""</code> then the 
     * {@link #openRoot(long) openRoot()} is actually called.
     * 
     * @param path			a path within a working copy relative to the currently
     * 						"opened" directory 
     * @param revision		the revision of the directory being opened
     * @throws SVNException
     */
	public void openDir(String path, long revision) throws SVNException;
    
	/**
     * Changes the value of a property of the currently "opened" directory.
     * 
     * @param  name				the name of a property to be changed
     * @param  value			new property value
     * @throws SVNException
     * @see 					#openDir(String, long)
     */
    public void changeDirProperty(String name, String value) throws SVNException;

    /**
     * Performs going upwards from the currently "opened" directory fixing all
     * changes of its properties and/or entries.
     * 
     * @throws SVNException
     */
    public void closeDir() throws SVNException;
    
    /**
     * "Adds" a file to a working copy.
     * 
     * <p>
     * Performs adding a file into the currently "opened" directory for a working copy 
     * (WC) (in <i>update</i>-like commands) or describes adding a file to a repository 
     * during a <i>commit</i>; in a <code>status</code> command (only when the WC is 
     * being compared with the repository) it says of the fact of adding a file to the
     * repository origin of the WC and how it was exactly added (just added/replaced/
     * branched).
     * 
     * <p>
     * If the current running command this editor is used for is a <i>commit</i> and 
     * the file is branched off from another file in the repository then 
     * <code>copyFromPath</code> and <code>copyFromRevision</code> are the path 
     * (relative to the repository root) and the revision respectively for the 
     * original file being copied.
     * 
     * @param  path					a file path relative to the currently "opened"
     * 								directory				
     * @param  copyFromPath			a path where the file is to be copied from
     * 								(when branching); relative to the repository root
     * @param  copyFromRevision		the revision of the original file located at
     * 								the <code>copyFromPath</code> (when branching)
     * @throws SVNException
     */
    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException;
    
    /**
     * "Opens" a file in the currently "opened" directory - that is all property 
     * changes and/or any text delta (if the file has been changed in a repository)
     * will be applied to this file until it's closed.
     * 
     * <p>
     * In a <i>commit</i> command as well as in a <i>status</i> one the 
     * <code>revision</code> is used to describe the revision of the file.
     * 
     * @param  path				a file path relative to the currently "opened" 
     * 							directory		
     * @param  revision			a file revision number
     * @throws SVNException
     */
    public void openFile(String path, long revision) throws SVNException;
    
    /**
     * Applies a text delta (if any) to the currently "opened" file which contents 
     * differ from its origin in the repository. To be sure that the delta will
     * be applied correctly the server must make certain of the working copy file
     * contents are the same which the delta was evaluated upon. So, the server
     * transmits a checksum which the client side will compare with its own one 
     * evaluated upon the file contents. If both match each other - the delta
     * is applied, else - possibly file contents were corrupted, an exception is
     * thrown. 
     * 
     * 
     *  
     * @param  baseChecksum		a server's checksum for the file to be modified
     * @throws SVNException		server's and client's checksums differ
     */
    public void applyTextDelta(String path, String baseChecksum) throws SVNException;
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException;

    public void textDeltaEnd(String path) throws SVNException;

    /**
     * Changes the value of a property of the currently "opened" file.
     * 
     * @param  name				a file property name
     * @param  value			a new value for the property
     * @throws SVNException
     */
    public void changeFileProperty(String path, String name, String value) throws SVNException;
    
    /**
     * "Closes" the currently opened file fixing all changes in its properties
     * and/or contents. If this file was applied any delta the server is to check
     * if it was modified properly. It sends a checksum evaluated upon post-diffed
     * file contents to the client's side where this checksum is compared with the
     * one evaluated upon the working copy file contents. If they match each other
     * then the file state is ok, otherwise its contents could have been possibly
     * corrupted, an exception is thrown.
     * 
     * @param  textChecksum		a server's checksum for the modified file 
     * @throws SVNException		if server's and client's checksums differ
     */
    public void closeFile(String path, String textChecksum) throws SVNException;
    
    /**
     * Closes this editor completing the whole operation the editor
     * was used for (that is updating, committing, checking out, getting status or
     * diff). As a result it returns the last commit information. 
     * 
     * @return 					information of when a working copy was last commited
     * @throws SVNException
     * @see 					SVNCommitInfo
     */
    public SVNCommitInfo closeEdit() throws SVNException;
    
    /**
     * Aborts the current running editor due to errors occured.
     * 
     * <p>
     * If an error occurs during the work of this editor, this routine should cause 
     * the filesystem transaction to be aborted & cleaned up.
     * 
     * @throws SVNException
     */
    public void abortEdit() throws SVNException;
}
