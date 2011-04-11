package org.tigris.subversion.javahl;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class JavaHLTestSuite extends TestCase {
	
	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(SVNAdminTests.class);
		suite.addTestSuite(BasicTests.class);
		return suite;
		
	}
}
