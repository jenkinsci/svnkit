package org.tmatesoft.svn.core.progress;

/**
 * @author Marc Strapetz
 */
public final class SVNProgressProcessor implements ISVNProgressViewer, ISVNProgressCanceller {

	public static final SVNProgressProcessor DUMMY = new SVNProgressProcessor(SVNProgressDummyViewer.getInstance(), new SVNProgressDummyCanceller());

	private final ISVNProgressCanceller canceller;
	private final ISVNProgressViewer viewer;

	public SVNProgressProcessor(ISVNProgressViewer viewer, ISVNProgressCanceller canceller) {
		this.viewer = viewer;
		this.canceller = canceller;
	}

	public void setProgress(double value) {
		if (viewer != null) {
			viewer.setProgress(value);
		}
	}

	public void checkCancelled() throws SVNProgressCancelledException {
		if (canceller != null) {
			canceller.checkCancelled();
		}
	}

	public SVNProgressProcessor createSubProcessor(double lowerBound, double upperBound) {
		return new SVNProgressProcessor(viewer != null ? SVNProgressRangeViewer.createInstance(viewer, lowerBound, upperBound) : null, canceller);
	}

	public SVNProgressProcessor createSubProcessor(long currentIndex, long indexCount) {
		return new SVNProgressProcessor(viewer != null ? SVNProgressRangeViewer.createInstance(viewer, currentIndex, indexCount) : null, canceller);
	}
}
