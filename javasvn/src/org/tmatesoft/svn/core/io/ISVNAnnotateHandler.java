/*
 * Created on Mar 3, 2005
 */
package org.tmatesoft.svn.core.io;

public interface ISVNAnnotateHandler {
	
	public void handleLine(long revision, String author, String line);

}
