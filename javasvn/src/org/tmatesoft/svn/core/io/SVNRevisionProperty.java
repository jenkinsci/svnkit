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
 * This class is a wrapper for revision properties.
 * 
 * <p>
 * Each revision can have some own system properties. Such properties are
 * unversioned, so there is always a risk to loose information when 
 * modifying revision property values. 
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRevisionProperty {
    /**
     * svn:author property that's responsible for the username of the revision's author
     */
    public static final String AUTHOR = "svn:author";
    /**
     * svn:log property -  a property to store the log message attached to the revision
     * during commit operation
     */
    public static final String LOG = "svn:log";
    /**
     * svn:date property that is a datestamp representing the time that the
     * revision was created
     */
    public static final String DATE = "svn:date";
    
    public static final String AUTOVERSIONED = "svn:autoversioned";
    public static final String ORIGINAL_DATE = "svn:original-date";
}
