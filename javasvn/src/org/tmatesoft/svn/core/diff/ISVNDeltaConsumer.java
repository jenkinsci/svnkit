package org.tmatesoft.svn.core.diff;

import java.io.OutputStream;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public interface ISVNDeltaConsumer {

	public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException;

	public void textDeltaEnd(String path) throws SVNException;
}