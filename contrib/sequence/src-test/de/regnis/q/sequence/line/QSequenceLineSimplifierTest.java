package de.regnis.q.sequence.line;

import junit.framework.*;

import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineSimplifierTest extends TestCase {

	// Accessing ==============================================================

	public void testDummySimplifier() {
		final QSequenceLineSimplifier simplifier = new QSequenceLineDummySimplifier();
		assertEquals(" nothing\tto  simplify \n", new String(simplifier.simplify(" nothing\tto  simplify \n".getBytes())));
	}

	public void testSkippingSimplifier() {
		final QSequenceLineSimplifier simplifier = new QSequenceLineWhiteSpaceSkippingSimplifier();
		assertEquals("muchtosimplify", new String(simplifier.simplify(" much to simplify  ".getBytes())));
		assertEquals("muchtosimplify\n", new String(simplifier.simplify(" much\tto simplify \n ".getBytes())));
	}

	public void testReducingSimplifier() {
		final QSequenceLineSimplifier simplifier = new QSequenceLineWhiteSpaceReducingSimplifier();
		assertEquals(" something to simplify ", new String(simplifier.simplify("  something\t to   simplify \t ".getBytes())));
		assertEquals(" something to simplify\n", new String(simplifier.simplify(" something\t to   simplify\n".getBytes())));
	}

	public void testEolSkippingSimplifier() {
		final QSequenceLineSimplifier simplifier = new QSequenceLineTeeSimplifier(new QSequenceLineWhiteSpaceReducingSimplifier(), new QSequenceLineEOLUnifyingSimplifier());
		assertEquals(" something to simplify\n", new String(simplifier.simplify(" something\t to   simplify\n\r".getBytes())));
	}
}
