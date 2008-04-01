/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence.media;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceCachableMediaRightGetter implements QSequenceCachableMediaGetter {

	// Implemented ============================================================
	
	public int getMediaLength(QSequenceCachableMedia media) {
		return media.getRightLength();
	}

	public Object getMediaObject(QSequenceCachableMedia media, int index) throws QSequenceException {
		return media.getMediaRightObject(index);
	}
}