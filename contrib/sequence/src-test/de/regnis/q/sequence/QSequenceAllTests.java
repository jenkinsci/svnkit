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

package de.regnis.q.sequence;

import junit.framework.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceAllTests {

	// Accessing ==============================================================

	public static Test suite() {
		final TestSuite suite = new TestSuite(de.regnis.q.sequence.QSequenceAllTests.class.getPackage().getName());
		suite.addTestSuite(QSequenceLCSTest.class);
		suite.addTestSuite(QSequenceDifferenceCoreTest.class);
		suite.addTestSuite(QSequenceDifferenceAssemblyTest.class);
		suite.addTestSuite(QSequenceDifferenceBlockShifterTest.class);
		return suite;
	}
}
