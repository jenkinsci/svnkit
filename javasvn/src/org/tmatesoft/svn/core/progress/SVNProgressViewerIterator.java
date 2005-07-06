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
package org.tmatesoft.svn.core.progress;

import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.util.SVNAssert;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public final class SVNProgressViewerIterator implements ISVNProgressViewer, Iterator {

	private final Iterator iterator;
	private final ISVNProgressViewer viewer;
	private final int count;

	private int index = -1;

	public SVNProgressViewerIterator(Collection collection, ISVNProgressViewer viewer) {
		SVNAssert.assertNotNull(collection);
		SVNAssert.assertNotNull(viewer);

		this.iterator = collection.iterator();
		this.count = collection.size();
		this.viewer = viewer;
	}

	public boolean hasNext() {
		return iterator.hasNext();
	}

	public Object next() {
		index++;
		setProgress(0.0);
		return iterator.next();
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}

	public void setProgress(double value) {
		SVNAssert.assertTrue(0.0 <= value && value <= 1.0);
		viewer.setProgress((index + value) / count);
	}

	public void checkCancelled() throws SVNProgressCancelledException {
		viewer.checkCancelled();
	}
}
