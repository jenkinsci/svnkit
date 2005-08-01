package org.tmatesoft.svn.core.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.tmatesoft.svn.core.internal.wc.FSMergerBySequenceTest;
import org.tmatesoft.svn.core.io.diff.SVNSequenceDeltaGeneratorTest;

import de.regnis.q.sequence.QSequenceAllTests;

/**
 * @author Marc Strapetz
 */
public class UnitTests extends TestCase {

    public static void main(String[] args) {
        new TestRunner().doRun(suite());
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite("JavaSVN Unit Tests");
        suite.addTest(QSequenceAllTests.suite());
        suite.addTestSuite(SVNSequenceDeltaGeneratorTest.class);
        suite.addTestSuite(FSMergerBySequenceTest.class);
        return suite;
    }
}
