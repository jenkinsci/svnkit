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

package de.regnis.q.sequence.line.simplifier;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineTeeSimplifier implements QSequenceLineSimplifier {

	// Fields =================================================================

	private final QSequenceLineSimplifier first;
	private final QSequenceLineSimplifier second;

	// Setup ==================================================================

	public QSequenceLineTeeSimplifier(QSequenceLineSimplifier first, QSequenceLineSimplifier second) {
		this.first = first;
		this.second = second;
	}

	// Implemented ============================================================

	public byte[] simplify(byte[] bytes) {
		return second.simplify(first.simplify(bytes));
	}
}
