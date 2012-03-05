package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;

public interface ISvnOperationOptionsProvider {

    ISVNEventHandler getEventHandler();

    ISVNOptions getOptions();

    ISVNRepositoryPool getRepositoryPool();

    ISVNAuthenticationManager getAuthenticationManager();

    ISVNCanceller getCanceller();
}
