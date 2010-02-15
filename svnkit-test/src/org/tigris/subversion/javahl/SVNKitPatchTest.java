/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import org.tmatesoft.svn.cli.SVN;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNKitPatchTest extends SVNTests {

    private static final String PATCH1_DIFF = "./svnkit-test/src/org/tigris/subversion/javahl/svnkit_patch1.diff";

    static {
        rootDirectoryName = "./build/javahl";
    }

    public SVNKitPatchTest(String name) {
        super(name);
        testBaseName = "svnkit_patch";
    }

    public void testPatch() throws SubversionException, IOException {

        OneTest thisTest = new OneTest();
        final String wcPath = thisTest.getWCPath();

        SVN.main(new String[] {
                "patch", PATCH1_DIFF, wcPath
        });

        final File iota = new File(wcPath, "iota");
        final BufferedReader r = new BufferedReader(new FileReader(iota));
        try {
            r.readLine();
            r.readLine();
            final String line3 = r.readLine();
            assertEquals("patched", line3);
        } finally {
            r.close();
        }

    }

}
