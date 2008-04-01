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

/**
 * @author Marc Strapetz
 */
public class QSequenceSimpleLCSCommand {

	// Fields =================================================================

	private final boolean left;
	private final int from;
	private final int to;

	// Setup ==================================================================

	public QSequenceSimpleLCSCommand(boolean left, int from, int to) {
		this.left = left;
		this.from = from;
		this.to = to;
	}

	// Accessing ==============================================================

	public boolean isLeft() {
		return left;
	}

	public int getFrom() {
		return from;
	}

	public int getTo() {
		return to;
	}
}