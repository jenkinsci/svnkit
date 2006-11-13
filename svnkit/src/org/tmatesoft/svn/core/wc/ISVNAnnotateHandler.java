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

import java.util.Date;

import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNAnnotateHandler</b> interface should be implemented to be further
 * provided to <b>SVNLogClient</b>'s <b>doAnnotate()</b> methods for processing
 * annotation information per each text line.  
 * 
 * <p>
 * Here's an example code snippet:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNLogClient;
 * ...
 * 
 *     SVNLogClient logClient;
 *     ...
 *     
 *     logClient.doAnnotate(<span class="javakeyword">new</span> File(<span class="javastring">"path/to/WC/file"</span>), SVNRevision.HEAD, SVNRevision.create(0), 
 *                          SVNRevision.HEAD, <span class="javakeyword">new</span> ISVNAnnotateHandler(){
 *                              <span class="javakeyword">public void</span> handleLine(Date date, <span class="javakeyword">long</span> revision, 
 *                                                            String author, String line){
 *                                  <span class="javacomment">//implement this method as you wish, for example:</span>
 *                                  System.out.println(revision + 
 *                                                     <span class="javastring">"  "</span> + 
 *                                                     author + 
 *                                                     <span class="javastring">"  "</span> + 
 *                                                     date + 
 *                                                     <span class="javastring">"  "</span> + 
 *                                                     line);
 *                              }
 *                          });
 *     ...</pre><br />
 * 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see     SVNLogClient
 */
public interface ISVNAnnotateHandler {
	/**
	 * Handles per line annotation information - that is information about 
     * who last committed (changed) this line, the revision and timestamp when it was last 
     * committed. 
	 * 
	 * @param date		the time moment when changes to <code>line</code> were commited
	 * 					to the repository		
	 * @param revision	the revision the changes were commited to
	 * @param author	the person who did those changes
	 * @param line		a text line of the target file (on which 
     *                  {@link SVNLogClient#doAnnotate(File, SVNRevision, SVNRevision, SVNRevision, ISVNAnnotateHandler) doAnnotate()}
     *                  was invoked)
     * @throws SVNException  
	 */
	public void handleLine(Date date, long revision, String author, String line) throws SVNException;

}
