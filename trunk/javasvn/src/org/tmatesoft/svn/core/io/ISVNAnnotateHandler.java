/*
 * Created on Mar 3, 2005
 */
package org.tmatesoft.svn.core.io;

import java.util.Date;

public interface ISVNAnnotateHandler {
	
	public void handleLine(Date date, long revision, String author, String line);

}
