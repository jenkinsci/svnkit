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
import junit.framework.*;

import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceDifferenceAssemblyTest extends TestCase {

	// Constants ==============================================================

	private static final int LINE_LENGTH = 3;
	private static final int ALPHABET_SIZE = 10;

	// Static =================================================================

	public static String[] createLines(int lineCount, Random random) {
		final String[] lines = new String[lineCount];
		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			String line = createLine(random);
			lines[lineIndex] = line;
		}

		return lines;
	}

	public static String[] alterLines(String[] leftLines, double pMod, double pAddRemove, Random random) {
		final List rightLines = new ArrayList();
		for (int leftIndex = 0; leftIndex < leftLines.length; leftIndex++) {
			if (random.nextDouble() < pMod) {
				rightLines.add(createLine(random));
				continue;
			}
			else if (random.nextDouble() < pAddRemove) {
				continue;
			}

			rightLines.add(leftLines[leftIndex]);

			if (random.nextDouble() < pAddRemove) {
				rightLines.add(createLine(random));
			}
		}
		return (String[])rightLines.toArray(new String[0]);
	}

	// Fields =================================================================

	private Random random;

	// Implemented ============================================================

	protected void setUp() throws Exception {
		super.setUp();
		random = new Random(0);
	}

	// Accessing ==============================================================

	public void test() throws QSequenceException {
		testVariousLength(0.001, 0.001);
		testVariousLength(0.01, 0.05);
		testVariousLength(0.5, 0.5);
	}

	// Utils ==================================================================

	private void testVariousLength(double pMod, double pAddRemove) throws QSequenceException {
		for (int size = 10; size <= 1000; size += 50) {
			testOneLength(size, pMod, pAddRemove);
		}
	}

	private void testOneLength(int lineCount, double pMod, double pAddRemove) throws QSequenceException {
		final String[] left = createLines(lineCount, random);
		final String[] right = alterLines(left, pMod, pAddRemove, random);
		testDiff(left, right);
	}

	private boolean areLinesEqual(String[] diff, String[] right) {
		if (diff.length != right.length) {
			return false;
		}

		for (int index = 0; index < diff.length; index++) {
			if (!diff[index].equals(right[index])) {
				return false;
			}
		}

		return true;
	}

	private static String createLine(Random random) {
		String line = "";
		for (int charIndex = 0; charIndex < LINE_LENGTH; charIndex++) {
			line += (char)('a' + (char)(Math.abs(random.nextInt()) % ALPHABET_SIZE));
		}
		return line;
	}

	private void testDiff(String[] left, String[] right) throws QSequenceException {
		final QSequenceTestMedia testMedia = QSequenceTestMedia.createStringMedia(left, right);
		testDiff(left, right, testMedia, new QSequenceMediaDummyIndexTransformer(testMedia.getLeftLength(), testMedia.getRightLength()), null);

		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(testMedia, new QSequenceDummyCanceller());
		final QSequenceDiscardingMedia media = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(true), new QSequenceDummyCanceller());
		testDiff(left, right, media, media, cachingMedia);
	}

	private void testDiff(String[] left, String[] right, QSequenceMedia media, QSequenceMediaIndexTransformer indexTransformer, QSequenceCachingMedia cachingMedia) throws QSequenceException {
		final List blocks = new QSequenceDifference(media, indexTransformer).getBlocks();
		if (cachingMedia != null) {
			new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		}

		final List diffLines = new ArrayList();
		int lastLeftTo = -1;
		for (int index = 0; index < blocks.size(); index++) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(index);
			for (int leftIndex = lastLeftTo + 1; leftIndex < block.getLeftFrom(); leftIndex++) {
				diffLines.add(left[leftIndex]);
			}

			lastLeftTo = block.getLeftTo();

			for (int rightIndex = block.getRightFrom(); rightIndex <= block.getRightTo(); rightIndex++) {
				diffLines.add(right[rightIndex]);
			}
		}

		for (int leftIndex = lastLeftTo + 1; leftIndex < left.length; leftIndex++) {
			diffLines.add(left[leftIndex]);
		}

		final String[] diff = (String[])diffLines.toArray(new String[0]);
		if (!areLinesEqual(diff, right)) {
			fail();
		}
	}
}