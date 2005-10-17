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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;



/**
 * The <b>ISVNFileRevisionHandler</b> interface should be implemented for handling
 * information about file revisions  - that is file path, properties, revision properties
 * against a particular revision.
 * 
 * <p>
 * This interface is provided to a   
 * {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler) getFileRevisions()}
 * method of <b>SVNRepository</b> when getting file revisions (in particular, when annotating).
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNRepository
 * @see     org.tmatesoft.svn.core.SVNAnnotationGenerator
 */
public interface ISVNFileRevisionHandler {
    
    /**
     * Handles a file revision info.
     *  
     * @param  fileRevision 	a <b>SVNFileRevision</b> object representing file
     * 							revision information
     * @throws SVNException
     * @see 					SVNFileRevision
     */
	public void handleFileRevision(SVNFileRevision fileRevision) throws SVNException;
	
    /**
     * Handles a next diff window for a file (represented by a token) and
     * returns an output stream to write instructions and new text data for
     * the window. 
     * 
     * @param  token            a file path or name (or anything an implementor would
     *                          like to use for his own implementation)
     * @param  diffWindow       a diff window representing a delta chunk 
     * @return                  an output stream where instructions and new text data
     *                          for <code>diffWindow</code> will be written
     * @throws SVNException
	 */
    public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) throws SVNException;
    
    /**
     * Finilazes collecting deltas (diff windows) for a file. This method is
     * called just when all the diff windows for a file were handled. It may be here
     * where the collected deltas are applied.
     *  
     * @param  token          defines a path or a name (or anything an implementor would
     *                        like to use for his own implementation) of the file
     *                        for which finalizing steps should be performed
     * @throws SVNException
     */
    public void handleDiffWindowClosed(String token) throws SVNException;

}

