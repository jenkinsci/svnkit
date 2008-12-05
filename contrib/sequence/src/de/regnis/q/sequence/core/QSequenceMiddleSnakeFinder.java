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
class QSequenceMiddleSnakeFinder {

	// Fields =================================================================

	private final QSequenceDeePathForwardExtender forwardDeePathExtender;
	private final QSequenceDeePathBackwardExtender backwardDeePathExtender;
	private final QSequenceMiddleSnakeFinderResult result;
	private final int maximumSearchDepth;

	// Setup ==================================================================

	public QSequenceMiddleSnakeFinder(int maximumMediaLeftLength, int maximumMediaRightLength, int maximumSearchDepth) {
		this.maximumSearchDepth = maximumSearchDepth;
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
			boolean found = false;
			for (int diagonal = (delta >= 0 ? dee : -dee); (delta >= 0 ? diagonal >= -dee : diagonal <= dee); diagonal += (delta >= 0 ? -2 : 2)) {
				forwardDeePathExtender.extendDeePath(media, dee, diagonal);
				if (checkForwardOverlapping(delta, diagonal, dee)) {
					if (isForwardAndBackwardOverlapping(diagonal)) {
						setMiddleSnake(result, forwardDeePathExtender, diagonal);
						// We are not returning here with the first overlapping point (we could do), but try all possible diagonals with the current
						// maximum dee. All possible solutions are shortes, but setMiddleSnake() can select that path with minimum rightFrom/leftFrom.
						// This gives more stable results, e.g. when having a similar left/right medium with just one line added/removed.
						found = true;
					}
				}
			}
			if (found) {
				return 2 * dee - 1;
			}

			for (int diagonal = (delta >= 0 ? -dee : dee); (delta >= 0 ? diagonal <= dee : diagonal >= -dee); diagonal += (delta >= 0 ? 2 : -2)) {
				final int deltadDiagonal = diagonal + delta;
				backwardDeePathExtender.extendDeePath(media, dee, deltadDiagonal);
				if (checkBackwardOverlapping(delta, diagonal, dee)) {
					if (isForwardAndBackwardOverlapping(deltadDiagonal)) {
						setMiddleSnake(result, backwardDeePathExtender, deltadDiagonal);
						found = true;
					}
				}
			}
			if (found) {
				return 2 * dee;
			}

			if (dee < maximumSearchDepth) {
				continue;
			}

			return determineBestSnake(media, dee, delta);
		}

		QSequenceAssert.assertTrue(false);
		return 0;
	}

	// Utils ==================================================================

	private boolean isForwardAndBackwardOverlapping(int diagonal) {
		final int forwardLeft = forwardDeePathExtender.getLeft(diagonal);
		final int backwardLeft = backwardDeePathExtender.getLeft(diagonal);
		return forwardLeft >= backwardLeft;
	}

	private int determineBestSnake(QSequenceMedia media, int dee, final int delta) {
		final int bestForwardDiagonal = getBestForwardDiagonal(dee, delta);
		final int bestBackwardDiagonal = getBestBackwardDiagonal(dee, delta);

		if (forwardDeePathExtender.getProgress(bestForwardDiagonal) > backwardDeePathExtender.getProgress(bestBackwardDiagonal)) {
			final int left = forwardDeePathExtender.getLeft(bestForwardDiagonal);
			final int right = forwardDeePathExtender.getRight(bestForwardDiagonal);
			result.setMiddleSnake(left, right, left, right);
			return 2 * dee - 1;
		}
		final int left = backwardDeePathExtender.getLeft(bestBackwardDiagonal);
		final int right = backwardDeePathExtender.getRight(bestBackwardDiagonal);
		if (left < 0 || right < 0) {
			backwardDeePathExtender.print(media, -dee + delta, dee + delta);
		}

		result.setMiddleSnake(left, right, left, right);
		return 2 * dee;
	}

	private int getBestForwardDiagonal(int dee, int delta) {
		int bestDiagonal = 0;
		int bestProgress = 0;
		for (int diagonal = (delta >= 0 ? dee : -dee); (delta >= 0 ? diagonal >= -dee : diagonal <= dee); diagonal += (delta >= 0 ? -2 : 2)) {
			final int progress = forwardDeePathExtender.getProgress(diagonal);
			if (progress > bestProgress) {
				bestDiagonal = diagonal;
				bestProgress = progress;
			}
		}

		return bestDiagonal;
	}

	private int getBestBackwardDiagonal(int dee, int delta) {
		int bestDiagonal = delta;
		int bestProgress = 0;
		for (int diagonal = (delta >= 0 ? -dee : dee); (delta >= 0 ? diagonal <= dee : diagonal >= -dee); diagonal += (delta >= 0 ? 2 : -2)) {
			final int deltadDiagonal = diagonal + delta;
			final int progress = backwardDeePathExtender.getProgress(deltadDiagonal);
			if (progress > bestProgress) {
				bestDiagonal = deltadDiagonal;
				bestProgress = progress;
			}
		}

		return bestDiagonal;
	}

	public static void setMiddleSnake(QSequenceMiddleSnakeFinderResult result, QSequenceDeePathExtender extender, int diagonal) {
		final int leftFrom = Math.min(extender.getLeft(diagonal), extender.getSnakeStartLeft());
		final int rightFrom = Math.min(extender.getRight(diagonal), extender.getSnakeStartRight());
		final int leftTo = Math.max(extender.getLeft(diagonal), extender.getSnakeStartLeft());
		final int rightTo = Math.max(extender.getRight(diagonal), extender.getSnakeStartRight());
		if ((result.getLeftFrom() == 0 && result.getRightFrom() == 0 && result.getLeftTo() == 0 && result.getRightTo() == 0)
				|| result.getRightFrom() > rightFrom || (result.getRightFrom() == rightFrom && result.getLeftFrom() > leftFrom)) {
			result.setMiddleSnake(leftFrom, rightFrom, leftTo, rightTo);
		}
	}

	private static boolean checkForwardOverlapping(final int delta, int diagonal, int dee) {
		return diagonal >= (delta - (dee - 1)) && diagonal <= (delta + (dee - 1));
	}

	private static boolean checkBackwardOverlapping(final int delta, int diagonal, int dee) {
		return diagonal + delta >= -dee && diagonal + delta <= dee;
	}
}