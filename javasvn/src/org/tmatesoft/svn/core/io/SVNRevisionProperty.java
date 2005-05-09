/*
 * SVNRevisionProperty.java, created 25.10.2004
 * 
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
 * Each revision can have some own system properties. Such properties are unversioned,
 * so there is always a risk to loose information when modifying revision property values. 
 * This class is a wrapper for revision properties.
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRevisionProperty {
    /**
     * svn:author property that's responsible for the username of the revision's author
     */
    public String AUTHOR = "svn:author";
    /**
     * svn:log property -  a property to store the log message attached to the revision
     * during commit operation
     */
    public String LOG = "svn:log";
    /**
     * svn:date property that is a datestamp representing the time that the
     * revision was created
     */
    public String DATE = "svn:date";
}
