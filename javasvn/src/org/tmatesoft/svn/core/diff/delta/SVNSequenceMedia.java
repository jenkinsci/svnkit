package org.tmatesoft.svn.core.diff.delta;

import java.util.*;

import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;
import de.regnis.q.sequence.*;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Static =================================================================

	public static List createBlocks(SVNSequenceLine[] baseLines, SVNSequenceLine[] lines) {
		try {
			final SVNSequenceMedia media = new SVNSequenceMedia(baseLines, lines);
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

	private final SVNSequenceLine[] myLeft;
	private final SVNSequenceLine[] myRight;

	// Setup ==================================================================

	public SVNSequenceMedia(SVNSequenceLine[] left, SVNSequenceLine[] right) {
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