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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.ISVNDirectoryContent;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public class FSFileEntryContent implements ISVNFileContent {

	private final FSFileEntry myEntry;

	public String getName() {
		return myEntry.getName();
	}

	public String getPath() {
		return myEntry.getPath();
	}

	public boolean isDirectory() {
		return false;
	}

	public ISVNDirectoryContent asDirectory() {
		return null;
	}

	public ISVNFileContent asFile() {
		return this;
	}

	public FSFileEntryContent(FSFileEntry entry) {
		this.myEntry = entry;
	}

	public boolean hasWorkingCopyContent() {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		return file != null && file.isFile();
	}

	public void getWorkingCopyContent(OutputStream os) throws SVNException {
		getWorkingCopyContent(os, null, false);
	}
	
	public void getWorkingCopyContent(OutputStream os, String eol, boolean unexpandKeywords) throws SVNException {
		final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
		if (file == null || !file.isFile()) {
			return;
		}

		try {
			final FileInputStream is = new FileInputStream(file);
			try {
				FSUtil.copy(is, os, myEntry.isBinary() ? null : eol, 
						myEntry.isBinary() || !unexpandKeywords ? null : myEntry.computeKeywords(false), null);
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
		getBaseFileContent(os, null);
	}
	
	public void getBaseFileContent(OutputStream os, String eol) throws SVNException {
		final File file = myEntry.getAdminArea().getBaseFile(myEntry);
		if (file == null || !file.isFile()) {
			return;
		}

		try {
			final FileInputStream is = new FileInputStream(file);
			try {
				FSUtil.copy(is, os, myEntry.isBinary() ? null : eol, null);
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
		if (file != null && file.exists() && !file.isFile()) {
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
		try {
			final File file = myEntry.getRootEntry().getWorkingCopyFile(myEntry);
			if (file == null || !file.isFile()) {
				return;
			}

			final boolean deleted = file.delete();
			if (!deleted) {
				throw new SVNException("Can't delete '" + file + "'.");
			}
		}
		finally {
			myEntry.dispose();
		}
	}
}