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
 * This interface is provided when manipulating with a working copy (updating it,
 * getting its status, checking it out from a repository). Used to make reports
 * by calling appropriate methods of {@link ISVNReporter} to describe a working copy
 * entries (their revision numbers, locations, etc.)
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNReporter
 * @see 	SVNRepository
 */
public interface ISVNReporterBaton {
    /**
     * Used by an implementor to make reports about a working copy. 
     * 
     * @param  reporter 		a reporter to describe a working copy
     * @throws SVNException
     * @see						ISVNReporter 
     */
    public void report(ISVNReporter reporter) throws SVNException;

}

