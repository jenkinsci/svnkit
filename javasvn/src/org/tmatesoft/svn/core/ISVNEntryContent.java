package org.tmatesoft.svn.core;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public interface ISVNEntryContent {
	public String getPath();

	public String getName();

	public ISVNFileContent asFile();

	public ISVNDirectoryContent asDirectory();

	public boolean isDirectory();

	public void deleteWorkingCopyContent() throws SVNException;
}