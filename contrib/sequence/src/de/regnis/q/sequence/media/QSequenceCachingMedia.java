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

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceCachingMedia extends QSequenceIntMedia {

	// Fields =================================================================

	private final QSequenceCachingMediaSymbolMap symbolMap;
	private final int[] leftSymbols;
	private final int[] rightSymbols;

	// Setup ==================================================================

	public QSequenceCachingMedia(QSequenceCachableMedia media, QSequenceCanceller canceller) throws QSequenceException {
		super(canceller);

		this.symbolMap = new QSequenceCachingMediaSymbolMap(media.getLeftLength() + media.getRightLength());
		this.leftSymbols = symbolMap.createSymbols(media, new QSequenceCachableMediaLeftGetter());
		this.rightSymbols = symbolMap.createSymbols(media, new QSequenceCachableMediaRightGetter());
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return leftSymbols.length;
	}

	public int getRightLength() {
		return rightSymbols.length;
	}

	public boolean equals(int leftIndex, int rightIndex) throws QSequenceCancelledException {
		checkCancelled();
		return leftSymbols[leftIndex] == rightSymbols[rightIndex];
	}

	// Accessing ==============================================================

	public int getSymbolCount() {
		return symbolMap.getSymbolCount();
	}

	public int[] getLeftSymbols() {
		return leftSymbols;
	}

	public int[] getRightSymbols() {
		return rightSymbols;
	}
}