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

package org.tmatesoft.svn.core.internal.ws.fs;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;
import java.io.*;

/**
 * @author Marc Strapetz
 */
public class FSFileEntryContent implements ISVNFileContent {

	private final FSFileEntry myEntry;

	public FSFileEntryContent(FSFileEntry entry) {
		this.myEntry = entry;
	}

	public boolean hasWorkingCopyContent() {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		return file != null && file.isFile();
	}

	public void getWorkingCopyContent(OutputStream os) throws SVNException {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		if (file == null || !file.isFile()) {
			return;
		}

		try {
			final FileInputStream is = new FileInputStream(file);
			try {
				FSUtil.copy(is, os, null);
			}
			finally {
				is.close();
			}
		}
		catch (IOException ex) {
			throw new SVNException(ex);
		}
	}

	public boolean hasBaseFileContent() throws SVNException {
		final File file = myEntry.getAdminArea().getBaseFile(myEntry);
		return file != null && file.isFile();
	}

	public void getBaseFileContent(OutputStream os) throws SVNException {
		final File file = myEntry.getAdminArea().getBaseFile(myEntry);
		if (file == null || !file.isFile()) {
			return;
		}

		try {
			final FileInputStream is = new FileInputStream(file);
			try {
				FSUtil.copy(is, os, null);
			}
			finally {
				is.close();
			}
		}
		catch (IOException ex) {
			throw new SVNException(ex);
		}
	}

	public void setWorkingCopyContent(InputStream is) throws SVNException {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		if (file != null && !file.exists() && !file.isFile()) {
			throw new SVNException("Can't write to '" + file + "'.");
		}

		try {
			final FileOutputStream os = new FileOutputStream(file);
			try {
				FSUtil.copy(is, os, null);
			}
			finally {
				os.close();
			}
		}
		catch (IOException ex) {
			throw new SVNException(ex);
		}
	}

	public void deleteWorkingCopyContent() throws SVNException {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		if (file == null || !file.isFile()) {
			return;
		}

		final boolean deleted = file.delete();
		if (!deleted) {
			throw new SVNException("Can't delete '" + file + "'.");
		}
	}
}