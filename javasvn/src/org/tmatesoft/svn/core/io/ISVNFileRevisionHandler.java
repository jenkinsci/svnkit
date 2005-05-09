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
 * <p>
 * The <code>ISVNFileRevisionHandler</code> public interface is an analogue (i.e. it 
 * incapsulates the similar functionality) to the native Subversion callback function
 * <code>*svn_ra_file_rev_handler_t</code> declared in the include/svn_ra.h file.
 * It is used with the {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)}
 * method (that is you should provide an <code>ISVNFileRevisionHandler</code> instance 
 * when going to call {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)}).
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 * @see SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)
 * @see org.tmatesoft.svn.core.io.ISVNDiffHandler
 */
public interface ISVNFileRevisionHandler extends ISVNDiffHandler {
    
    /**
     * <p>
     * Called within the @link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)
     * method.
     * </p>
     * @param fileRevision a <code>SVNFileRevision</code> object  
     * @see SVNFileRevision
     */
	public void handleFileRevision(SVNFileRevision fileRevision);

}

