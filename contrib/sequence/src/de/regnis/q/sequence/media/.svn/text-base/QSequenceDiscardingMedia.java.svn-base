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
public class QSequenceDiscardingMedia implements QSequenceMedia, QSequenceMediaIndexTransformer {

	// Fields =================================================================

	private final QSequenceIntMedia media;
	private final QSequenceCanceller canceller;
	private final QSequenceDiscardingMediaBlock leftBlock;
	private final QSequenceDiscardingMediaBlock rightBlock;

	private final int[] undiscardedLeftSymbols;
	private final int[] undiscardedRightSymbols;
	private final int undiscardedLeftSymbolCount;
	private final int undiscardedRightSymbolCount;

	// Setup ==================================================================

	public QSequenceDiscardingMedia(QSequenceIntMedia media, QSequenceDiscardingMediaConfusionDetector confusionDetector, QSequenceCanceller canceller) {
		this.media = media;
		this.canceller = canceller;
		this.leftBlock = new QSequenceDiscardingMediaLeftBlock(this.media);
		this.rightBlock = new QSequenceDiscardingMediaRightBlock(this.media);

		leftBlock.init(rightBlock, confusionDetector);
		rightBlock.init(leftBlock, confusionDetector);

		undiscardedLeftSymbols = leftBlock.getUndiscardedSymbols();
		undiscardedLeftSymbolCount = leftBlock.getUndiscardedSymbolCount();
		undiscardedRightSymbols = rightBlock.getUndiscardedSymbols();
		undiscardedRightSymbolCount = rightBlock.getUndiscardedSymbolCount();
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return undiscardedLeftSymbolCount;
	}

	public int getRightLength() {
		return undiscardedRightSymbolCount;
	}

	public boolean equals(int leftIndex, int rightIndex) throws QSequenceCancelledException {
		canceller.checkCancelled();
		return undiscardedLeftSymbols[leftIndex] == undiscardedRightSymbols[rightIndex];
	}

	public int getMediaLeftIndex(int index) {
		return leftBlock.getMediaIndex(index);
	}

	public int getMediaRightIndex(int index) {
		return rightBlock.getMediaIndex(index);
	}

	public int getMediaLeftLength() {
		return media.getLeftLength();
	}

	public int getMediaRightLength() {
		return media.getRightLength();
	}
}