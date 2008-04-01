/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svnlook;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNLookCommand extends AbstractSVNCommand {

    public SVNLookCommand(String name, String[] aliases) {
        super(name, aliases);
    }
    
    protected SVNLookCommandEnvironment getSVNLookEnvironment() {
        return (SVNLookCommandEnvironment) getEnvironment();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli2.svnlook.commands";
    }

    protected File getLocalRepository() throws SVNException {
        List targets = getEnvironment().combineTargets(null, false);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Repository argument required"));
        }
        SVNPath target = new SVNPath((String) targets.get(0));
        if (target.isURL()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'" + target.getTarget() + "' is an URL when it should be a path"));
        }
        return target.getFile();
    }


    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }
}
