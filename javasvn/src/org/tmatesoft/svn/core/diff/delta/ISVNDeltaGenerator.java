package org.tmatesoft.svn.core.diff.delta;

import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public interface ISVNDeltaGenerator {
	void generateDiffWindow(ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException;
}