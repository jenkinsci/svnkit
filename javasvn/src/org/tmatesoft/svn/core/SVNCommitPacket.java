package org.tmatesoft.svn.core;


/**
 * @author Marc Strapetz
 */
public class SVNCommitPacket {

	// Fields =================================================================

	private final String myRoot;
	private final SVNStatus[] myStatuses;

	// Setup ==================================================================

	public SVNCommitPacket(String root, SVNStatus[] statuses) {
		this.myRoot = root;
		this.myStatuses = statuses;
	}

	// Accessing ==============================================================

	public String getRoot() {
		return myRoot;
	}

	public SVNStatus[] getStatuses() {
		return myStatuses;
	}
}