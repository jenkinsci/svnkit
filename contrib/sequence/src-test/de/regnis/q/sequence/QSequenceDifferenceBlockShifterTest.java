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
public class QSequenceDifferenceBlockShifterTest extends TestCase {

	// Accessing ==============================================================

	public void test() throws QSequenceCancelledException {
		test2("aa", "aaa", "aa", "aa-");
		test2("xaa", "yaaa", "-aa", "--aa");
		test2("ababab", "abababab", "ababab", "ababab--");
		test2("xababab", "yabababab", "-ababab", "---ababab");
		test2("public class PolygonExtractionTree implements QBBox2D {", "class PolygonExtractionTree implements QBBox2D {", "-------class PolygonExtractionTree implements QBBox2D {", "class PolygonExtractionTree implements QBBox2D {");
		test2("abcde", "axcye", "a-c-e", "a-c-e");
		test2("ab*#cd*#", "ab*#ef*#cd*#", "ab*#cd*#", "ab*#----cd*#");
	}

	// Utils ==================================================================

	private void test2(String left, String right, String leftTest, String rightTest) throws QSequenceCancelledException {
		test1(left, right, leftTest, rightTest);
		test1(right, left, rightTest, leftTest);
	}

	private void test1(String left, String right, String leftTest, String rightTest) throws QSequenceCancelledException {
		final QSequenceTestMedia media = QSequenceTestMedia.createCharacterMedia(left, right);

		final QSequenceDifference difference = new QSequenceDifference(media, new QSequenceMediaDummyIndexTransformer(media.getLeftLength(), media.getRightLength()));
		final List blocks = difference.getBlocks();

		final QSequenceDifferenceBlockShifter blockShifter = new QSequenceDifferenceBlockShifter(media, media);
		blockShifter.shiftBlocks(blocks);

		final StringBuffer leftBuffer = new StringBuffer(left);
		final StringBuffer rightBuffer = new StringBuffer(right);
		for (int index = 0; index < blocks.size(); index++) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(index);

			final int leftFrom = block.getLeftFrom();
			final int leftTo = block.getLeftTo();
			if (leftFrom <= leftTo) {
				leftBuffer.replace(leftFrom, leftTo + 1, QSequenceDifferenceCoreTest.fillWithChar("", leftTo - leftFrom + 1, '-'));
			}

			final int rightFrom = block.getRightFrom();
			final int rightTo = block.getRightTo();
			if (rightFrom <= rightTo) {
				rightBuffer.replace(rightFrom, rightTo + 1, QSequenceDifferenceCoreTest.fillWithChar("", rightTo - rightFrom + 1, '-'));
			}
		}

		assertEquals(leftTest, leftBuffer.toString());
		assertEquals(rightTest, rightBuffer.toString());
	}
}