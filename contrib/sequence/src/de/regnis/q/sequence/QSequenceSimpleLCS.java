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

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceSimpleLCS implements QSequenceSnakeRegister {

	// Fields =================================================================

	private final QSequenceMedia media;
	private final List commands = new ArrayList();

	// Setup ==================================================================

	public QSequenceSimpleLCS(QSequenceMedia media) {
		this.media = media;
	}

	// Implemented ============================================================

	public void registerSnake(int leftFrom, int leftTo, int rightFrom, int rightTo) {
		commands.add(new QSequenceSimpleLCSCommand(true, leftFrom, leftTo));
	}

	// Accessing ==============================================================

	public List getCommands() throws QSequenceException {
		commands.clear();
		final QSequenceAlgorithm algorithm = new QSequenceAlgorithm(media, this, Integer.MAX_VALUE);
		algorithm.produceSnakesInOrder();
		return commands;
	}
}