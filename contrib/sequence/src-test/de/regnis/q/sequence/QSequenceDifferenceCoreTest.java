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
public class QSequenceDifferenceCoreTest extends TestCase {

	// Static =================================================================

	public static String fillWithChar(String string, int totalLength, char chr) {
		if (string.length() == totalLength) {
			return string;
		}

		final StringBuffer buffer = new StringBuffer(totalLength);
		buffer.append(string);
		fillWithChar(buffer, totalLength, chr);
		return buffer.toString();
	}

	public static String fillWithChar(String string, int totalLength, int startIndex, char chr) {
		if (string.length() == totalLength) {
			return string;
		}

		final StringBuffer buffer = new StringBuffer(totalLength);
		buffer.append(string);
		fillWithChar(buffer, totalLength, startIndex, chr);
		return buffer.toString();
	}

	public static StringBuffer fillWithChar(StringBuffer buffer, int totalLength, char chr) {
		return fillWithChar(buffer, totalLength, -1, chr);
	}

	public static StringBuffer fillWithChar(StringBuffer buffer, int totalLength, int startIndex, char chr) {
		buffer.ensureCapacity(totalLength);
		if (startIndex >= 0 && startIndex < buffer.length()) {
			while (buffer.length() < totalLength) {
				buffer.insert(startIndex, chr);
			}
		}
		else {
			while (buffer.length() < totalLength) {
				buffer.append(chr);
			}
		}
		return buffer;
	}

	// Accessing ==============================================================

	public void test() throws QSequenceException {
		test1("abcabba", "cbabac", "--c-bba", "cb-ba-");
		test1("abcccd", "accccd", "a-cccd", "a-cccd");
		test2("abbb", "abbbb", "abbb", "abbb-");
		test2("abccd", "abccd", "abccd", "abccd");
		test2("abccd*", "abccd", "abccd-", "abccd");
		test2("*abccd", "abccd", "-abccd", "abccd");
		test2("abc*cd", "abccd", "abc-cd", "abccd");
		test2("abccd", "x", "-----", "-");
		test2("abccd", "", "-----", "");
		test2("", "", "", "");
		test2("Howdy", "Rucola", "-o---", "---o--");
	}

	// Utils ==================================================================

	private void test2(String left, String right, String leftTest, String rightTest) throws QSequenceException {
		test1(left, right, leftTest, rightTest);
		test1(right, left, rightTest, leftTest);
	}

	private void test1(String left, String right, String leftTest, String rightTest) throws QSequenceException {
		final QSequenceTestMedia media = QSequenceTestMedia.createCharacterMedia(left, right);
		final QSequenceIntMedia cachingMedia = new QSequenceCachingMedia(media, new QSequenceDummyCanceller());
		final QSequenceDiscardingMedia discardingMedia = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(false), new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(discardingMedia, discardingMedia).getBlocks();

		final StringBuffer leftBuffer = new StringBuffer(left);
		final StringBuffer rightBuffer = new StringBuffer(right);
		for (int index = 0; index < blocks.size(); index++) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)blocks.get(index);

			final int leftFrom = block.getLeftFrom();
			final int leftTo = block.getLeftTo();
			if (leftFrom <= leftTo) {
				leftBuffer.replace(leftFrom, leftTo + 1, fillWithChar("", leftTo - leftFrom + 1, '-'));
			}

			final int rightFrom = block.getRightFrom();
			final int rightTo = block.getRightTo();
			if (rightFrom <= rightTo) {
				rightBuffer.replace(rightFrom, rightTo + 1, fillWithChar("", rightTo - rightFrom + 1, '-'));
			}
		}

		assertEquals(leftTest, leftBuffer.toString());
		assertEquals(rightTest, rightBuffer.toString());
	}
}