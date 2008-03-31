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

/**
 * @author Marc Strapetz
 */
public class QSequenceDiscardingMediaNoConfusionDectector implements QSequenceDiscardingMediaConfusionDetector {

	// Fields =================================================================

	private final boolean discardAbsolutes;

	// Setup ==================================================================

	public QSequenceDiscardingMediaNoConfusionDectector(boolean discardAbsolutes) {
		this.discardAbsolutes = discardAbsolutes;
	}

	// Implemented ============================================================

	public void init(int symbolCount) {
	}

	public boolean isAbsolute(int occurences) {
		return discardAbsolutes && occurences == 0;
	}

	public boolean isProvisional(int occurences) {
		return false;
	}
}