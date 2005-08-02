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
package org.tmatesoft.svn.core.wc;

import java.util.Date;

/**
 * This is an interface for a handler that is to be invoked on each delta line
 * within the {@link SVNRepository#annotate(String, long, long, ISVNAnnotateHandler)
 * annotate} method.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 */
public interface ISVNAnnotateHandler {
	/**
	 * Handles each line that is to be annotated (that is to be provided in-line
	 * information about who added this line to a file and what revision it
	 * was done at).
	 * 
	 * @param date		the time moment when changes including this line were commited
	 * 					to the repository		
	 * @param revision	the revision the changes were commited to
	 * @param author	the person who did the changes
	 * @param line		a single file line that is a part of those changes 
	 */
	public void handleLine(Date date, long revision, String author, String line);

}
