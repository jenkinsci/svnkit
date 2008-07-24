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

package de.regnis.q.sequence;

import junit.framework.*;

import de.regnis.q.sequence.line.*;

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
		suite.addTestSuite(QSequenceLineReaderTest.class);
		suite.addTestSuite(QSequenceLineSimplifierTest.class);
		suite.addTestSuite(QSequenceLineMediaTest.class);
		return suite;
	}
}
