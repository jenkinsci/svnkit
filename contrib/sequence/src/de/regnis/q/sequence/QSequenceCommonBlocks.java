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

/**
 * @author Marc Strapetz
 */
public class QSequenceCommonBlocks {

	// Accessing ==============================================================

	public static List createBlocks(boolean[] leftCommonPoints, boolean[] rightCommonPoints, QSequenceCommonBlockFactory factory) {
		final List blocks = new ArrayList();

		int leftIndex = 0;
		int rightIndex = 0;

		while (leftIndex < leftCommonPoints.length || rightIndex < rightCommonPoints.length) {
			int leftStart = leftIndex;
			int rightStart = rightIndex;

			while (leftIndex < leftCommonPoints.length && rightIndex < rightCommonPoints.length && leftCommonPoints[leftIndex] && rightCommonPoints[rightIndex]) {
				leftIndex++;
				rightIndex++;
			}

			if (leftIndex > leftStart && rightIndex > rightStart) {
				final Object block = factory.createCommonBlock(leftStart, leftIndex - 1, rightStart, rightIndex - 1);
				if (block != null) {
					blocks.add(block);
				}
			}

			leftStart = leftIndex;
			rightStart = rightIndex;

			while ((leftIndex < leftCommonPoints.length && !leftCommonPoints[leftIndex]) || (rightIndex < rightCommonPoints.length && !rightCommonPoints[rightIndex])) {
				if (leftIndex < leftCommonPoints.length && !leftCommonPoints[leftIndex]) {
					leftIndex++;
				}
				if (rightIndex < rightCommonPoints.length && !rightCommonPoints[rightIndex]) {
					rightIndex++;
				}
			}

			if (leftIndex > leftStart || rightIndex > rightStart) {
				final Object block = factory.createDistinctBlock(leftStart, leftIndex - 1, rightStart, rightIndex - 1);
				if (block != null) {
					blocks.add(block);
				}
			}

			QSequenceAssert.assertTrue((leftIndex >= leftCommonPoints.length && rightIndex >= rightCommonPoints.length)
			                   || (leftCommonPoints[leftIndex] && rightCommonPoints[rightIndex]));
		}

		return blocks;
	}
}