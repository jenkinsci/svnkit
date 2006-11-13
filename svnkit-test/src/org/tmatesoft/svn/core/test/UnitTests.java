package org.tmatesoft.svn.core.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import de.regnis.q.sequence.QSequenceAllTests;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class UnitTests extends TestCase {

    public static void main(String[] args) {
        new TestRunner().doRun(suite());
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite("SVNKit Unit Tests");
        suite.addTest(QSequenceAllTests.suite());
        return suite;
    }
}
