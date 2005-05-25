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
class QSequenceMiddleSnakeFinder {

	// Fields =================================================================

	private final QSequenceDeePathForwardExtender forwardDeePathExtender;
	private final QSequenceDeePathBackwardExtender backwardDeePathExtender;
	private final QSequenceMiddleSnakeFinderResult result;

	// Setup ==================================================================

	public QSequenceMiddleSnakeFinder(int maximumMediaLeftLength, int maximumMediaRightLength) {
		this.forwardDeePathExtender = new QSequenceDeePathForwardExtender(maximumMediaLeftLength, maximumMediaRightLength);
		this.backwardDeePathExtender = new QSequenceDeePathBackwardExtender(maximumMediaLeftLength, maximumMediaRightLength);
		this.result = new QSequenceMiddleSnakeFinderResult();
	}

	// Accessing ==============================================================

	public QSequenceMiddleSnakeFinderResult getResult() {
		return result;
	}

	public int determineMiddleSnake(QSequenceMedia media) throws QSequenceException {
		result.reset();
		forwardDeePathExtender.reset(media);
		backwardDeePathExtender.reset(media);

		final int delta = media.getLeftLength() - media.getRightLength();
		final int deeMax = (int)Math.ceil(((double)media.getLeftLength() + (double)media.getRightLength()) / 2);
		for (int dee = 0; dee <= deeMax; dee++) {
			for (int diagonal = (delta >= 0 ? dee : -dee); (delta >= 0 ? diagonal >= -dee : diagonal <= dee); diagonal += (delta >= 0 ? -2 : 2)) {
				forwardDeePathExtender.extendDeePath(media, dee, diagonal);
				if (checkForwardOverlapping(delta, diagonal, dee)) {
					if (isForwardAndBackwardOverlapping(diagonal)) {
						result.setMiddleSnake(forwardDeePathExtender, diagonal);
						return 2 * dee - 1;
					}
				}
			}

			for (int diagonal = (delta >= 0 ? -dee : dee); (delta >= 0 ? diagonal <= dee : diagonal >= -dee); diagonal += (delta >= 0 ? 2 : -2)) {
				backwardDeePathExtender.extendDeePath(media, dee, diagonal + delta);
				if (checkBackwardOverlapping(delta, diagonal, dee)) {
					if (isForwardAndBackwardOverlapping(diagonal + delta)) {
						result.setMiddleSnake(backwardDeePathExtender, diagonal + delta);
						return 2 * dee;
					}
				}
			}
		}

		QSequenceAssert.assertTrue(false);
		return 0;
	}

	// Utils ==================================================================

	private static boolean checkForwardOverlapping(final int delta, int diagonal, int dee) {
		return diagonal >= (delta - (dee - 1)) && diagonal <= (delta + (dee - 1));
	}

	private static boolean checkBackwardOverlapping(final int delta, int diagonal, int dee) {
		return diagonal + delta >= -dee && diagonal + delta <= dee;
	}

	private boolean isForwardAndBackwardOverlapping(int diagonal) {
		final int forwardLeft = forwardDeePathExtender.getLeft(diagonal);
		final int backwardLeft = backwardDeePathExtender.getLeft(diagonal);
		return forwardLeft >= backwardLeft;
	}
}