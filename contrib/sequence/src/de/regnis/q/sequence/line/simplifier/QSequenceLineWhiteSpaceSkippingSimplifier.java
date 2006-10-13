package de.regnis.q.sequence.line.simplifier;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineWhiteSpaceSkippingSimplifier implements QSequenceLineSimplifier {

	// Static =================================================================

	public static String removeWhiteSpaces(String text) {
		final StringBuffer buffer = new StringBuffer();
		for (int index = 0; index < text.length(); index++) {
			final char ch = text.charAt(index);
			if (ch != '\n' && ch != '\r' && Character.isWhitespace(ch)) {
				continue;
			}

			buffer.append(ch);
		}

		return buffer.toString();
	}

	// Implemented ============================================================

	public byte[] simplify(byte[] bytes) {
		return removeWhiteSpaces(new String(bytes)).getBytes();
	}
}
