package org.tmatesoft.svn.core.progress;

/**
 * @author Marc Strapetz
 */
public interface ISVNProgressViewer {

	void setProgress(double value);

	void checkCancelled() throws SVNProgressCancelledException;
}
