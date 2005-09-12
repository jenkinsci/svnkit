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

package org.tmatesoft.svn.core;

import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * 
 * The public interface <code>ISVNLogEntryHandler</code> is implemented to
 * handle  log entries (<code>SVNLogEntry</code> objects). It declares the
 * only one method <code>handleLogEntry(SVNLogEntry logEntry)<code> and should
 * be provided into {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
 * SVNRepository.log(String[], long, long, boolean, boolean, ISVNLogEntryHandler}.
 * 
 * @version 	1.0
 * @author 		TMate Software Ltd.
 * @see 		SVNRepository
 * @see 		SVNLogEntry
 */
public interface ISVNLogEntryHandler {
    /**
     * 
     * Handles the log entry (<code>SVNLogEntry</code> object) passed.
     * 
     * @param logEntry 		a <code>SVNLogEntry</code> instance to be handled.
     * @throws SVNException 
     */
    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException;

}
