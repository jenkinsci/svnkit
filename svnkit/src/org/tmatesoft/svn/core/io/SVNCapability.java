/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCapability {
	public static final SVNCapability DEPTH = new SVNCapability("depth");
	public static final SVNCapability MERGE_INFO = new SVNCapability("mergeinfo");
	public static final SVNCapability LOG_REVPROPS = new SVNCapability("log-revprops");
	public static final SVNCapability PARTIAL_REPLAY = new SVNCapability("partial-replay");
	
	private String myName;
	
	private SVNCapability(String name) {
		myName = name;
	}

	public String toString() {
        return myName;
    }

}
