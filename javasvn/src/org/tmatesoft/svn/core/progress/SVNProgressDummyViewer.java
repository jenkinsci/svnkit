package org.tmatesoft.svn.core.progress;

/**
 * @author Marc Strapetz
 */
public final class SVNProgressDummyViewer implements ISVNProgressViewer {

	private static ISVNProgressViewer instance = new SVNProgressDummyViewer();

	public static ISVNProgressViewer getInstance() {
		return instance;
	}

	public void setProgress(double value) {
	}

	public void checkCancelled() {
	}
}
