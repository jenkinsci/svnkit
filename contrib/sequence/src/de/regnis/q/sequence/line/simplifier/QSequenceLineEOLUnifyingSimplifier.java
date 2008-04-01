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
public class QSequenceLineEOLUnifyingSimplifier implements QSequenceLineSimplifier {

	// Implemented ============================================================

	public byte[] simplify(byte[] bytes) {
		String line = new String(bytes);
		boolean trimmed = false;
		while (line.endsWith("\n") || line.endsWith("\r")) {
			line = line.substring(0, line.length() - 1);
			trimmed = true;
		}

		if (trimmed) {
			line += "\n";
		}

		return line.getBytes();
	}
}
