package org.tmatesoft.svn.core.progress;

/**
 * @author Marc Strapetz
 */
public class SVNProgressDummyCanceller implements ISVNProgressCanceller {

	private static final SVNProgressDummyCanceller instance = new SVNProgressDummyCanceller();

	public static ISVNProgressCanceller getInstance() {
		return instance;
	}

	public void checkCancelled() {
	}
}