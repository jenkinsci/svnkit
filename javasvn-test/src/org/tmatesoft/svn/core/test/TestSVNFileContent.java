/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import java.io.*;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;

/**
 * @author TMate Software Ltd.
 */
public class TestSVNFileContent extends AbstractRepositoryTest {

    public TestSVNFileContent(String url, String methodName) {
        super(url, methodName);
    }

	  public void testContent() throws Throwable {
		  File svn = AllTests.createPlayground();

		  ISVNWorkspace ws = createWorkspace(svn);
		  ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);

		  final ISVNFileContent existingContent = ws.getFileContent("testFile.txt");

		  // Test base file content of existing base file.
		  final ByteArrayOutputStream existingBaseFile1 = new ByteArrayOutputStream();
		  assertTrue(existingContent.hasBaseFileContent());
		  existingContent.getBaseFileContent(existingBaseFile1);
		  assertEquals("test file", new String(existingBaseFile1.toByteArray()));

		  // Test working copy content of existing working copy file.
		  final ByteArrayOutputStream existingWorkingCopy1 = new ByteArrayOutputStream();
		  assertTrue(existingContent.hasWorkingCopyContent());
		  existingContent.getWorkingCopyContent(existingWorkingCopy1);
		  assertEquals("test file", new String(existingWorkingCopy1.toByteArray()));

		  // Modify working copy.
		  existingContent.setWorkingCopyContent(new ByteArrayInputStream(new String("modified\n\r\r\n").getBytes()));

		  // Test base file content again.
		  final ByteArrayOutputStream existingBaseFile2 = new ByteArrayOutputStream();
		  existingContent.getBaseFileContent(existingBaseFile2);
		  assertEquals("test file", new String(existingBaseFile2.toByteArray()));

		  // Test changed working copy content.
		  final ByteArrayOutputStream existingWorkingCopy2 = new ByteArrayOutputStream();
		  existingContent.getWorkingCopyContent(existingWorkingCopy2);
		  assertEquals("modified\n\r\r\n", new String(existingWorkingCopy2.toByteArray()));

		  // Test base file content of non-existing base file.
		  final FileWriter writer = new FileWriter(new File(svn, "new.txt"));
		  writer.write("New.\n");
		  writer.close();

		  final ISVNFileContent newContent = ws.getFileContent("new.txt");
		  assertTrue(!newContent.hasBaseFileContent());

		  final ByteArrayOutputStream newWorkingCopy = new ByteArrayOutputStream();
		  assertTrue(newContent.hasWorkingCopyContent());
		  newContent.getWorkingCopyContent(newWorkingCopy);
		  assertEquals("New.\n", new String(newWorkingCopy.toByteArray()));

		  // Delete working copy content.
		  newContent.deleteWorkingCopyContent();
		  assertTrue(!newContent.hasWorkingCopyContent());
	  }
}
