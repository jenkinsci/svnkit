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

import java.util.*;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceCachingMediaSymbolMap {

	// Fields =================================================================

	private final Map map;
	private int symbolCount;

	// Setup ==================================================================

	public QSequenceCachingMediaSymbolMap(int maximumSize) {
		this.map = new HashMap(maximumSize);
		this.symbolCount = 0;
	}

	// Accessing ==============================================================

	public int getSymbolCount() {
		return symbolCount;
	}

	public int[] createSymbols(QSequenceCachableMedia media, QSequenceCachableMediaGetter mediaGetter) throws QSequenceException {
		final int length = mediaGetter.getMediaLength(media);
		final int[] symbols = new int[length];
		for (int index = 0; index < length; index++) {
			final Object object = mediaGetter.getMediaObject(media, index);
			symbols[index] = getSymbol(object);
		}
		return symbols;
	}

	// Utils ==================================================================

	private int getSymbol(Object obj) {
		Integer symbol = (Integer)map.get(obj);
		if (symbol == null) {
			symbol = new Integer(symbolCount);
			symbolCount++;
			map.put(obj, symbol);
		}

		return symbol.intValue();
	}
}