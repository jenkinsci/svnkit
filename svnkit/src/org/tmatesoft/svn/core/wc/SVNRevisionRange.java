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
package org.tmatesoft.svn.core.wc;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNRevisionRange {
	private SVNRevision myStartRevision;
	private SVNRevision myEndRevision;
	
	public SVNRevisionRange(SVNRevision startRevision,
			SVNRevision endRevision) {
		myStartRevision = startRevision;
		myEndRevision = endRevision;
	}

	public SVNRevision getStartRevision() {
		return myStartRevision;
	}

	public SVNRevision getEndRevision() {
		return myEndRevision;
	}
	
}
