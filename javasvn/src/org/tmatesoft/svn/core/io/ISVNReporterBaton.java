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
 * This interface is provided when manipulating with a working copy (updating it,
 * getting its status, getting checking it out from the repository). Used to make reports
 * by calling appropriate methods of {@link ISVNReporter} to describe a working copy
 * entries (their revision numbers, locations, etc.)
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 * @see ISVNReporter
 * @see SVNRepository
 */
public interface ISVNReporterBaton {
    /**
     * <p>
     * Used by an implementor to make reports about a working copy. 
     * </p>
     * @param reporter {@link ISVNReporter} to describe a working copy
     * @throws SVNException 
     */
    public void report(ISVNReporter reporter) throws SVNException;

}
