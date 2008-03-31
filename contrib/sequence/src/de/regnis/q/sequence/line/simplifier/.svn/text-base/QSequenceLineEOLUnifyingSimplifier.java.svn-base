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
