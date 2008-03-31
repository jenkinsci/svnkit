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

package de.regnis.q.sequence;

import java.util.*;

import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceDifference
    implements QSequenceSnakeRegister, QSequenceCommonBlockFactory {

	// Fields =================================================================

	private final QSequenceMedia media;
	private final QSequenceMediaIndexTransformer indexTransformer;
	private final boolean[] leftCommonPoints;
	private final boolean[] rightCommonPoints;
	private final int maximumSearchDepth;

	// Setup ==================================================================

	public QSequenceDifference(QSequenceMedia media, QSequenceMediaIndexTransformer indexTransformer) {
		this(media, indexTransformer, Integer.MAX_VALUE);
	}

	public QSequenceDifference(QSequenceMedia media, QSequenceMediaIndexTransformer indexTransformer, int maximumSearchDepth) {
		QSequenceAssert.assertNotNull(media);
		QSequenceAssert.assertNotNull(indexTransformer);

		this.media = media;
		this.indexTransformer = indexTransformer;
		this.leftCommonPoints = new boolean[indexTransformer.getMediaLeftLength()];
		this.rightCommonPoints = new boolean[indexTransformer.getMediaRightLength()];
		this.maximumSearchDepth = maximumSearchDepth;
	}

	// Implemented ============================================================

	public void registerSnake(int leftFrom, int leftTo, int rightFrom, int rightTo) throws QSequenceCancelledException {
		for (int leftIndex = leftFrom; leftIndex <= leftTo; leftIndex++) {
			QSequenceAssert.assertTrue(!leftCommonPoints[indexTransformer.getMediaLeftIndex(leftIndex)]);
			leftCommonPoints[indexTransformer.getMediaLeftIndex(leftIndex)] = true;
		}

		for (int rightIndex = rightFrom; rightIndex <= rightTo; rightIndex++) {
			QSequenceAssert.assertTrue(!rightCommonPoints[indexTransformer.getMediaRightIndex(rightIndex)]);
			rightCommonPoints[indexTransformer.getMediaRightIndex(rightIndex)] = true;
		}
	}

	public Object createCommonBlock(int leftFrom, int leftTo, int rightFrom, int rightTo) {
		return null;
	}

	public Object createDistinctBlock(int leftFrom, int leftTo, int rightFrom, int rightTo) {
		return new QSequenceDifferenceBlock(leftFrom, leftTo, rightFrom, rightTo);
	}

	// Accessing ==============================================================

	public List getBlocks() throws QSequenceException {
		final QSequenceAlgorithm algorithm = new QSequenceAlgorithm(media, this, maximumSearchDepth);
		algorithm.produceSnakesInOrder();
		return QSequenceCommonBlocks.createBlocks(leftCommonPoints, rightCommonPoints, this);
	}
}