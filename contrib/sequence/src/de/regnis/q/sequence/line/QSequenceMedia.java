package de.regnis.q.sequence.line;

import java.util.*;

import de.regnis.q.sequence.*;
import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Static =================================================================

	public static List createBlocks(QSequenceLine[] baseLines, QSequenceLine[] lines) {
		try {
			final QSequenceMedia media = new QSequenceMedia(baseLines, lines);
			final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(media, new QSequenceDummyCanceller());
			final QSequenceDiscardingMedia discardingMedia = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(true), new QSequenceDummyCanceller());
			final List blocks = new QSequenceDifference(discardingMedia, discardingMedia).getBlocks();
			new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
			return blocks;
		}
		catch (QSequenceCancelledException ex) {
			// Ignore
			return null;
		}
	}

	// Fields =================================================================

	private final QSequenceLine[] myLeft;
	private final QSequenceLine[] myRight;

	// Setup ==================================================================

	public QSequenceMedia(QSequenceLine[] left, QSequenceLine[] right) {
		this.myLeft = left;
		this.myRight = right;
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return myLeft.length;
	}

	public int getRightLength() {
		return myRight.length;
	}

	public Object getMediaLeftObject(int index) {
		return myLeft[index];
	}

	public Object getMediaRightObject(int index) {
		return myRight[index];
	}

	public boolean equals(int leftIndex, int rightIndex) {
		return myLeft[leftIndex].equals(myRight[rightIndex]);
	}

	public boolean equalsLeft(int left1, int left2) throws QSequenceCancelledException {
		return myLeft[left1].equals(myLeft[left2]);
	}

	public boolean equalsRight(int right1, int right2) throws QSequenceCancelledException {
		return myRight[right1].equals(myRight[right2]);
	}
}