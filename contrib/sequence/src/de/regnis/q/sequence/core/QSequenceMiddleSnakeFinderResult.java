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
class QSequenceMiddleSnakeFinderResult {

	// Fields =================================================================

	private int leftFrom;
	private int rightFrom;
	private int leftTo;
	private int rightTo;

	// Accessing ==============================================================

	public int getLeftFrom() {
		return leftFrom;
	}

	public int getRightFrom() {
		return rightFrom;
	}

	public int getLeftTo() {
		return leftTo;
	}

	public int getRightTo() {
		return rightTo;
	}

	public void reset() {
		leftFrom = 0;
		rightFrom = 0;
		leftTo = 0;
		rightTo = 0;
	}

	public void setMiddleSnake(int leftFrom, int rightFrom, int leftTo, int rightTo) {
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(0 <= leftFrom && leftFrom <= leftTo);
			QSequenceAssert.assertTrue(0 <= rightFrom && rightFrom <= rightTo);
		}

		this.leftFrom = leftFrom;
		this.rightFrom = rightFrom;
		this.leftTo = leftTo;
		this.rightTo = rightTo;
	}
}