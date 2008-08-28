/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import de.regnis.q.sequence.QSequenceAllTests;

/**
 * @version 1.2.0
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
