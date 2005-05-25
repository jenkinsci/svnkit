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

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceDifferenceBlock {

	// Fields =================================================================

	private int leftFrom;
	private int leftTo;
	private int rightFrom;
	private int rightTo;

	// Setup ==================================================================

	public QSequenceDifferenceBlock(int leftFrom, int leftTo, int rightFrom, int rightTo) {
		QSequenceAssert.assertTrue(leftFrom <= leftTo || rightFrom <= rightTo);

		this.leftFrom = leftFrom;
		this.leftTo = leftTo;
		this.rightFrom = rightFrom;
		this.rightTo = rightTo;
	}

	// Accessing ==============================================================

	public int getLeftFrom() {
		return leftFrom;
	}

	public int getLeftTo() {
		return leftTo;
	}

	public int getLeftSize() {
		return leftTo - leftFrom + 1;
	}

	public int getRightFrom() {
		return rightFrom;
	}

	public int getRightTo() {
		return rightTo;
	}

	public int getRightSize() {
		return rightTo - rightFrom + 1;
	}

	// Package ================================================================

	void setLeftFrom(int leftFrom) {
		this.leftFrom = leftFrom;
	}

	void setLeftTo(int leftTo) {
		this.leftTo = leftTo;
	}

	void setRightFrom(int rightFrom) {
		this.rightFrom = rightFrom;
	}

	void setRightTo(int rightTo) {
		this.rightTo = rightTo;
	}

	// Implemented ============================================================

	public String toString() {
		return "[" + leftFrom + "/" + leftTo + "/" + rightFrom + "/" + rightTo + "]";
	}
}