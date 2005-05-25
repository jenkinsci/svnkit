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

import java.util.*;

import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceDifferenceBlockShifter {

	// Static =================================================================

	public static void joinBlocks(List blocks) {
		QSequenceDifferenceBlock lastBlock = null;
		for (int index = 0; index < blocks.size();) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(index);
			if (lastBlock == null) {
				index++;
				lastBlock = block;
				continue;
			}

			QSequenceAssert.assertTrue(lastBlock.getLeftTo() < block.getLeftFrom());
			QSequenceAssert.assertTrue(lastBlock.getRightTo() < block.getRightFrom());

			if (lastBlock.getLeftTo() + 1 != block.getLeftFrom()) {
				QSequenceAssert.assertTrue(lastBlock.getRightTo() != block.getRightFrom() + 1);
				lastBlock = block;
				index++;
				continue;
			}

			if (lastBlock.getRightTo() + 1 != block.getRightFrom()) {
				QSequenceAssert.assertTrue(lastBlock.getLeftTo() != block.getLeftFrom() + 1);
				lastBlock = block;
				index++;
				continue;
			}

			lastBlock.setLeftTo(block.getLeftTo());
			lastBlock.setRightTo(block.getRightTo());
			blocks.remove(index);
			continue;
		}
	}

	// Fields =================================================================

	private final QSequenceMedia media;
	private final QSequenceMediaComparer comparer;

	// Setup ==================================================================

	public QSequenceDifferenceBlockShifter(QSequenceMedia media, QSequenceMediaComparer comparer) {
		QSequenceAssert.assertNotNull(media);
		QSequenceAssert.assertNotNull(comparer);

		this.media = media;
		this.comparer = comparer;
	}

	// Accessing ==============================================================

	public void shiftBlocks(List blocks) throws QSequenceException {
		if (blocks.isEmpty()) {
			return;
		}

		joinBlocks(blocks);

		for (int index = 0; index < blocks.size();) {
			if (tryShiftUp(blocks, index, true)) {
				continue;
			}

			index++;
		}

		for (int index = 0; index < blocks.size();) {
			if (tryShiftDown(blocks, index)) {
				continue;
			}

			index++;
		}
	}

	public boolean tryShiftUp(List blocks, int blockIndex, boolean requireMerge) throws QSequenceException {
		if (blockIndex == 0) {
			return false;
		}

		final QSequenceDifferenceBlock prevBlock = (QSequenceDifferenceBlock)blocks.get(blockIndex - 1);
		final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(blockIndex);

		final int prevLeftTo = prevBlock.getLeftTo();
		final int prevRightTo = prevBlock.getRightTo();

		int leftFrom = block.getLeftFrom();
		int leftTo = block.getLeftTo();
		int rightFrom = block.getRightFrom();
		int rightTo = block.getRightTo();

		QSequenceAssert.assertTrue(leftFrom > prevLeftTo);
		QSequenceAssert.assertTrue(rightFrom > prevRightTo);
		QSequenceAssert.assertTrue(leftFrom <= leftTo || rightFrom <= rightTo);

		if (leftFrom - prevLeftTo != rightFrom - prevRightTo) {
			return false;
		}

		while (leftFrom > prevLeftTo + 1) {
			if (leftFrom <= leftTo && !comparer.equalsLeft(leftFrom - 1, leftTo)) {
				break;
			}

			if (rightFrom <= rightTo && !comparer.equalsRight(rightFrom - 1, rightTo)) {
				break;
			}

			leftFrom--;
			leftTo--;
			rightFrom--;
			rightTo--;
		}

		if (leftFrom > prevLeftTo + 1) {
			if (requireMerge) {
				return false;
			}

			block.setLeftFrom(leftFrom);
			block.setLeftTo(leftTo);
			block.setRightFrom(rightFrom);
			block.setRightTo(rightTo);
		}
		else {
			prevBlock.setLeftTo(prevBlock.getLeftTo() + (leftTo - leftFrom + 1));
			prevBlock.setRightTo(prevBlock.getRightTo() + (rightTo - rightFrom + 1));
			blocks.remove(blockIndex);
		}

		return true;
	}

	public boolean tryShiftDown(List blocks, int blockIndex) throws QSequenceException {
		final QSequenceDifferenceBlock nextBlock = blockIndex < blocks.size() - 1
		    ? (QSequenceDifferenceBlock)blocks.get(blockIndex + 1) : null;
		final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(blockIndex);

		final int nextLeftFrom = nextBlock != null ? nextBlock.getLeftFrom() : media.getLeftLength();
		final int nextRightFrom = nextBlock != null ? nextBlock.getRightFrom() : media.getRightLength();

		int leftFrom = block.getLeftFrom();
		int leftTo = block.getLeftTo();
		int rightFrom = block.getRightFrom();
		int rightTo = block.getRightTo();

		QSequenceAssert.assertTrue(leftTo < nextLeftFrom);
		QSequenceAssert.assertTrue(rightTo < nextRightFrom);
		QSequenceAssert.assertTrue(leftFrom <= leftTo || rightFrom <= rightTo);

		while (leftTo < nextLeftFrom - 1 && rightTo < nextRightFrom - 1) {
			if (leftFrom <= leftTo && !comparer.equalsLeft(leftFrom, leftTo + 1)) {
				break;
			}

			if (rightFrom <= rightTo && !comparer.equalsRight(rightFrom, rightTo + 1)) {
				break;
			}

			leftFrom++;
			leftTo++;
			rightFrom++;
			rightTo++;
		}

		if (nextBlock != null && leftTo == nextLeftFrom - 1 && rightTo == nextRightFrom - 1) {
			nextBlock.setLeftFrom(nextBlock.getLeftFrom() - (leftTo - leftFrom + 1));
			nextBlock.setRightFrom(nextBlock.getRightFrom() - (rightTo - rightFrom + 1));
			blocks.remove(blockIndex);
			return true;
		}

		block.setLeftFrom(leftFrom);
		block.setLeftTo(leftTo);
		block.setRightFrom(rightFrom);
		block.setRightTo(rightTo);
		return false;
	}
}