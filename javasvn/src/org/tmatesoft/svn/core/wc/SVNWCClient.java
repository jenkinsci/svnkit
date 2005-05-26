/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

public class SVNWCClient extends SVNBasicClient {

    public SVNWCClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }
    
    public void cleanup(File path) throws SVNException {
        if (!SVNWCAccess.isVersionedDirectory(path)) {
            SVNErrorManager.error(0, null);
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, true, true);
        wcAccess.getAnchor().cleanup();
        wcAccess.close(true, true);
    }

}
