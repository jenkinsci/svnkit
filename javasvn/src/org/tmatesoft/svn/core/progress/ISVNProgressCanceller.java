package org.tmatesoft.svn.core.progress;

/**
 * @author Marc Strapetz
 */
public interface ISVNProgressCanceller {
	
	void checkCancelled() throws SVNProgressCancelledException;
}