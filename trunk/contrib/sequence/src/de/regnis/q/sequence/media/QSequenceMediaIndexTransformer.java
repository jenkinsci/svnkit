/*
 * ====================================================================
 * Copyright (c) 2004 Marc Strapetz, marc.strapetz@smartsvn.com. 
 * All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence.media;

/**
 * @author Marc Strapetz
 */
public interface QSequenceMediaIndexTransformer {

	int getMediaLeftIndex(int index);

	int getMediaRightIndex(int index);

	int getMediaLeftLength();

	int getMediaRightLength();
}