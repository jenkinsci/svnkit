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
class QSequenceDeePathExtenderArray {

	// Fields =================================================================

	private final int[] xs;
	private final int offset;

	private int delta;

	// Setup ==================================================================

	public QSequenceDeePathExtenderArray(int maximumMediaLeftRightLength) {
		this.offset = maximumMediaLeftRightLength;
		this.xs = new int[2 * maximumMediaLeftRightLength + 1];
	}

	// Accessing ==============================================================

	public void set(int diagonal, int maxLeft) {
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(-offset + delta <= diagonal && diagonal <= offset + delta);
		}
		this.xs[offset - delta + diagonal] = maxLeft;
	}

	public int get(int diagonal) {
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(-offset + delta <= diagonal && diagonal <= offset + delta);
		}
		final int left = xs[offset - delta + diagonal];
		if (QSequenceAlgorithm.ASSERTIONS) {
			QSequenceAssert.assertTrue(left != Integer.MAX_VALUE);
		}
		return left;
	}

	public void setDelta(int delta) {
		this.delta = delta;
	}
}