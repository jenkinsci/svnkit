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

	private int leftStart;
	private int rightStart;

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
		leftStart = 0;
		rightStart = 0;
		leftFrom = 0;
		rightFrom = 0;
		leftTo = 0;
		rightTo = 0;
	}

	public void setMiddleSnake(QSequenceDeePathExtender extender, int diagonal) {
		leftFrom = Math.min(extender.getLeft(diagonal), extender.getSnakeStartLeft());
		rightFrom = Math.min(extender.getRight(diagonal), extender.getSnakeStartRight());
		leftTo = Math.max(extender.getLeft(diagonal), extender.getSnakeStartLeft());
		rightTo = Math.max(extender.getRight(diagonal), extender.getSnakeStartRight());
		leftStart = extender.getStartX();
		rightStart = extender.getStartY();
	}

	public int getLeftStart() {
		return leftStart;
	}

	public int getRightStart() {
		return rightStart;
	}

}