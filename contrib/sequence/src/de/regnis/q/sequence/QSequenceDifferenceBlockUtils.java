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

import java.util.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceDifferenceBlockUtils {

	// Accessing ==============================================================

	public static List createCopy(List blocks) {
		final List copy = new ArrayList();
		for (Iterator it = blocks.iterator(); it.hasNext();) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)it.next();
			copy.add(createCopy(block));
		}
		return copy;
	}

	public static QSequenceDifferenceBlock createCopy(QSequenceDifferenceBlock block) {
		return new QSequenceDifferenceBlock(block.getLeftFrom(), block.getLeftTo(), block.getRightFrom(), block.getRightTo());
	}
}