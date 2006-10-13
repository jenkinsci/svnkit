package de.regnis.q.sequence.line.simplifier;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineEOLSkippingSimplifier implements QSequenceLineSimplifier {

	// Implemented ============================================================

	public byte[] simplify(byte[] bytes) {
		String line = new String(bytes);
		while (line.endsWith("\n") || line.endsWith("\r")) {
			line = line.substring(0, line.length() - 1);
		}

		return line.getBytes();
	}
}
