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

package de.regnis.q.sequence.core;

/**
 * @author Marc Strapetz
 */
class QSequenceRestrictedMedia implements QSequenceMedia {

	// Fields =================================================================

	private final QSequenceMedia media;

	private int leftMin;
	private int rightMin;
	private int leftMax;
	private int rightMax;

	// Setup ==================================================================

	public QSequenceRestrictedMedia(QSequenceMedia media) {
		this.media = media;
		restrictTo(1, media.getLeftLength(), 1, media.getRightLength());
	}

	// Accessing ==============================================================

	public void restrictTo(int leftMin, int leftMax, int rightMin, int rightMax) {
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(0 <= leftMin && leftMin <= leftMax + 1);
			QSequenceAssert.assertTrue(leftMax <= media.getLeftLength());
			QSequenceAssert.assertTrue(0 <= rightMin && rightMin <= rightMax + 1);
			QSequenceAssert.assertTrue(rightMax <= media.getRightLength());
		}

		this.leftMin = leftMin;
		this.leftMax = leftMax;
		this.rightMin = rightMin;
		this.rightMax = rightMax;
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return leftMax - leftMin + 1;
	}

	public int getRightLength() {
		return rightMax - rightMin + 1;
	}

	public boolean equals(int leftIndex, int rightIndex) throws QSequenceException {
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(1 <= leftIndex && leftIndex <= leftMax - leftMin + 1);
		}
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(1 <= rightIndex && rightIndex <= rightMax - rightMin + 1);
		}
		return media.equals(leftMin + leftIndex - 2, rightMin + rightIndex - 2);
	}

	// Accessing ==============================================================

	public int getLeftMin() {
		return leftMin;
	}

	public int getLeftMax() {
		return leftMax;
	}

	public int getRightMin() {
		return rightMin;
	}

	public int getRightMax() {
		return rightMax;
	}

}