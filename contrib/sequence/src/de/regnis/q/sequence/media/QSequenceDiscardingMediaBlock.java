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

package de.regnis.q.sequence.media;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public abstract class QSequenceDiscardingMediaBlock {

	// Abstract ===============================================================

	protected abstract int[] getAllSymbols(QSequenceIntMedia media);

	// Fields =================================================================

	private final QSequenceIntMedia media;
	private final int[] undiscardedSymbols;
	private final int[] undiscardedIndices;

	private int undiscardedSymbolCount;

	// Setup ==================================================================

	protected QSequenceDiscardingMediaBlock(QSequenceIntMedia media) {
		this.media = media;
		this.undiscardedSymbols = new int[getAllSymbols(media).length];
		this.undiscardedIndices = new int[getAllSymbols(media).length];
		this.undiscardedSymbolCount = 0;
	}

	// Accessing ==============================================================

	public int getUndiscardedSymbolCount() {
		return undiscardedSymbolCount;
	}

	public int[] getUndiscardedSymbols() {
		return undiscardedSymbols;
	}

	public int getMediaIndex(int index) {
		return undiscardedIndices[index];
	}

	public void init(QSequenceDiscardingMediaBlock thatBlock, QSequenceDiscardingMediaConfusionDetector confusionDetector) {
		QSequenceAssert.assertNotNull(confusionDetector);

		// Set up table of which lines are going to be discarded.
		final int[] thisAllSymbols = getAllSymbols(media);
		final int[] thatAllSymbols = thatBlock.getAllSymbols(media);
		final int[] otherEquivalences = createEquivalences(thatAllSymbols, media);
		final byte[] discardableMarkers = createDiscardableMarkers(thisAllSymbols, otherEquivalences, confusionDetector);

		// Don't really discard the provisional lines except when they occur
		// in a run of discardables, with nonprovisionals at the beginning
		// and end.
//		filterDiscardableMarkers(allSymbols, discardableMarkers);

		for (int index = 0; index < thisAllSymbols.length; ++index) {
			if (discardableMarkers[index] != 1) {
				undiscardedSymbols[undiscardedSymbolCount] = thisAllSymbols[index];
				undiscardedIndices[undiscardedSymbolCount] = index;
				undiscardedSymbolCount++;
			}
		}
	}

	// Utils ==================================================================

	private static int[] createEquivalences(int[] symbols, QSequenceIntMedia media) {
		final int[] equivalences = new int[media.getSymbolCount()];
		for (int index = 0; index < symbols.length; index++) {
			equivalences[symbols[index]]++;
		}
		return equivalences;
	}

	private static byte[] createDiscardableMarkers(int[] symbols, int[] otherEquivalences, QSequenceDiscardingMediaConfusionDetector confusionDetector) {
		final byte[] discardableMarkers = new byte[symbols.length];
		confusionDetector.init(symbols.length);

		for (int index = 0; index < symbols.length; index++) {
			final int occurences = otherEquivalences[symbols[index]];
			if (confusionDetector.isAbsolute(occurences)) {
				discardableMarkers[index] = 1;
			}
			else if (confusionDetector.isProvisional(occurences)) {
				discardableMarkers[index] = 2;
			}
		}

		return discardableMarkers;
	}

// --Commented out by Inspection START (17.06.04 15:17):
//	private static void filterDiscardableMarkers(int[] symbols, byte[] discardableMarkers) {
//		for (int i = 0; i < symbols.length; i++) {
//			// Cancel provisional discards not in middle of run of discards.
//			if (discardableMarkers[i] == 2) {
//				discardableMarkers[i] = 0;
//			}
//			else if (discardableMarkers[i] != 0) {
//				// We have found a nonprovisional discard.
//				int j;
//				final int length;
//				int provisional = 0;
//
//				// Find end of this run of discardable lines.
//				// Count how many are provisionally discardable.
//				for (j = i; j < symbols.length; j++) {
//					if (discardableMarkers[j] == 0) {
//						break;
//					}
//					if (discardableMarkers[j] == 2) {
//						provisional++;
//					}
//				}
//
//				// Cancel provisional discards at end, and shrink the run.
//				while (j > i && discardableMarkers[j - 1] == 2) {
//					discardableMarkers[--j] = 0;
//					provisional--;
//				}
//
//				// Now we have the length of a run of discardable lines
//				// whose first and last are not provisional.
//				length = j - i;
//
//				// If 1/4 of the lines in the run are provisional,
//				// cancel discarding of all provisional lines in the run.
//				if (provisional * 4 > length) {
//					while (j > i) {
//						if (discardableMarkers[--j] == 2) {
//							discardableMarkers[j] = 0;
//						}
//					}
//				}
//				else {
//					int consec;
//					int minimum = 1;
//					int tem = length / 4;
//
//					// MINIMUM is approximate square root of LENGTH/4.
//					// A subrun of two or more provisionals can stand
//					// when LENGTH is at least 16.
//					// A subrun of 4 or more can stand when LENGTH >= 64.
//					while ((tem >>= 2) > 0) {
//						minimum *= 2;
//					}
//					minimum++;
//
//					// Cancel any subrun of MINIMUM or more provisionals
//					// within the larger run.
//					for (j = 0, consec = 0; j < length; j++) {
//						if (discardableMarkers[i + j] != 2) {
//							consec = 0;
//						}
//						else if (minimum == ++consec) {
//							// Back up to start of subrun, to cancel it all.
//							j -= consec;
//						}
//						else if (minimum < consec) {
//							discardableMarkers[i + j] = 0;
//						}
//					}
//
//					// Scan from beginning of run
//					// until we find 3 or more nonprovisionals in a row
//					// or until the first nonprovisional at least 8 lines in.
//					// Until that point, cancel any provisionals.
//					for (j = 0, consec = 0; j < length; j++) {
//						if (j >= 8 && discardableMarkers[i + j] == 1) {
//							break;
//						}
//						if (discardableMarkers[i + j] == 2) {
//							consec = 0;
//							discardableMarkers[i + j] = 0;
//						}
//						else if (discardableMarkers[i + j] == 0) {
//							consec = 0;
//						}
//						else {
//							consec++;
//						}
//						if (consec == 3) {
//							break;
//						}
//					}
//
//					// I advances to the last line of the run.
//					i += length - 1;
//
//					// Same thing, from end.
//					for (j = 0, consec = 0; j < length; j++) {
//						if (j >= 8 && discardableMarkers[i - j] == 1) {
//							break;
//						}
//						if (discardableMarkers[i - j] == 2) {
//							consec = 0;
//							discardableMarkers[i - j] = 0;
//						}
//						else if (discardableMarkers[i - j] == 0) {
//							consec = 0;
//						}
//						else {
//							consec++;
//						}
//						if (consec == 3) {
//							break;
//						}
//					}
//				}
//			}
//		}
//	}
// --Commented out by Inspection STOP (17.06.04 15:17)
}