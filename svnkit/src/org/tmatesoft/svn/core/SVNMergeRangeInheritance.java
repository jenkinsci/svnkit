/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeRangeInheritance {
    public static SVNMergeRangeInheritance IGNORE_INHERITANCE = new SVNMergeRangeInheritance();
    public static SVNMergeRangeInheritance EQUAL_INHERITANCE = new SVNMergeRangeInheritance();
    public static SVNMergeRangeInheritance ONLY_INHERITABLE = new SVNMergeRangeInheritance();

    private SVNMergeRangeInheritance() {
    }
}
