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

package de.regnis.q.sequence.core;

/**
 * @author Marc Strapetz
 */
public final class QSequenceAssert {

	// Static =================================================================

	public static void assertNotNull(Object obj) {
		assertTrue(obj != null, "Object must not be null!");
	}

	public static void assertNotNull(Object obj, String text) {
		assertTrue(obj != null, "Object must not be null: " + text);
	}

	public static void assertEquals(int i1, int i2) {
		assertTrue(i1 == i2, i1 + " != " + i2);
	}

	public static void assertEquals(long l1, long l2) {
		assertTrue(l1 == l2, l1 + " != " + l2);
	}

	public static void assertTrue(boolean forceTrueCondition) {
		assertTrue(forceTrueCondition, "");
	}

	public static void assertTrue(boolean forceTrueCondition, String text) {
		if (!forceTrueCondition) {
			error(text);
		}
	}

	public static void error(String text) {
		throw new InternalError(text);
	}

	public static void fatal(String text) {
		throw new InternalError(text);
	}

	// Setup ==================================================================

	private QSequenceAssert() {
	}
}
