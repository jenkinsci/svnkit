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

package de.regnis.q.sequence;

import junit.framework.*;

import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceTestMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Static =================================================================

	public static QSequenceTestMedia createStringMedia(String[] left, String[] right) {
		return new QSequenceTestMedia(left, right);
	}

	public static QSequenceTestMedia createCharacterMedia(String leftChars, String rightChars) {
		final String[] left = new String[leftChars.length()];
		for (int index = 0; index < left.length; index++) {
			left[index] = String.valueOf(leftChars.charAt(index));
		}
		final String[] right = new String[rightChars.length()];
		for (int index = 0; index < right.length; index++) {
			right[index] = String.valueOf(rightChars.charAt(index));
		}
		return new QSequenceTestMedia(left, right);
	}

	// Fields =================================================================

	private final String[] left;
	private final String[] right;

	// Setup ==================================================================

	private QSequenceTestMedia(String[] left, String[] right) {
		this.left = left;
		this.right = right;
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return left.length;
	}

	public int getRightLength() {
		return right.length;
	}

	public Object getMediaLeftObject(int index) {
		return left[index];
	}

	public Object getMediaRightObject(int index) {
		return right[index];
	}

	public boolean equals(int leftIndex, int rightIndex) {
		Assert.assertTrue(0 <= leftIndex && leftIndex < left.length);
		Assert.assertTrue(0 <= rightIndex && rightIndex < right.length);

		return left[leftIndex].equals(right[rightIndex]);
	}

	public boolean equalsLeft(int left1, int left2) throws QSequenceCancelledException {
		return left[left1].equals(left[left2]);
	}

	public boolean equalsRight(int right1, int right2) throws QSequenceCancelledException {
		return right[right1].equals(right[right2]);
	}

	// Accessing ==============================================================

	public String getLeftLine(int index) {
		return left[index];
	}

	public String getRightLine(int index) {
		return right[index];
	}
}