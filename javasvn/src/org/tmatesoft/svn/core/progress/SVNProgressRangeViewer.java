package org.tmatesoft.svn.core.progress;

import org.tmatesoft.svn.util.*;

/**
 * @author Marc Strapetz
 */
public class SVNProgressRangeViewer
        implements ISVNProgressViewer {

	public static ISVNProgressViewer createInstance(ISVNProgressViewer parentProgressViewer, long currentIndex, long indexCount) {
		SVNAssert.assertNotNull(parentProgressViewer);
		SVNAssert.assertTrue(currentIndex >= 0);
		SVNAssert.assertTrue(currentIndex < indexCount);

		if (currentIndex == 0 && indexCount == 1) {
			return parentProgressViewer;
		}

		final double lowerBound = 1.0 * currentIndex / indexCount;
		final double upperBound = 1.0 * (currentIndex + 1) / indexCount;
		return new SVNProgressRangeViewer(parentProgressViewer, lowerBound, upperBound);
	}

	public static ISVNProgressViewer createInstance(ISVNProgressViewer parentProgressViewer, double lowerBound, double upperBound) {
		SVNAssert.assertNotNull(parentProgressViewer);
		SVNAssert.assertTrue(lowerBound >= 0.0 && lowerBound <= upperBound && upperBound <= 1.0);

		return new SVNProgressRangeViewer(parentProgressViewer, lowerBound, upperBound);
	}

	private final double lowerBound;
	private final double upperBound;
	private final ISVNProgressViewer parentProgressViewer;

	private SVNProgressRangeViewer(ISVNProgressViewer parentProgressViewer, double lowerBound, double upperBound) {
		SVNAssert.assertNotNull(parentProgressViewer);

		this.parentProgressViewer = parentProgressViewer;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	public void setProgress(double value) {
		SVNAssert.assertTrue(0.0 <= value && value <= 1.0);

		final double boundedValue = (1.0 - value) * lowerBound + value * upperBound;
		parentProgressViewer.setProgress(boundedValue);
	}
}
