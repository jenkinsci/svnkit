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