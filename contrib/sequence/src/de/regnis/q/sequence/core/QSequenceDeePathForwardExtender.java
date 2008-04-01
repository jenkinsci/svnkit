/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence.core;

/**
 * @author Marc Strapetz
 */
class QSequenceDeePathForwardExtender extends QSequenceDeePathExtender {

	// Setup ==================================================================

	public QSequenceDeePathForwardExtender(int maximumMediaLeftLength, int maximumMediaRightLength) {
		super(new QSequenceDeePathExtenderArray(maximumMediaLeftLength + maximumMediaRightLength));
	}

	// Accessing ==============================================================

	protected int getNextX(QSequenceDeePathExtenderArray xs, int diagonal, int dee) {
		if (diagonal == -dee || (diagonal != dee && xs.get(diagonal - 1) < xs.get(diagonal + 1))) {
			return xs.get(diagonal + 1);
		}

		return xs.get(diagonal - 1) + 1;
	}

	protected int getSnakeX(QSequenceMedia media, int x, int y) throws QSequenceException {
		for (; x < media.getLeftLength() && y < media.getRightLength() && media.equals(x + 1, y + 1);) {
			x++;
			y++;
		}

		return x;
	}

	protected final void reset(QSequenceMedia media, QSequenceDeePathExtenderArray xs) {
		xs.set(1, 0);
	}

	public int getProgress(int diagonal) {
		return getLeft(diagonal) + getRight(diagonal);
	}
}