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
public class QSequenceAlgorithm {

	// Constants ==============================================================

	public static final boolean ASSERTIONS = true;

	// Fields =================================================================

	private final QSequenceMedia mainMedia;
	private final QSequenceSnakeRegister snakeRegister;
	private final QSequenceMiddleSnakeFinder finder;

	// Setup ==================================================================

	public QSequenceAlgorithm(QSequenceMedia media, QSequenceSnakeRegister snakeRegister) {
		this.mainMedia = media;
		this.snakeRegister = snakeRegister;
		this.finder = new QSequenceMiddleSnakeFinder(media.getLeftLength(), media.getRightLength());
	}

	// Accessing ==============================================================

	public void produceSnakesInOrder() throws QSequenceCancelledException {
		final QSequenceRestrictedMedia media = new QSequenceRestrictedMedia(mainMedia);
		producesSnakesInOrder(media);
	}

	// Utils ==================================================================

	private void producesSnakesInOrder(QSequenceRestrictedMedia media) throws QSequenceCancelledException {
		final int leftLength = media.getLeftLength();
		final int rightLength = media.getRightLength();

		if (leftLength < 1 || rightLength < 1) {
			return;
		}

		final int dee = finder.determineMiddleSnake(media);
		if (dee <= 0) {
			registerSnake(media, 1, leftLength, 1, rightLength);
			return;
		}

		if (dee == 1) {
			final int leftStart = finder.getResult().getLeftStart();
			final int rightStart = finder.getResult().getRightStart();

			QSequenceAssert.assertTrue(leftStart >= 0 && rightStart >= 0);

			if (rightLength == leftLength + 1) {
				registerSnake(media, 1, rightStart, 1, rightStart);
				registerSnake(media, rightStart + 1, leftLength, rightStart + 2, rightLength);
			}
			else if (leftLength == rightLength + 1) {
				registerSnake(media, 1, leftStart, 1, leftStart);
				registerSnake(media, leftStart + 2, leftLength, leftStart + 1, rightLength);
			}
			else {
				QSequenceAssert.assertTrue(false);
			}

			return;
		}

		final int leftFrom = finder.getResult().getLeftFrom();
		final int rightFrom = finder.getResult().getRightFrom();
		final int leftTo = finder.getResult().getLeftTo();
		final int rightTo = finder.getResult().getRightTo();
		final int leftMin = media.getLeftMin();
		final int rightMin = media.getRightMin();
		final int leftMax = media.getLeftMax();
		final int rightMax = media.getRightMax();

		try {
			media.restrictTo(leftMin, leftMin + leftFrom - 1, rightMin, rightMin + rightFrom - 1);
			producesSnakesInOrder(media);
			media.restrictTo(leftMin, leftMax, rightMin, rightMax);
			registerSnake(media, leftFrom + 1, leftTo, rightFrom + 1, rightTo);
			media.restrictTo(leftMin + leftTo - 1 + 1, leftMax, rightMin + rightTo - 1 + 1, rightMax);
			producesSnakesInOrder(media);
		}
		finally {
			media.restrictTo(leftMin, leftMax, rightMin, rightMax);
		}
	}

	private void registerSnake(QSequenceRestrictedMedia media, int leftFrom, int leftTo, int rightFrom, int rightTo) throws QSequenceCancelledException {
		QSequenceAssert.assertTrue(leftTo - leftFrom == rightTo - rightFrom);

		if (leftFrom > leftTo || rightFrom > rightTo) {
			return;
		}

		for (int index = 0; index < leftTo - leftFrom; index++) {
			QSequenceAssert.assertTrue(media.equals(leftFrom + index, rightFrom + index));
		}

		leftFrom = media.getLeftMin() + leftFrom - 1;
		leftTo = media.getLeftMin() + leftTo - 1;
		rightFrom = media.getRightMin() + rightFrom - 1;
		rightTo = media.getRightMin() + rightTo - 1;
		snakeRegister.registerSnake(leftFrom - 1, leftTo - 1, rightFrom - 1, rightTo - 1);
	}
}