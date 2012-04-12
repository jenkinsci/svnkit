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
package org.tmatesoft.svn.cli.svnadmin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminCommand extends AbstractSVNCommand {

    protected SVNAdminCommand(String name, String[] aliases) {
        super(name, aliases);
    }
    
    protected SVNAdminCommandEnvironment getSVNAdminEnvironment() {
        return (SVNAdminCommandEnvironment) getEnvironment();
    }
    
    protected File getLocalRepository() throws SVNException {
        return getLocalRepository(0);
    }

    protected File getLocalRepository(int index) throws SVNException {
        List targets = getEnvironment().combineTargets(null, false);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (targets.isEmpty() || index > targets.size() - 1 ) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Repository argument required"), SVNLogType.CLIENT);
        }
        SVNPath target = new SVNPath((String) targets.get(index));
        if (target.isURL()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'" + target.getTarget() + "' is an URL when it should be a path"), SVNLogType.CLIENT);
        }
        return target.getFile();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli.svnadmin.commands";
    }

    protected long getRevisionNumber(SVNRevision rev, long latestRevision, SVNRepository repos) throws SVNException {
        long result = -1;
        if (rev.getNumber() >= 0) {
            result = rev.getNumber();
        } else if (rev == SVNRevision.HEAD) {
            result = latestRevision;
        } else if (rev.getDate() != null) {
            result = repos.getDatedRevision(rev.getDate());
        } else if (rev != SVNRevision.UNDEFINED) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision specifier"), SVNLogType.CLIENT);
        }
        if (result > latestRevision) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Revisions must not be greater than the youngest revision ("  + latestRevision + ")"), SVNLogType.CLIENT);
        }
        return result;
    }
    
    public Collection getGlobalOptions() {
        return Collections.EMPTY_LIST;
    }
}
