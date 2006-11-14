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

package org.tmatesoft.svn.core;

import java.util.Collection;
import java.util.HashSet;

/**
 * The <b>SVNRevisionProperty</b> class represents revision properties - those
 * unversioned properties supported by Subversion.
 * 
 * <p>
 * Revision properties are unversioned, so there is always a risk to 
 * lose information when modifying revision property values. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRevisionProperty {
    
    private static final Collection REVISION_PROPS = new HashSet();

    static {
        REVISION_PROPS.add(SVNRevisionProperty.AUTHOR);
        REVISION_PROPS.add(SVNRevisionProperty.LOG);
        REVISION_PROPS.add(SVNRevisionProperty.DATE);
        REVISION_PROPS.add(SVNRevisionProperty.ORIGINAL_DATE);
        REVISION_PROPS.add(SVNRevisionProperty.AUTOVERSIONED);
    }
    
    /**
     * Says if the given revision property name is really a valid
     * revision property name.  
     *  
     * @param   name a property name
     * @return  <span class="javakeyword">true</span> if it's a 
     *          revision property name, <span class="javakeyword">false</span>
     *          otherwise
     */
    public static boolean isRevisionProperty(String name) {
        return name != null && REVISION_PROPS.contains(name);
    }

    /**
     * An <span class="javastring">"svn:author"</span> revision 
     * property (that holds the name of the revision's author).
     */
    public static final String AUTHOR = "svn:author";
    /**
     * An <span class="javastring">"svn:log"</span> revision property -  
     * the one that stores a log message attached to a revision
     * during a commit operation.
     */
    public static final String LOG = "svn:log";
    /**
     * An <span class="javastring">"svn:date"</span> revision property 
     * that is a date & time stamp representing the time when the
     * revision was created.
     */
    public static final String DATE = "svn:date";
    
    /**
     * <span class="javastring">"svn:sync-lock"</span> revision property.
     * @since 1.1, new in Subversion 1.4 
     */
    public static final String LOCK = "svn:sync-lock";
    
    /**
     * <span class="javastring">"svn:sync-from-url"</span> revision property.
     * @since 1.1, new in Subversion 1.4 
     */
    public static final String FROM_URL = "svn:sync-from-url";

    /**
     * <span class="javastring">"svn:sync-from-uuid"</span> revision property.
     * @since 1.1, new in Subversion 1.4 
     */
    public static final String FROM_UUID = "svn:sync-from-uuid";
    
    /**
     * <span class="javastring">"svn:sync-last-merged-rev"</span> revision property.
     * @since 1.1, new in Subversion 1.4 
     */
    public static final String LAST_MERGED_REVISION = "svn:sync-last-merged-rev";

    /**
     * <span class="javastring">"svn:sync-currently-copying"</span> revision property.
     * @since 1.1, new in Subversion 1.4 
     */
    public static final String CURRENTLY_COPYING = "svn:sync-currently-copying";
    
    public static final String AUTOVERSIONED = "svn:autoversioned";
    
    public static final String ORIGINAL_DATE = "svn:original-date";
}
