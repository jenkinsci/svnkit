package de.regnis.q.sequence.line.simplifier;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineWhiteSpaceReducingSimplifier implements QSequenceLineSimplifier {

	// Static =================================================================

	public static String reduceWhiteSpaces(String text) {
		final StringBuffer buffer = new StringBuffer();
		boolean lastWasWhiteSpace = false;
		for (int index = 0; index < text.length(); index++) {
			final char ch = text.charAt(index);
			if (ch != '\n' && ch != '\r' && Character.isWhitespace(ch)) {
				lastWasWhiteSpace = true;
			}
			else {
				if (lastWasWhiteSpace) {
					buffer.append(' ');
					lastWasWhiteSpace = false;
				}
				buffer.append(ch);
			}
		}

		if (lastWasWhiteSpace) {
			buffer.append(' ');
		}

		return buffer.toString();
	}

	// Implemented ============================================================

	public byte[] simplify(byte[] bytes) {
		return reduceWhiteSpaces(new String(bytes)).getBytes();
	}
}
