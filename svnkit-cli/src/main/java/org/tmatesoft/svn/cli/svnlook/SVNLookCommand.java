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
import java.util.Collections;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNLookCommand extends AbstractSVNCommand {

    public SVNLookCommand(String name, String[] aliases) {
        super(name, aliases);
    }

    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }

    protected SVNLookCommandEnvironment getSVNLookEnvironment() {
        return (SVNLookCommandEnvironment) getEnvironment();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnlook.commands";
    }
    
    protected FSRoot getFSRoot() throws SVNException {
        FSRepository repository = getSVNLookEnvironment().getRepository();
        if (getSVNLookEnvironment().isRevision()) {
            long rev = getSVNLookEnvironment().getRevision();
            if (rev < 0) {
                rev = repository.getLatestRevision();
            }
            return repository.getFSFS().createRevisionRoot(rev);
        } 
        return repository.getFSFS().createTransactionRoot(getSVNLookEnvironment().getTransactionInfo());        
    }
    
    protected SVNProperties getProperties() throws SVNException {
        FSRoot root = getFSRoot();
        if (root instanceof FSTransactionRoot) {
            return root.getOwner().getTransactionProperties(((FSTransactionRoot) root).getTxnID()); 
        }
        return root.getOwner().getRevisionProperties(root.getRevision());
    }

    protected SVNRevision getRevisionObject() {
        if (!SVNRevision.isValidRevisionNumber(getSVNLookEnvironment().getRevision())) {
            return SVNRevision.HEAD;
        }
        return SVNRevision.create(getSVNLookEnvironment().getRevision());
    }
}
