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
abstract class QSequenceDeePathExtender {

	// Abstract ===============================================================

	protected abstract int getNextX(QSequenceDeePathExtenderArray xs, int diagonal, int dee);

	protected abstract int getSnakeX(QSequenceMedia media, int x, int y) throws QSequenceCancelledException;

	protected abstract void reset(QSequenceMedia media, QSequenceDeePathExtenderArray xs);

	// Fields =================================================================

	private final QSequenceDeePathExtenderArray xs;

	private int snakeStartLeft;
	private int snakeStartRight;
	private int startX;
	private int startY;
	private int endX;
	private int endY;

	// Setup ==================================================================

	protected QSequenceDeePathExtender(QSequenceDeePathExtenderArray xs) {
		this.xs = xs;
	}

	// Accessing ==============================================================

	public final int getLeft(int diagonal) {
		return xs.get(diagonal);
	}

	public final int getRight(int diagonal) {
		return xs.get(diagonal) - diagonal;
	}

	public final int getStartX() {
		return startX;
	}

	public final int getStartY() {
		return startY;
	}

	public final int getSnakeStartLeft() {
		return snakeStartLeft;
	}

	public final int getSnakeStartRight() {
		return snakeStartRight;
	}

	public final void extendDeePath(QSequenceMedia media, int dee, int diagonal) throws QSequenceCancelledException {
		startX = endX;
		startY = endY;
		int x = getNextX(xs, diagonal, dee);
		int y = x - diagonal;

		snakeStartLeft = x;
		snakeStartRight = y;

		x = getSnakeX(media, x, y);
		y = x - diagonal;
		xs.set(diagonal, x);

		endX = x;
		endY = y;
	}

	public final void reset(QSequenceMedia media) {
		startX = -1;
		startY = -1;
		endX = -1;
		endY = -1;
		reset(media, xs);
	}
}