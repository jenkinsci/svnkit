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
final class QSequenceDeePathBackwardExtender extends QSequenceDeePathExtender {

	// Fields =================================================================

	private int delta;
	private int mediaLeftLength;
	private int mediaRightLength;

	// Setup ==================================================================

	public QSequenceDeePathBackwardExtender(int maximumMediaLeftLength, int maximumMediaRightLength) {
		super(new QSequenceDeePathExtenderArray(maximumMediaLeftLength + maximumMediaRightLength));
	}

	// Accessing ==============================================================

	protected int getNextX(QSequenceDeePathExtenderArray xs, int diagonal, int dee) {
		if (diagonal - delta == dee || (diagonal - delta != -dee && xs.get(diagonal + 1) > xs.get(diagonal - 1))) {
			return xs.get(diagonal - 1);
		}

		return xs.get(diagonal + 1) - 1;
	}

	protected int getSnakeX(QSequenceMedia media, int x, int y) throws QSequenceException {
		for (; x > 0 && y > 0 && media.equals(x, y);) {
			x--;
			y--;
		}

		return x;
	}

	protected final void reset(QSequenceMedia media, QSequenceDeePathExtenderArray xs) {
		mediaLeftLength = media.getLeftLength();
		mediaRightLength = media.getRightLength();
		delta = mediaLeftLength - mediaRightLength;
		xs.setDelta(delta);
		xs.set(delta - 1, mediaLeftLength);
	}

	public int getProgress(int diagonal) {
		return mediaLeftLength - getLeft(diagonal) + mediaRightLength - getRight(diagonal);
	}
}