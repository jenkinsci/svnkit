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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.ISVNDirectoryContent;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author TMate Software Ltd.
 */
public class TestSVNFileContent extends AbstractRepositoryTest {

    public TestSVNFileContent(String url, String methodName) {
        super(url, methodName);
    }

	  public void testFileContent() throws Throwable {
		  File svn = AllTests.createPlayground();

		  ISVNWorkspace ws = createWorkspace(svn);
		  ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);

		  final ISVNFileContent existingContent = ws.getContent("testFile.txt").asFile();

		  // Test base file content of existing base file.
		  final ByteArrayOutputStream existingBaseFile1 = new ByteArrayOutputStream();
		  assertTrue(existingContent.hasBaseFileContent());
		  existingContent.getBaseFileContent(existingBaseFile1);
		  assertEquals("testFile.txt", existingContent.getPath());
		  assertEquals("testFile.txt", existingContent.getPath());
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

		  final ISVNFileContent newContent = ws.getContent("new.txt").asFile();
		  assertTrue(!newContent.hasBaseFileContent());

		  final ByteArrayOutputStream newWorkingCopy = new ByteArrayOutputStream();
		  assertTrue(newContent.hasWorkingCopyContent());
		  newContent.getWorkingCopyContent(newWorkingCopy);
		  assertEquals("New.\n", new String(newWorkingCopy.toByteArray()));

		  // Delete working copy content.
		  newContent.deleteWorkingCopyContent();
		  assertTrue(!newContent.hasWorkingCopyContent());
	  }

	public void testDirectoryContent() throws IOException, SVNException {
		File svn = AllTests.createPlayground();

		ISVNWorkspace ws = createWorkspace(svn);
		ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);

		// Add new directory
		final File newDir = new File(svn, "newDir");
		final boolean dirMked = newDir.mkdir();
		assertTrue(dirMked);

		// with new file in it.
		final FileWriter writer = new FileWriter(new File(newDir, "newFile.txt"));
		writer.write("New.\n");
		writer.close();

		// Check content path/name for root directory.
		final ISVNDirectoryContent content = ws.getContent("").asDirectory();
		assertEquals("", content.getPath());
		assertEquals("", content.getName());
		try {
			// Deletion on root directory must fail, because it is non-empty.
			content.deleteWorkingCopyContent();
			assertTrue(false);
		}
		catch (SVNException ex) {
			// Ok.
		}

		// Check children of root directory.
		final List childContents = content.getChildContents();
		assertEquals(3, childContents.size());

		// Find newDir and delete it
		boolean foundAndDeleted = false;
		for (Iterator it = childContents.iterator(); it.hasNext();) {
			final ISVNEntryContent childContent = (ISVNEntryContent)it.next();
			if (!childContent.isDirectory() || !childContent.asDirectory().getName().equals(newDir.getName())) {
				continue;
			}

			assertEquals(childContent.getPath(), "newDir");

			assertTrue(!childContent.asDirectory().getChildContents().isEmpty());
			for (Iterator it2 = childContent.asDirectory().getChildContents().iterator(); it2.hasNext();) {
				final ISVNEntryContent grandChildContent = (ISVNEntryContent)it2.next();
				grandChildContent.deleteWorkingCopyContent();
			}

			childContent.asDirectory().deleteWorkingCopyContent();
			assertTrue(childContent.asDirectory().getChildContents().isEmpty());
			foundAndDeleted = true;
		}

		// Check that newDir has actually been deleted ...
		assertTrue(foundAndDeleted);
		assertTrue(!newDir.exists());

		// ... and the root's state is ok.
		assertEquals(2, content.getChildContents().size());
	}
}
