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
public class QSequenceMediaDummyIndexTransformer implements QSequenceMediaIndexTransformer {

	// Fields =================================================================

	private final int mediaLeftLength;
	private final int mediaRightLength;

	// Setup ==================================================================

	public QSequenceMediaDummyIndexTransformer(QSequenceMedia media) {
		this.mediaLeftLength = media.getLeftLength();
		this.mediaRightLength = media.getRightLength();
	}

	public QSequenceMediaDummyIndexTransformer(int mediaLeftLength, int mediaRightLength) {
		this.mediaLeftLength = mediaLeftLength;
		this.mediaRightLength = mediaRightLength;
	}

	// Implemented ============================================================

	public int getMediaLeftIndex(int index) {
		return index;
	}

	public int getMediaRightIndex(int index) {
		return index;
	}

	public int getMediaLeftLength() {
		return mediaLeftLength;
	}

	public int getMediaRightLength() {
		return mediaRightLength;
	}
}