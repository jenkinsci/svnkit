/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnlook;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNGNUDiffGenerator;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookDiffCommand extends SVNLookCommand {
    
    public SVNLookDiffCommand() {
        super("diff", null);
    }
    
    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        options.add(SVNLookOption.NO_DIFF_DELETED);
        options.add(SVNLookOption.NO_DIFF_ADDED);
        options.add(SVNLookOption.DIFF_COPY_FROM);
        options.add(SVNLookOption.EXTENSIONS);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        SVNLookClient client = environment.getClientManager().getLookClient();

        DefaultSVNGNUDiffGenerator defaultDiffGenerator = new DefaultSVNGNUDiffGenerator();
        defaultDiffGenerator.setOptions(client.getOptions());
        defaultDiffGenerator.setDiffOptions(environment.getDiffOptions());
        
        client.setDiffGenerator(defaultDiffGenerator);
        if (environment.isRevision()) {
            client.doGetDiff(environment.getRepositoryFile(), getRevisionObject(), 
                    !environment.isNoDiffDeleted(), !environment.isNoDiffAdded(), environment.isDiffCopyFrom(), 
                    environment.getOut());
        } else {
            client.doGetDiff(environment.getRepositoryFile(), environment.getTransaction(), 
                    !environment.isNoDiffDeleted(), !environment.isNoDiffAdded(), environment.isDiffCopyFrom(), 
                    environment.getOut());
        }
    }

}
