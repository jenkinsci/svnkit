/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

public interface ISVNRepositoryFactory {

    public SVNRepository createRepository(String url) throws SVNException;
}
