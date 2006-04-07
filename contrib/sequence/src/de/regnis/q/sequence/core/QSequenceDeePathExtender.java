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

	protected abstract int getSnakeX(QSequenceMedia media, int x, int y) throws QSequenceException;

	protected abstract void reset(QSequenceMedia media, QSequenceDeePathExtenderArray xs);

	public abstract int getProgress(int diagonal);

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

	public final void extendDeePath(QSequenceMedia media, int dee, int diagonal) throws QSequenceException {
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

	public final void print(QSequenceMedia media, int fromDiagonal, int toDiagonal) {
		final StringBuffer[] lines = new StringBuffer[media.getRightLength() + 1];
		for (int line = 0; line < lines.length; line++) {
			lines[line] = new StringBuffer(media.getLeftLength() + 1);

			lines[line].append('.');
			for (int ch = 0; ch < media.getLeftLength(); ch++) {
				lines[line].append(line >= 1 && line <= media.getRightLength() ? '*' : '.');
			}
		}

		for (int diagonal = fromDiagonal; diagonal <= toDiagonal; diagonal++) {
			final int left = getLeft(diagonal);
			final int right = getRight(diagonal);
			if (left < 0 || right < 0 || right >= lines.length || left >= lines[right].length()) {
				continue;
			}

			lines[right].setCharAt(left, String.valueOf(Math.abs(diagonal % 9)).charAt(0));
		}
	}
}